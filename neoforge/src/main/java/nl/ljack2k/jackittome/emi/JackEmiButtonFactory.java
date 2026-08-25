package nl.ljack2k.jackittome.emi;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.client.PullTooltipBuilder;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.WidgetHolder;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the JackItToMe pull button for an EMI recipe display.
 * <p>
 * This is NOT an {@code EmiRecipeDecorator}. EMI's decorator API is gated behind
 * its {@code show-recipe-decorators} setting, which ships disabled, and it can
 * only draw inside the recipe body — where short layouts (anvil, fuel) leave no
 * room. {@code RecipeDisplayMixin} therefore reserves a column beside the recipe
 * and calls {@link #decorate} to fill it, so the button is always present and
 * always in the same place.
 * <p>
 * The button carries a dynamic tooltip showing in-stock/craftable counts (the
 * same text JEI/REI show). EMI doesn't expose recipe slot positions to us, so
 * the per-slot shortage overlays the JEI/REI versions draw are not available
 * here — only the tooltip.
 */
public final class JackEmiButtonFactory {

    /** Shared instance; {@code RecipeDisplayMixin} is the only caller. */
    public static final JackEmiButtonFactory INSTANCE = new JackEmiButtonFactory();

    /**
     * Adds the button and its tooltip at a group-relative position, chosen by
     * {@code RecipeDisplayMixin} to sit in the column it reserved beside the
     * recipe body.
     */
    public void decorate(EmiRecipe recipe, WidgetHolder widgets, int x, int y) {
        List<Ingredient> ingredients = EmiStacks.inputIngredients(recipe);
        if (ingredients.stream().allMatch(Ingredient::isEmpty)) {
            return; // output-only display (e.g. info recipes) — nothing to pull.
        }

        widgets.add(new JackEmiButton(x, y, recipe, ingredients,
                (mouseX, mouseY, button) -> sendPull(recipe, ingredients)));

        // Dynamic tooltip — recomputed each hover from the live AvailabilityCache
        // (the button's render hook keeps it refreshed while hovered).
        widgets.addTooltip((mx, my) -> {
            List<ClientTooltipComponent> out = new ArrayList<>();
            for (Component line : PullTooltipBuilder.build(recipe, ingredients, resultsPerCraft(recipe))) {
                out.add(ClientTooltipComponent.create(line.getVisualOrderText()));
            }
            return out;
        }, x, y, JackEmiButton.SIZE, JackEmiButton.SIZE);
    }

    private static void sendPull(EmiRecipe recipe, List<Ingredient> ingredients) {
        // Modifier → mode mapping is shared across viewers in PullButtonClick.
        nl.ljack2k.jackittome.client.PullButtonClick.send(
                "EMI", ingredients, resultsPerCraft(recipe), false);
    }

    /**
     * How many output items one craft produces (first output's amount) —
     * drives STACK mode's "a stack of the result" target. EMI models the
     * count as {@code EmiStack.getAmount()}, not on the key ItemStack.
     */
    private static int resultsPerCraft(EmiRecipe recipe) {
        if (recipe == null) return 1;
        for (var output : recipe.getOutputs()) {
            if (output != null && !output.isEmpty()) {
                return (int) Math.max(1, Math.min(output.getAmount(), Integer.MAX_VALUE));
            }
        }
        return 1;
    }
}
