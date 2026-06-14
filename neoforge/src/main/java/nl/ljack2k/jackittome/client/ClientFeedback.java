package nl.ljack2k.jackittome.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Single entry point for pull-result feedback. Invoked from
 * {@code JackFeedbackPayload#handle} on the client's main thread.
 * <p>
 * Two parallel streams: a list of items that actually went into the
 * inventory (each gets its own success animation, fanned out around the
 * cursor and staggered in time) and an optional failure item (red-shake).
 */
public final class ClientFeedback {
    private ClientFeedback() {}

    /** Horizontal spacing between adjacent fanned-out success animations, in GUI pixels. */
    private static final float FAN_SPACING_X = 18f;
    /** Time between successive success animations starting, in milliseconds. */
    private static final long STAGGER_MS = 60L;

    public static void handle(List<ItemStack> successItems, ItemStack failureItem) {
        double[] mouse = mousePos();
        if (mouse == null) return;

        // Successes first so a same-frame failure (rare; we never set both)
        // overlays on top.
        if (successItems != null && !successItems.isEmpty()) {
            playMultiSuccess(successItems, mouse[0], mouse[1]);
        }
        if (failureItem != null && !failureItem.isEmpty()) {
            JackAnimations.startFailure(failureItem, mouse[0], mouse[1]);
        }
    }

    /**
     * Spread N success animations horizontally around the cursor and stagger
     * their start times. With N items the entries occupy
     * {@code (N - 1) * FAN_SPACING_X} pixels wide, centered on the cursor.
     * Even with a 9-ingredient recipe the spread is ~145px — narrower than
     * most modded inventory screens and short enough not to clip in practice.
     */
    private static void playMultiSuccess(List<ItemStack> items, double mouseX, double mouseY) {
        // Filter out empties first so we know the actual count for centering.
        int n = 0;
        for (ItemStack s : items) if (s != null && !s.isEmpty()) n++;
        if (n == 0) return;

        int i = 0;
        float center = (n - 1) / 2f;
        for (ItemStack s : items) {
            if (s == null || s.isEmpty()) continue;
            float xOffset = (i - center) * FAN_SPACING_X;
            long delayMs = i * STAGGER_MS;
            JackAnimations.start(s, mouseX + xOffset, mouseY, delayMs);
            i++;
        }
    }

    /** Cursor position in GUI-scaled pixels, or null if Minecraft isn't ready. */
    private static double[] mousePos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        Window win = mc.getWindow();
        if (win == null) return null;
        double mx = mc.mouseHandler.xpos() * win.getGuiScaledWidth()  / (double) win.getScreenWidth();
        double my = mc.mouseHandler.ypos() * win.getGuiScaledHeight() / (double) win.getScreenHeight();
        return new double[]{ mx, my };
    }
}
