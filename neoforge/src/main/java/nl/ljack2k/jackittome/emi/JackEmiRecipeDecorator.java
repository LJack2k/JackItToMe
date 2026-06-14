package nl.ljack2k.jackittome.emi;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.client.PullTooltipBuilder;
import nl.ljack2k.jackittome.network.PullIngredientsPayload;
import nl.ljack2k.jackittome.network.PullMode;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeDecorator;
import dev.emi.emi.api.widget.WidgetHolder;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.network.PacketDistributor;

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
            for (Component line : PullTooltipBuilder.build(recipe, ingredients)) {
                out.add(ClientTooltipComponent.create(line.getVisualOrderText()));
            }
            return out;
        }, x, y, 16, 16);
    }

    private static void sendPull(EmiRecipe recipe, List<Ingredient> ingredients) {
        if (ingredients.stream().allMatch(Ingredient::isEmpty)) {
            JackItToMe.LOGGER.debug("[JackItToMe] EMI recipe button clicked but recipe has no input slots.");
            return;
        }
        // Shift = also pull what's in stock; autocraft always fires. Mirrors the
        // JEI button controller's semantics (see JackPullButtonController.onPress).
        boolean shift = Screen.hasShiftDown();
        JackItToMe.LOGGER.info("[JackItToMe] EMI recipe button: {} ingredients, pull={}, autocraft=true.",
                ingredients.size(), shift);
        PacketDistributor.sendToServer(new PullIngredientsPayload(
                ingredients, PullMode.SINGLE, /*pullAvailable=*/ shift, /*triggerAutocraft=*/ true));
    }
}
