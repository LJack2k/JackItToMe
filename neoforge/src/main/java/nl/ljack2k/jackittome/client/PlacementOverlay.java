package nl.ljack2k.jackittome.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The panel shown while button-placement mode is active: what the keys do, the
 * live offset, and Reset / Done buttons.
 * <p>
 * It sits on the LEFT edge because recipe viewers keep their item index and page
 * controls on the right and along the top, where a panel would both cover them
 * and be covered by them.
 * <p>
 * Everything draws in a stratum of its own so it stays above the viewer's own
 * widgets — {@code ScreenEvent.Render.Post} fires after the screen has drawn,
 * but within a stratum the GUI orders elements by render pipeline rather than
 * by call order, so a later call is not by itself a higher one.
 */
public final class PlacementOverlay {
    private PlacementOverlay() {}

    private static final int MARGIN = 6;
    private static final int PADDING = 5;
    private static final int BUTTON_HEIGHT = 14;
    private static final int GOLD = 0xFFFFC857;   // matches the jack icon
    private static final int PANEL_BG = 0xE6000000;
    private static final int BUTTON_BG = 0xFF2B2B2B;
    private static final int BUTTON_BG_HOVER = 0xFF4A4A4A;

    // Cached bounds so the click handler and the renderer always agree.
    private static int panelX, panelY, panelW;
    private static int resetX, resetY, resetW;
    private static int doneX, doneY, doneW;
    private static boolean visible = false;

    /** True when the overlay is currently on screen and should take clicks. */
    public static boolean isVisible() {
        return visible;
    }

    public static void render(GuiGraphicsExtractor graphics, Screen screen, int mouseX, int mouseY) {
        visible = false;
        if (screen == null || !JackConfig.PLACEMENT_KEYS.get()) return;
        visible = true;

        Minecraft mc = Minecraft.getInstance();
        int line = mc.font.lineHeight;

        Component title = Component.translatable("jackittome.placement.title");
        Component move = Component.translatable("jackittome.placement.move");
        Component resetHint = Component.translatable("jackittome.placement.reset_hint");
        Component offset = Component.translatable("jackittome.placement.offset",
                ButtonPlacement.dx(), ButtonPlacement.dy());
        Component reset = Component.translatable("jackittome.placement.reset");
        Component done = Component.translatable("jackittome.placement.done");

        resetW = mc.font.width(reset) + 10;
        doneW = mc.font.width(done) + 10;

        int textW = Math.max(Math.max(mc.font.width(title), mc.font.width(move)),
                Math.max(mc.font.width(resetHint), mc.font.width(offset)));
        panelW = Math.max(textW, resetW + 4 + doneW) + PADDING * 2;
        int panelH = PADDING * 2 + line * 4 + 6 + BUTTON_HEIGHT + 4;

        panelX = MARGIN;
        panelY = Math.max(MARGIN, (screen.height - panelH) / 2);

        graphics.nextStratum();

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        graphics.outline(panelX, panelY, panelW, panelH, GOLD);

        int tx = panelX + PADDING;
        int ty = panelY + PADDING;
        graphics.text(mc.font, title, tx, ty, GOLD, false);
        ty += line + 2;
        graphics.text(mc.font, move, tx, ty, 0xFFDDDDDD, false);
        ty += line;
        graphics.text(mc.font, resetHint, tx, ty, 0xFFDDDDDD, false);
        ty += line + 1;
        graphics.text(mc.font, offset, tx, ty, 0xFFFFFFFF, false);
        ty += line + 4;

        resetX = tx;
        resetY = ty;
        doneX = tx + resetW + 4;
        doneY = ty;

        drawButton(graphics, mc, reset, resetX, resetY, resetW, mouseX, mouseY);
        drawButton(graphics, mc, done, doneX, doneY, doneW, mouseX, mouseY);
    }

    private static void drawButton(GuiGraphicsExtractor graphics, Minecraft mc, Component label,
                                   int x, int y, int w, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, BUTTON_HEIGHT);
        graphics.fill(x, y, x + w, y + BUTTON_HEIGHT, hovered ? BUTTON_BG_HOVER : BUTTON_BG);
        graphics.outline(x, y, w, BUTTON_HEIGHT, hovered ? GOLD : 0xFF6A6A6A);
        graphics.text(mc.font, label,
                x + (w - mc.font.width(label)) / 2,
                y + (BUTTON_HEIGHT - mc.font.lineHeight) / 2 + 1,
                hovered ? GOLD : 0xFFFFFFFF, false);
    }

    /**
     * Handles a click on the overlay.
     *
     * @return true if the click hit a button and should not reach the screen
     */
    public static boolean mouseClicked(double mouseX, double mouseY) {
        if (!visible || !JackConfig.PLACEMENT_KEYS.get()) return false;

        if (inside((int) mouseX, (int) mouseY, resetX, resetY, resetW, BUTTON_HEIGHT)) {
            ButtonPlacement.reset();
            playClick();
            return true;
        }
        if (inside((int) mouseX, (int) mouseY, doneX, doneY, doneW, BUTTON_HEIGHT)) {
            // The offset is already saved on every nudge, so "Done" only has to
            // leave the mode — which is the part players would otherwise forget.
            ButtonPlacement.setPlacementKeys(false);
            playClick();
            return true;
        }
        return false;
    }

    private static void playClick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                    .forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
