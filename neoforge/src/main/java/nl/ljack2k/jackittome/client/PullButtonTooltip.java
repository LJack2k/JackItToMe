package nl.ljack2k.jackittome.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Draws the pull button's tooltip to the LEFT of the button instead of at the
 * cursor.
 * <p>
 * The button sits in a column on the left of the recipe, so a tooltip opening
 * the usual way covers the recipe itself — including the red/green slot
 * highlights that say which ingredients are missing, which is exactly what the
 * player is hovering the button to find out.
 * <p>
 * Neither viewer can express this: EMI's {@code WidgetTooltipHolder} takes no
 * position at all, and REI positions from a point whose anchoring is its own
 * business. So the button records where it is, in SCREEN coordinates, while it
 * is hovered, and the tooltip is drawn afterwards from a screen render event —
 * outside the translated coordinate space the viewers render widgets in, and
 * late enough to sit above the recipe.
 */
public final class PullButtonTooltip {
    private PullButtonTooltip() {}

    /** Gap between the button and the tooltip box. */
    private static final int GAP = 6;
    /** Vanilla offsets the tooltip +12 from the position it is given. */
    private static final int VANILLA_X_OFFSET = 12;
    /** Keeps the box off the very edge of the screen when it has to be clamped. */
    private static final int SCREEN_MARGIN = 2;

    private static int buttonX, buttonY;
    private static List<Component> lines;

    /**
     * Called by the button while hovered, with its position in screen space.
     * Cleared every frame by {@link #render}, so it never lingers.
     */
    public static void show(int screenX, int screenY, List<Component> tooltip) {
        buttonX = screenX;
        buttonY = screenY;
        lines = tooltip;
    }

    /** Draws and consumes any tooltip queued this frame. */
    public static void render(GuiGraphics graphics, Screen screen) {
        List<Component> queued = lines;
        lines = null;
        if (queued == null || queued.isEmpty() || screen == null) return;

        Minecraft mc = Minecraft.getInstance();
        int widest = 0;
        for (Component line : queued) {
            widest = Math.max(widest, mc.font.width(line));
        }

        // Vanilla draws the box at (given x + 12), so aim for a right edge just
        // left of the button and subtract that offset back out.
        int x = buttonX - GAP - widest - VANILLA_X_OFFSET;

        // Clamp to the screen rather than flipping to the other side. The box
        // grows once availability arrives (the "missing" and hint lines), so a
        // width-based flip would move the tooltip out from under the cursor a
        // frame after it appeared. Staying left and sliding is far less jarring.
        int leftMost = SCREEN_MARGIN - VANILLA_X_OFFSET;
        if (x < leftMost) x = leftMost;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);   // above recipe content
        graphics.renderComponentTooltip(mc.font, queued, x, buttonY);
        graphics.pose().popPose();
    }
}
