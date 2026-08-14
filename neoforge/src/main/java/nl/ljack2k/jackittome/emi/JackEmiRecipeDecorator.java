package nl.ljack2k.jackittome.emi;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.client.PullTooltipBuilder;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeDecorator;
import dev.emi.emi.api.widget.WidgetHolder;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds the JackItToMe pull button to every EMI recipe display. EMI invokes
 * {@link #decorateRecipe} once per recipe layout (any category), so a single
 * generic decorator gives us the universal button — the EMI equivalent of
 * JEI's {@code IRecipeButtonControllerFactory}.
 * <p>
 * The button carries a dynamic tooltip showing in-stock/craftable counts (the
 * same text JEI/REI show). EMI doesn't expose recipe slot positions to
 * decorators, so the per-slot shortage overlays the JEI/REI versions draw are
 * not available here — only the tooltip.
 */
public final class JackEmiRecipeDecorator implements EmiRecipeDecorator {

    @Override
    public void decorateRecipe(EmiRecipe recipe, WidgetHolder widgets) {
        List<Ingredient> ingredients = EmiStacks.inputIngredients(recipe);
        if (ingredients.stream().allMatch(Ingredient::isEmpty)) {
            return; // output-only display (e.g. info recipes) — nothing to pull.
        }

        // Bottom-right corner of the recipe display (relative to the recipe
        // origin). Clearer than the top-right, which crowds EMI's rounded panel
        // corner. Recipe layouts vary, so this is a best-effort universal spot.
        int x = widgets.getWidth() - 16;
        int y = widgets.getHeight() - 16;

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
        }, x, y, 16, 16);
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
