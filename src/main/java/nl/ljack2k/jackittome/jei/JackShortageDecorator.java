package nl.ljack2k.jackittome.jei;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.client.AvailabilityCache;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.category.extensions.IRecipeCategoryDecorator;

import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Draws a translucent red overlay on each recipe input slot that wouldn't be
 * filled by a pull-all click — IF the recipe's pull-all button is the one
 * currently hovered.
 * <p>
 * Data flow:
 * <ol>
 *   <li>User hovers a recipe-pull button. {@code JackPullButtonController}
 *       ships a {@code CheckAvailabilityPayload} and registers the layout's
 *       slot view in {@code AvailabilityCache}.</li>
 *   <li>Server simulates the pull and ships back per-ingredient shortage
 *       booleans.</li>
 *   <li>{@code AvailabilityResponsePayload#handle} stores them in the cache.</li>
 *   <li>This decorator runs every render frame; for the hovered recipe it
 *       reads the cached shortages and calls
 *       {@link IRecipeSlotView#drawHighlight} on each shortage slot.</li>
 * </ol>
 * For non-hovered recipes the decorator is a no-op.
 */
public class JackShortageDecorator implements IRecipeCategoryDecorator<Object> {

    /** Translucent red (ARGB: ~50% alpha). */
    private static final int OVERLAY_COLOR = 0x80FF4040;

    private static int diagFrame = 0;

    @Override
    public void draw(Object recipe, IRecipeCategory<Object> recipeCategory, IRecipeSlotsView slotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        List<Boolean> shortages = AvailabilityCache.shortagesFor(recipe);
        if (shortages == null) return;

        List<IRecipeSlotView> inputs = slotsView.getSlotViews(RecipeIngredientRole.INPUT);
        int n = Math.min(inputs.size(), shortages.size());
        for (int i = 0; i < n; i++) {
            if (shortages.get(i)) {
                inputs.get(i).drawHighlight(guiGraphics, OVERLAY_COLOR);
            }
        }
    }
}
