package nl.ljack2k.jackittome.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Single entry point for pull-result feedback. Invoked from
 * {@code JackFeedbackPayload#handle} on the client's main thread.
 * <p>
 * Both success and failure animations live behind this dispatch so the
 * animation only runs after the server has confirmed what actually happened.
 */
public final class ClientFeedback {
    private ClientFeedback() {}

    /**
     * @param stack  the item the player requested
     * @param moved  how many actually ended up in their inventory; 0 means
     *               the pull failed (source had nothing matching)
     */
    public static void handle(ItemStack stack, int moved) {
        if (stack == null || stack.isEmpty()) return;
        if (moved > 0) {
            playSuccess(stack);
        } else {
            playFailure(stack);
        }
    }

    private static void playSuccess(ItemStack stack) {
        double[] mouse = mousePos();
        if (mouse == null) return;
        JackAnimations.start(stack, mouse[0], mouse[1]);
    }

    private static void playFailure(ItemStack stack) {
        double[] mouse = mousePos();
        if (mouse == null) return;

        // No sound — feedback is visual only (red-shake animation).
        JackAnimations.startFailure(stack, mouse[0], mouse[1]);
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
