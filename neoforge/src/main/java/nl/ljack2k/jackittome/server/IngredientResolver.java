package nl.ljack2k.jackittome.server;

import nl.ljack2k.jackittome.network.IngredientRequest;
import nl.ljack2k.jackittome.source.ItemSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Resolves an ambiguous {@link IngredientRequest} (e.g. "any plank") to a single
 * concrete {@link ItemStack} variant, picking the variant the {@link ItemSource}
 * has the most of right now.
 */
public final class IngredientResolver {
    private IngredientResolver() {}

    /**
     * @return the acceptable variant the source has the most of, or
     *         {@link ItemStack#EMPTY} if the source has none of any variant.
     */
    public static ItemStack pickMostAbundant(ItemSource source, IngredientRequest request, ServerPlayer player) {
        ItemStack best = ItemStack.EMPTY;
        long bestCount = 0;

        for (ItemStack acceptable : request.variants()) {
            long available = source.count(acceptable, player);
            if (available > bestCount) {
                bestCount = available;
                best = acceptable;
            }
        }
        return best;
    }
}
