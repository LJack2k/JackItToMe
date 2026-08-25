package nl.ljack2k.jackittome.emi;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.client.ButtonPlacement;
import nl.ljack2k.jackittome.client.PullButtonTooltip;
import nl.ljack2k.jackittome.client.PullHoverPoller;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.ButtonWidget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;
import java.util.function.Supplier;

/**
 * EMI-native button rendered inside every recipe display by
 * {@link JackEmiButtonFactory}. Subclassing {@link ButtonWidget} lets us
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

    /**
     * The button is a 12×12 sprite with its border painted into the artwork —
     * the same scheme EMI uses for its own recipe buttons, so ours sits in that
     * row at an identical size.
     * <p>
     * The texture is a 12×24 sheet holding two stacked cells: the normal state
     * at v=0 (black border) and the hovered state at v=12 (white border). Like
     * EMI's {@code RecipeButtonWidget}, hovering just shifts the sample down by
     * one cell — no runtime tinting, and no vanilla frame underneath. Everything
     * blits 1:1, so every authored pixel lands on exactly one GUI pixel.
     */
    public static final int SIZE = 12;

    /** Total texture height — two {@link #SIZE}-tall state cells. */
    private static final int SHEET_HEIGHT = SIZE * 2;

    private final EmiRecipe recipe;
    private final List<Ingredient> ingredients;
    private final PullHoverPoller poller = new PullHoverPoller();
    private final int groupX;
    private final int groupY;
    private final Supplier<List<Component>> tooltip;

    /**
     * @param groupX  screen X of the widget group this button belongs to. EMI
     *                renders widgets translated to that origin, so the button's
     *                own coordinates are relative; the tooltip is drawn later in
     *                screen space and needs the absolute position.
     * @param tooltip supplied per frame, so the in-stock counts stay live
     */
    public JackEmiButton(int x, int y, int groupX, int groupY, EmiRecipe recipe,
                         List<Ingredient> ingredients, Supplier<List<Component>> tooltip,
                         ButtonWidget.ClickAction action) {
        super(x, y, SIZE, SIZE, 0, 0, ICON, () -> true, action);
        this.groupX = groupX;
        this.groupY = groupY;
        this.recipe = recipe;
        this.ingredients = ingredients;
        this.tooltip = tooltip;
    }

    /**
     * Overridden so the clickable area follows the configured placement offset
     * (see {@link ButtonPlacement}), keeping the button clickable wherever the
     * player has moved it.
     */
    @Override
    public Bounds getBounds() {
        return new Bounds(x + ButtonPlacement.dx(), y + ButtonPlacement.dy(), width, height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int x = this.x + ButtonPlacement.dx();   // configured placement offset
        int y = this.y + ButtonPlacement.dy();
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        // Hover swaps to the second cell of the sheet (white border), exactly how
        // EMI's own RecipeButtonWidget offsets v by one cell height.
        graphics.blit(ICON, x, y, SIZE, SIZE,
                0.0F, hovered ? SIZE : 0.0F, SIZE, SIZE, SIZE, SHEET_HEIGHT);
        // Drive the availability refresh so the tooltip's counts stay current.
        poller.tick(hovered, recipe, ingredients);

        if (hovered) {
            // Drawn by us, to the LEFT, so it never covers the recipe and the
            // shortage highlights the player is hovering to read.
            PullButtonTooltip.show(groupX + x, groupY + y, tooltip.get());
        }
    }
}
