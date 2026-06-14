package nl.ljack2k.jackittome.server;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.network.AutocraftChainPayload;
import nl.ljack2k.jackittome.network.JackFeedbackPayload;
import nl.ljack2k.jackittome.network.PullIngredientsPayload;
import nl.ljack2k.jackittome.source.ItemSource;
import nl.ljack2k.jackittome.source.ItemSourceRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side orchestration. The payload tells us two things independently:
 * {@code pullAvailable} — pull whatever is in stock — and
 * {@code triggerAutocraft} — open autocraft popups for whatever isn't.
 * Both flags drive distinct outputs and don't gate each other.
 * <p>
 * Plain J-button click sends {@code (pullAvailable=false, triggerAutocraft=true)},
 * Shift+click sends {@code (true, true)}, the P keybind sends {@code (true, true)}.
 */
public final class PullHandler {
    private PullHandler() {}

    /** Hard cap on how much we'll move in one click in MAX mode. */
    private static final int MAX_PER_CLICK = 64 * 9 * 6; // ~one large chest's worth per ingredient

    public static void handle(ServerPlayer player, PullIngredientsPayload payload) {
        ItemSource source = ItemSourceRegistry.findSource(player);
        if (source == null) {
            JackItToMe.LOGGER.debug("No ItemSource matched for {}, ignoring pull request.", player.getName().getString());
            return;
        }

        Inventory inv = player.getInventory();
        int totalRequested = 0;
        int totalShortfall = 0;

        // Per-slot plan staged during simulation; the extract step (below)
        // consumes it only if effectivePull is true. Keeps the simulation
        // and execution cleanly separated so the "should we actually pull?"
        // decision can be made AFTER we've seen every slot's shortfall.
        List<SlotPlan> plans = new ArrayList<>();
        List<ItemStack> chainCandidates = new ArrayList<>();

        // Local "what's left in the source" snapshot, keyed by Item. Seeded
        // lazily from source.count(...) the first time we encounter each
        // variant, then decremented per-slot as we allocate it. Same idea as
        // AvailabilityHandler.simulate — we need it here so that multiple
        // recipe slots wanting the same item (e.g. two planks slots) produce
        // correct shortfalls. Without it, every slot would see the same
        // "5 available" count and neither would flag a shortage even when
        // the network only has enough for one slot.
        Map<Item, Long> stockSnapshot = new HashMap<>();

        // ---- Phase 1: simulate. Don't touch the source yet. ----
        for (Ingredient ingredient : payload.ingredients()) {
            if (ingredient.isEmpty()) continue;

            int recipeCount = firstCount(ingredient);
            int desired = switch (payload.mode()) {
                case SINGLE -> recipeCount;
                case STACK  -> 64;
                case MAX    -> MAX_PER_CLICK;
            };
            totalRequested += desired;

            // Pick the most abundant *remaining* variant according to the
            // snapshot. This switches automatically as variants run out.
            ItemStack best = ItemStack.EMPTY;
            long bestRemaining = 0;
            for (ItemStack acceptable : ingredient.getItems()) {
                if (acceptable.isEmpty()) continue;
                Item item = acceptable.getItem();
                long remaining = stockSnapshot.computeIfAbsent(item,
                        k -> source.count(acceptable, player));
                if (remaining > bestRemaining) {
                    bestRemaining = remaining;
                    best = acceptable;
                }
            }

            int available = (int) Math.min(bestRemaining, Integer.MAX_VALUE);
            int pullable = Math.min(desired, available);
            int shortfall = desired - pullable;
            totalShortfall += shortfall;

            // Decrement the snapshot for subsequent slots. Done regardless
            // of whether we ultimately extract — the simulation is authoritative.
            if (!best.isEmpty() && pullable > 0) {
                stockSnapshot.put(best.getItem(), bestRemaining - pullable);
                plans.add(new SlotPlan(best, pullable));
            }

            // Autocraft pass — queue ONLY the shortfall (what the network
            // is short by), not the full recipe count.
            if (payload.triggerAutocraft() && shortfall > 0) {
                ItemStack candidate = firstCraftableVariant(ingredient, source, player, shortfall);
                if (!candidate.isEmpty()) {
                    chainCandidates.add(candidate);
                }
            }
        }

        // Aggregate by item identity: collapse duplicate entries from
        // different recipe slots into a single popup with the summed count.
        chainCandidates = aggregateByItem(chainCandidates);

        // ---- Phase 2: decide whether the pull actually executes. ----
        //   - Shift+click (pullAvailable=true): always extract what's in stock.
        //   - Plain click (pullAvailable=false): extract only when there's
        //     no shortage at all. With any shortage, plain click's role is
        //     "trigger autocraft, don't commit anything to inventory"; with
        //     no shortage, plain click's natural meaning is just "pull this
        //     recipe's ingredients" — no Shift required for the trivial case.
        boolean effectivePull = payload.pullAvailable() || totalShortfall == 0;

        // ---- Phase 3: execute extracts. ----
        // movedPerItem: per-Item total of how many ended up in the inventory.
        // LinkedHashMap so the success animations fire in the order we
        // processed the recipe slots — a recipe with planks-first-then-stick
        // animates planks first, then stick, which lines up with how the
        // player would expect to read the recipe.
        java.util.LinkedHashMap<Item, Integer> movedPerItem = new java.util.LinkedHashMap<>();
        java.util.HashMap<Item, ItemStack> templatePerItem = new java.util.HashMap<>();
        int totalMoved = 0;
        if (effectivePull) {
            for (SlotPlan plan : plans) {
                ItemStack best = plan.best();
                int remaining = plan.pullable();
                while (remaining > 0) {
                    int extractRequest = Math.min(remaining, best.getMaxStackSize());
                    ItemStack extracted = source.extract(best, extractRequest, player);
                    if (extracted.isEmpty()) break;

                    int moved = extracted.getCount();
                    if (!inv.add(extracted)) {
                        int leftover = extracted.getCount();
                        if (leftover > 0) {
                            ItemStack returnStack = best.copyWithCount(leftover);
                            source.insertOrDrop(returnStack, player);
                        }
                        moved -= leftover;
                        remaining = 0;
                    } else {
                        remaining -= moved;
                    }
                    totalMoved += moved;

                    if (moved > 0) {
                        movedPerItem.merge(best.getItem(), moved, Integer::sum);
                        templatePerItem.putIfAbsent(best.getItem(), best.copyWithCount(1));
                    }

                    if (moved == 0) break;
                }
            }
        }

        // Build the list of items that actually went into the inventory,
        // one entry per unique Item, with count = total moved of that type.
        List<ItemStack> successItems = new ArrayList<>();
        for (java.util.Map.Entry<Item, Integer> e : movedPerItem.entrySet()) {
            ItemStack template = templatePerItem.get(e.getKey());
            // Cap the displayed count at the item's max stack size — purely
            // cosmetic, the actual items are already in the inventory.
            int count = Math.min(e.getValue(), template.getMaxStackSize());
            successItems.add(template.copyWithCount(count));
        }

        // The single-ingredient P-keybind special case: nothing was pulled,
        // there's exactly one ingredient, and we have a craftable variant.
        // Skip the chain payload and just open the popup directly.
        boolean popupEscalated = false;
        if (payload.triggerAutocraft()
                && payload.ingredients().size() == 1
                && totalMoved == 0
                && !chainCandidates.isEmpty()) {
            ItemStack target = chainCandidates.get(0);
            popupEscalated = source.openAutoCraftPopup(target, target.getCount(), player);
            if (popupEscalated) {
                JackItToMe.LOGGER.info("[JackItToMe] P-keybind: opened autocraft popup for {} (amount {})",
                        target.getHoverName().getString(), target.getCount());
            }
        }

        // Multi-ingredient autocraft chain (J-button path). Send the queue to
        // the client which fires the popups one after the next.
        if (!popupEscalated
                && payload.triggerAutocraft()
                && payload.ingredients().size() > 1
                && !chainCandidates.isEmpty()) {
            JackItToMe.LOGGER.info("[JackItToMe] Autocraft chain: {} missing-craftable item(s) queued.",
                    chainCandidates.size());
            PacketDistributor.sendToPlayer(player, new AutocraftChainPayload(chainCandidates));
        }

        // Feedback animation:
        //   - successItems non-empty → one falling-into-hotbar animation
        //     per unique pulled Item, fanned out and staggered.
        //   - failureItem non-empty → one red-shake animation.
        //   - both empty (or packet not sent) → no animation, silent no-op.
        //
        // Suppression rules:
        //   - If we pulled anything, always send (animations play even when
        //     the autocraft popup also opens — JackAnimations is screen-
        //     agnostic so they keep rendering after the screen change).
        //   - If we pulled nothing but a popup/chain is queued, suppress
        //     (popup is the visible feedback, no red shake needed).
        //   - If we pulled nothing and there's no popup/chain:
        //       - effectivePull was true (P on missing-uncraftable) → red shake.
        //       - effectivePull was false (plain click against an
        //         uncraftable-only shortage) → silent no-op.
        boolean queuedAutocraft = popupEscalated || !chainCandidates.isEmpty();
        ItemStack failureItem = ItemStack.EMPTY;
        if (totalMoved == 0 && !queuedAutocraft && effectivePull) {
            failureItem = firstRepresentative(payload.ingredients());
        }
        if (totalRequested > 0 && (!successItems.isEmpty() || !failureItem.isEmpty())) {
            PacketDistributor.sendToPlayer(player, new JackFeedbackPayload(successItems, failureItem));
        }

        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
    }

    /**
     * Look across the ingredient's acceptable variants for the first one this
     * source reports as autocraftable. The returned stack's count carries the
     * shortfall amount — how many of that item this single slot still needs —
     * so that {@link #aggregateByItem} can sum across slots wanting the same
     * item.
     */
    private static ItemStack firstCraftableVariant(Ingredient ingredient, ItemSource source, ServerPlayer player, int amount) {
        int safeAmount = Math.max(1, amount);
        for (ItemStack acceptable : ingredient.getItems()) {
            if (acceptable.isEmpty()) continue;
            if (source.isAutocraftable(acceptable, player)) {
                return acceptable.copyWithCount(safeAmount);
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Collapse stacks of the same item (same components) into one entry whose
     * count is the sum. Preserves the order of first occurrence so the chain
     * popups appear in roughly the same order as the recipe's input slots.
     * <p>
     * Fixes the "missing 2 planks → 2 popups → 8 planks delivered" footgun:
     * now it's "missing 2 planks → 1 popup pre-filled with 2 → AE2 crafts
     * ceil(2/4) = 1 batch = 4 planks delivered".
     */
    private static java.util.List<ItemStack> aggregateByItem(java.util.List<ItemStack> stacks) {
        java.util.List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : stacks) {
            if (s.isEmpty()) continue;
            boolean merged = false;
            for (int i = 0; i < out.size(); i++) {
                ItemStack existing = out.get(i);
                if (ItemStack.isSameItemSameComponents(existing, s)) {
                    int summed = existing.getCount() + s.getCount();
                    if (summed < 0) summed = Integer.MAX_VALUE; // overflow guard
                    out.set(i, existing.copyWithCount(summed));
                    merged = true;
                    break;
                }
            }
            if (!merged) out.add(s);
        }
        return out;
    }

    /**
     * One pull intent staged during simulation: extract {@code pullable} of
     * {@code best} from the source if {@link #handle}'s {@code effectivePull}
     * is decided true.
     */
    private record SlotPlan(ItemStack best, int pullable) {}

    private static int firstCount(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) return 1;
        int c = items[0].getCount();
        return c <= 0 ? 1 : c;
    }

    /** First acceptable item across all ingredients — used as the animation icon. */
    private static ItemStack firstRepresentative(java.util.List<Ingredient> ingredients) {
        for (Ingredient ing : ingredients) {
            if (ing.isEmpty()) continue;
            ItemStack[] items = ing.getItems();
            if (items.length > 0 && !items[0].isEmpty()) return items[0];
        }
        return ItemStack.EMPTY;
    }
}
