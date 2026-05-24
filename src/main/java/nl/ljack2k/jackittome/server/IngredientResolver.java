package nl.ljack2k.jackittome.server;

import nl.ljack2k.jackittome.source.ItemSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Resolves an ambiguous {@link Ingredient} (e.g. "any plank") to a single
 * concrete {@link ItemStack} variant, picking the variant the {@link ItemSource}
 * has the most of right now.
 */
public final class IngredientResolver {
    private IngredientResolver() {}

    /**
     * @return the acceptable variant the source has the most of, or
     *         {@link ItemStack#EMPTY} if the source has none of any variant.
     */
    public static ItemStack pickMostAbundant(ItemSource source, Ingredient ingredient, ServerPlayer player) {
        ItemStack best = ItemStack.EMPTY;
        long bestCount = 0;

        for (ItemStack acceptable : ingredient.getItems()) {
            if (acceptable.isEmpty()) continue;
            long available = source.count(acceptable, player);
            if (available > bestCount) {
                bestCount = available;
                best = acceptable;
            }
        }
        return best;
    }
}
