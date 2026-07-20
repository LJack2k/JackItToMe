package nl.ljack2k.jackittome.compat.id;

import nl.ljack2k.jackittome.JackItToMe;

import org.cyclops.commoncapabilities.api.ingredient.IngredientComponent;
import org.cyclops.integratedcrafting.api.network.ICraftingNetwork;
import org.cyclops.integratedcrafting.api.recipe.IRecipeIndex;
import org.cyclops.integratedcrafting.core.CraftingHelpers;
import org.cyclops.integrateddynamics.api.network.INetwork;

import net.minecraft.world.item.ItemStack;

/**
 * All Integrated <em>Crafting</em> API access lives here, isolated from
 * {@link IntegratedDynamicsItemSource} so that a network <em>without</em> the
 * Integrated Crafting addon never classloads any of its types. The source only
 * calls into this class after checking {@code ModList.isLoaded("integratedcrafting")},
 * which — because a class isn't loaded until first active use — keeps every
 * {@code org.cyclops.integratedcrafting.*} reference dormant otherwise.
 * <p>
 * Storage (pull/count/extract) never needs this; only the optional autocraft
 * hooks do.
 */
final class IdCraftingSupport {
    private IdCraftingSupport() {}

    /**
     * Cheap "can the network craft this?" check — an index lookup only, no job
     * calculation. Walks the crafting network's channels and asks each channel's
     * {@link IRecipeIndex} whether any recipe outputs {@code template} (matched
     * with {@code matchFlags}, e.g. item + components). This is the fast path the
     * hover preview leans on; it does <em>not</em> verify sub-ingredients are
     * available (that would need a full plan calc), so a green highlight means
     * "a recipe exists", matching how AE2/RS advertise craftability.
     */
    static boolean isCraftable(INetwork network, ItemStack template, int matchFlags) {
        try {
            return CraftingHelpers.getCraftingNetwork(network).map(craftingNetwork -> {
                for (int channel : craftingNetwork.getChannels()) {
                    IRecipeIndex index = craftingNetwork.getRecipeIndex(channel);
                    if (index != null
                            && index.getRecipes(IngredientComponent.ITEMSTACK, template, matchFlags).hasNext()) {
                        return true;
                    }
                }
                return false;
            }).orElse(false);
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("[ID] isCraftable check failed: {}", t.toString());
            return false;
        }
    }
}
