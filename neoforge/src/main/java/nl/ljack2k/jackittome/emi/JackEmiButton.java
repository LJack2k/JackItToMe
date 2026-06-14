package nl.ljack2k.jackittome.emi;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.client.PullHoverPoller;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.ButtonWidget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * EMI-native button rendered inside every recipe display by
 * {@link JackEmiRecipeDecorator}. Subclassing {@link ButtonWidget} lets us
 * draw the 16×16 jack icon cleanly (EMI's stock texture-button expects a
 * normal/hover state sheet, which our single-state icon doesn't have) while
 * reusing EMI's click routing.
 * <p>
 * The render hook also drives the availability poll while hovered, so the
 * button's tooltip (added by the decorator) can show live in-stock/craftable
 * counts. EMI doesn't expose recipe slot positions to decorators, so — unlike
 * the JEI/REI versions — there are no per-slot overlays here, only the tooltip.
 */
public final class JackEmiButton extends ButtonWidget {

    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath(JackItToMe.MODID, "textures/gui/jack_button.png");

    // Vanilla GUI button sprites (nine-sliced by blitSprite to any size).
    private static final ResourceLocation FRAME =
            ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation FRAME_HOVER =
            ResourceLocation.withDefaultNamespace("widget/button_highlighted");

    private final EmiRecipe recipe;
    private final List<Ingredient> ingredients;
    private final PullHoverPoller poller = new PullHoverPoller();

    public JackEmiButton(int x, int y, EmiRecipe recipe, List<Ingredient> ingredients,
                         ButtonWidget.ClickAction action) {
        super(x, y, 16, 16, 0, 0, ICON, () -> true, action);
        this.recipe = recipe;
        this.ingredients = ingredients;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        graphics.blitSprite(hovered ? FRAME_HOVER : FRAME, x, y, width, height);
        graphics.blit(ICON, x + 2, y + 2, 12, 12, 0.0F, 0.0F, 16, 16, 16, 16);
        // Drive the availability refresh so the tooltip's counts stay current.
        poller.tick(hovered, recipe, ingredients);
    }
}
