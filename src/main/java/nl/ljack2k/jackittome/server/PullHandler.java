package nl.ljack2k.jackittome.server;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.network.JackFeedbackPayload;
import nl.ljack2k.jackittome.network.PullIngredientsPayload;
import nl.ljack2k.jackittome.source.ItemSource;
import nl.ljack2k.jackittome.source.ItemSourceRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side orchestration. Given a payload from the client:
 *   1. Pick the right {@link ItemSource} for the player's currently-open menu.
 *   2. For each ingredient, ask the source which acceptable variant it has the
 *      most of, then extract.
 *   3. Drop the extracted stacks into the player's inventory.
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
        int totalMoved = 0;
        int totalRequested = 0;

        // Track which item we actually moved so the client can animate THAT item.
        // For an exact-match ingredient (P-keybind path) this equals the player's
        // hovered item; for tag-based ingredients it's whichever variant the
        // most-abundant resolver picked.
        ItemStack representativeMoved = ItemStack.EMPTY;

        for (Ingredient ingredient : payload.ingredients()) {
            if (ingredient.isEmpty()) continue;

            int recipeCount = firstCount(ingredient);
            int desired = switch (payload.mode()) {
                case SINGLE -> recipeCount;
                case STACK  -> 64;
                case MAX    -> MAX_PER_CLICK;
            };
            totalRequested += desired;

            ItemStack best = IngredientResolver.pickMostAbundant(source, ingredient, player);
            if (best.isEmpty()) continue;

            int remaining = desired;
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

                if (moved > 0 && representativeMoved.isEmpty()) {
                    representativeMoved = best.copyWithCount(1);
                }

                if (moved == 0) break;
            }
        }

        // Always send a feedback packet — the client uses it to decide whether to
        // play the success or failure animation. We pass moved=0 on failure plus
        // the first acceptable variant of the first ingredient as the icon to draw.
        ItemStack feedbackItem = representativeMoved;
        if (feedbackItem.isEmpty()) {
            for (Ingredient ing : payload.ingredients()) {
                ItemStack[] items = ing.getItems();
                if (items.length > 0 && !items[0].isEmpty()) {
                    feedbackItem = items[0];
                    break;
                }
            }
        }

        if (totalRequested > 0) {
            PacketDistributor.sendToPlayer(player, new JackFeedbackPayload(feedbackItem, totalMoved));
        }

        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.broadcastChanges();
        }
    }

    private static int firstCount(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) return 1;
        int c = items[0].getCount();
        return c <= 0 ? 1 : c;
    }
}
