package nl.ljack2k.jackittome.jei;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;

import org.jetbrains.annotations.Nullable;

/**
 * JEI calls this factory once per displayed recipe layout. We return a button
 * controller for recipes that have at least one input slot; for output-only
 * displays (which shouldn't really exist but just in case) we return null and
 * JEI omits the button.
 */
public class JackPullRecipeButtonFactory implements IRecipeButtonControllerFactory {

    private final IJeiHelpers helpers;

    public JackPullRecipeButtonFactory(IJeiHelpers helpers) {
        this.helpers = helpers;
    }

    @Override
    @Nullable
    public <T> IIconButtonController createButtonController(IRecipeLayoutDrawable<T> layoutDrawable) {
        IRecipeSlotsView slots = layoutDrawable.getRecipeSlotsView();
        if (slots.getSlotViews(RecipeIngredientRole.INPUT).isEmpty()) {
            return null;
        }
        return new JackPullButtonController(layoutDrawable, helpers);
    }
}
