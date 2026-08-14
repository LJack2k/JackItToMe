package nl.ljack2k.jackittome.server;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.network.AutocraftChainPayload;
import nl.ljack2k.jackittome.network.JackFeedbackPayload;
import nl.ljack2k.jackittome.network.PullIngredientsPayload;
import nl.ljack2k.jackittome.network.PullMode;
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

        // ---- Phase 1: simulate. Don't touch the source yet. ----
        // SINGLE asks for the recipe's own amounts per slot (partial pulls
        // allowed); STACK/MAX ask for whole extra crafts, keeping the
        // recipe's ingredient ratios (see simulateBulk).
        Simulation sim = payload.mode() == PullMode.SINGLE
                ? simulateSingle(source, payload, player)
                : simulateBulk(source, payload, player);
        int totalRequested = sim.totalRequested();
        int totalShortfall = sim.totalShortfall();
        List<SlotPlan> plans = sim.plans();

        // Aggregate by item identity: collapse duplicate entries from
        // different recipe slots into a single popup with the summed count.
        List<ItemStack> chainCandidates = aggregateByItem(sim.chainCandidates());

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

    /** What Phase 1 produced, however the mode computed it. */
    private record Simulation(List<SlotPlan> plans,
                              List<ItemStack> chainCandidates,
                              int totalRequested,
                              int totalShortfall) {}

    /**
     * SINGLE-mode simulation: each slot independently wants its recipe count.
     * Partial fulfillment is fine — 6 of 8 planks pulls 6 and shortfalls 2.
     */
    private static Simulation simulateSingle(ItemSource source, PullIngredientsPayload payload, ServerPlayer player) {
        List<SlotPlan> plans = new ArrayList<>();
        List<ItemStack> chainCandidates = new ArrayList<>();
        int totalRequested = 0;
        int totalShortfall = 0;

        // Local "what's left in the source" snapshot, keyed by Item. Seeded
        // lazily from source.count(...) the first time we encounter each
        // variant, then decremented per-slot as we allocate it. Same idea as
        // AvailabilityHandler.simulate — we need it here so that multiple
        // recipe slots wanting the same item (e.g. two planks slots) produce
        // correct shortfalls. Without it, every slot would see the same
        // "5 available" count and neither would flag a shortage even when
        // the network only has enough for one slot.
        Map<Item, Long> stockSnapshot = new HashMap<>();

        for (Ingredient ingredient : payload.ingredients()) {
            if (ingredient.isEmpty()) continue;

            int desired = firstCount(ingredient);
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
        return new Simulation(plans, chainCandidates, totalRequested, totalShortfall);
    }

    /**
     * STACK/MAX simulation: think in whole crafts, not per-slot stacks, so the
     * recipe's ingredient ratios are preserved. A book (3 paper + 1 leather)
     * with 64 paper and 3 leather in stock pulls 9 paper + 3 leather — three
     * complete crafts, limited by the scarcest ingredient — never 64 of each.
     * <p>
     * The craft multiplier is computed in three steps:
     * <ol>
     *   <li><b>mode cap</b> — STACK targets materials for one full stack of
     *       the OUTPUT: {@code ceil(64 / resultsPerCraft)} crafts. MAX targets
     *       as many crafts as {@link #MAX_PER_CLICK} per ingredient allows
     *       (which also sanity-caps STACK).</li>
     *   <li><b>target m*</b> — the mode cap further limited by every
     *       NON-craftable ingredient's stock. Craftable ingredients don't
     *       limit the target: their gap toward it becomes the autocraft
     *       popup's pre-fill amount.</li>
     *   <li><b>pulled m</b> — {@code min(m*, what stock supports for every
     *       ingredient)} complete crafts are extracted now.</li>
     * </ol>
     */
    private static Simulation simulateBulk(ItemSource source, PullIngredientsPayload payload, ServerPlayer player) {
        // Resolve each slot to its most abundant variant, then total up the
        // per-craft need and available stock per resolved item.
        Map<Item, Long> stock = new HashMap<>();
        java.util.LinkedHashMap<Item, Integer> perCraft = new java.util.LinkedHashMap<>();
        Map<Item, ItemStack> template = new HashMap<>();

        for (Ingredient ingredient : payload.ingredients()) {
            if (ingredient.isEmpty()) continue;

            ItemStack best = ItemStack.EMPTY;
            long bestAvailable = -1;
            for (ItemStack acceptable : ingredient.getItems()) {
                if (acceptable.isEmpty()) continue;
                long available = stock.computeIfAbsent(acceptable.getItem(),
                        k -> source.count(acceptable, player));
                if (available > bestAvailable) {
                    bestAvailable = available;
                    best = acceptable;
                }
            }
            if (best.isEmpty()) continue;

            perCraft.merge(best.getItem(), Math.max(1, best.getCount()), Integer::sum);
            template.putIfAbsent(best.getItem(), best.copyWithCount(1));
        }
        if (perCraft.isEmpty()) {
            return new Simulation(List.of(), List.of(), 0, 0);
        }

        // Step 1: the mode cap, in whole crafts.
        //   STACK — enough crafts for one full stack of the OUTPUT item:
        //           ceil(64 / resultsPerCraft). A book (1 result/craft) wants
        //           64 crafts = 192 paper + 64 leather; ingredients may well
        //           exceed a stack each — that's the point.
        //   MAX   — as many crafts as MAX_PER_CLICK per ingredient allows.
        // Both are additionally sanity-capped per ingredient at MAX_PER_CLICK,
        // and max(1, ...) so a recipe slot wanting more than the cap per craft
        // still pulls at least one craft's worth.
        long sanityCap = Long.MAX_VALUE;
        long stockCrafts = Long.MAX_VALUE;
        for (Map.Entry<Item, Integer> e : perCraft.entrySet()) {
            sanityCap = Math.min(sanityCap, (long) MAX_PER_CLICK / e.getValue());
            stockCrafts = Math.min(stockCrafts, stock.get(e.getKey()) / e.getValue());
        }
        long modeCap = payload.mode() == PullMode.STACK
                ? Math.ceilDiv(64, Math.max(1, payload.resultsPerCraft()))
                : sanityCap;
        modeCap = Math.max(1, Math.min(modeCap, sanityCap));

        // Step 2: the target — craftable ingredients don't limit it, their
        // gap becomes the popup pre-fill. Without autocraft the target is
        // just what stock supports.
        long target = modeCap;
        if (payload.triggerAutocraft()) {
            for (Map.Entry<Item, Integer> e : perCraft.entrySet()) {
                if (!source.isAutocraftable(template.get(e.getKey()), player)) {
                    target = Math.min(target, stock.get(e.getKey()) / e.getValue());
                }
            }
        } else {
            target = Math.min(target, stockCrafts);
        }

        // Step 3: pull whole crafts only, never partial ratios — unless the
        // player held the override (fillPartial): then each ingredient fills
        // toward the target independently, so a missing ingredient doesn't
        // zero out the rest ("grab what you can, I'll sort out the gap").
        long pulled = Math.min(target, stockCrafts);

        List<SlotPlan> plans = new ArrayList<>();
        List<ItemStack> chainCandidates = new ArrayList<>();
        int totalRequested = 0;
        int totalShortfall = 0;
        for (Map.Entry<Item, Integer> e : perCraft.entrySet()) {
            long want = target * e.getValue();
            // Requested reflects the mode's ASK (the cap), not the stock-clamped
            // target — so a bulk click that can't complete a single craft still
            // counts as "asked for something" and the no-op gets the red-shake
            // failure feedback instead of feeling like a dead click.
            totalRequested += (int) Math.min(modeCap * e.getValue(), Integer.MAX_VALUE - totalRequested);

            long pull = payload.fillPartial()
                    ? Math.min(modeCap * e.getValue(), stock.get(e.getKey()))
                    : pulled * e.getValue();
            if (pull > 0) {
                plans.add(new SlotPlan(template.get(e.getKey()), (int) Math.min(pull, Integer.MAX_VALUE)));
            }

            long gap = want - Math.min(stock.get(e.getKey()), want);
            if (gap > 0) {
                totalShortfall += (int) Math.min(gap, Integer.MAX_VALUE - totalShortfall);
                if (payload.triggerAutocraft() && source.isAutocraftable(template.get(e.getKey()), player)) {
                    chainCandidates.add(template.get(e.getKey())
                            .copyWithCount((int) Math.min(gap, Integer.MAX_VALUE)));
                }
            }
        }
        return new Simulation(plans, chainCandidates, totalRequested, totalShortfall);
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
