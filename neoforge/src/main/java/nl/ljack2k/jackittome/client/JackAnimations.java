package nl.ljack2k.jackittome.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client-side animations for pull feedback.
 * <p>
 * Two flavors:
 * <ul>
 *   <li><strong>Success</strong> ({@link #start}): ghost item arcs from the
 *       cursor down to the center of the hotbar over ~500ms, shrinking away
 *       as it lands. Fired optimistically on P press.</li>
 *   <li><strong>Failure</strong> ({@link #startFailure}): ghost item stays at
 *       the cursor, shakes left-right rapidly under a red wash, and shrinks
 *       to nothing. Fired when the server reports the pull couldn't be
 *       fulfilled.</li>
 * </ul>
 * Multiple animations can run in parallel. State is purely visual — no
 * game-state coupling.
 */
public final class JackAnimations {
    private JackAnimations() {}

    private static final long DURATION_MS = 500;

    private static final List<Anim> ACTIVE = new ArrayList<>();

    private enum Kind { SUCCESS, FAILURE }

    private static final class Anim {
        final ItemStack stack;
        final float startX, startY;
        /** Wallclock time at which this animation should start rendering. */
        final long startTime;
        final Kind kind;

        Anim(ItemStack stack, float x, float y, Kind kind, long delayMs) {
            this.stack = stack.copyWithCount(1);
            this.startX = x;
            this.startY = y;
            this.startTime = System.currentTimeMillis() + delayMs;
            this.kind = kind;
        }
    }

    /** Falling-into-hotbar success animation. Convenience overload with no delay. */
    public static void start(ItemStack stack, double mouseX, double mouseY) {
        start(stack, mouseX, mouseY, 0L);
    }

    /**
     * Falling-into-hotbar success animation, with an optional delay before
     * it starts rendering. {@link ClientFeedback} uses the delay to stagger
     * a series of animations so they fan out visibly instead of overlapping.
     */
    public static void start(ItemStack stack, double mouseX, double mouseY, long delayMs) {
        if (stack == null || stack.isEmpty()) return;
        ACTIVE.add(new Anim(stack, (float) mouseX, (float) mouseY, Kind.SUCCESS, delayMs));
    }

    /** Shake-and-fade failure animation. */
    public static void startFailure(ItemStack stack, double mouseX, double mouseY) {
        if (stack == null || stack.isEmpty()) return;
        ACTIVE.add(new Anim(stack, (float) mouseX, (float) mouseY, Kind.FAILURE, 0L));
    }

    /** Translucent red wash marking a pull the server refused. */
    private static final int DENIED_WASH = 0xFF3030;

    /** One animation resolved to where and how big it is this frame. */
    private record Frame(ItemStack stack, float x, float y, float scale, float fade, boolean denied) {}

    public static void render(GuiGraphicsExtractor g, Screen screen) {
        if (ACTIVE.isEmpty()) return;

        long now = System.currentTimeMillis();
        float targetX = screen.width / 2f;
        float targetY = screen.height - 11f;

        List<Frame> frames = new ArrayList<>();
        Iterator<Anim> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Anim a = it.next();
            long elapsed = now - a.startTime;
            if (elapsed < 0) continue;                  // still in the staggered pre-start delay
            if (elapsed >= DURATION_MS) {
                it.remove();
                continue;
            }
            float t = elapsed / (float) DURATION_MS;
            frames.add(switch (a.kind) {
                case SUCCESS -> successFrame(a, t, targetX, targetY);
                case FAILURE -> failureFrame(a, t);
            });
        }
        if (frames.isEmpty()) return;

        // Two passes with a stratum between them. Within one stratum the GUI
        // sorts elements by render pipeline rather than by call order, so a
        // wash drawn alongside its item is not guaranteed to land on top of
        // it; a new stratum is what actually orders the two.
        g.nextStratum();
        for (Frame frame : frames) {
            withTransform(g, frame, () -> g.item(frame.stack(), 0, 0));
        }
        g.nextStratum();
        for (Frame frame : frames) {
            if (!frame.denied()) continue;
            withTransform(g, frame, () -> g.fill(0, 0, 16, 16, argb(DENIED_WASH, 0.55f * frame.fade())));
        }
    }

    private static Frame successFrame(Anim a, float t, float targetX, float targetY) {
        float tEase = t * t;
        return new Frame(a.stack,
                a.startX + (targetX - a.startX) * t,
                a.startY + (targetY - a.startY) * tEase,
                1f - t,
                1f - t,
                false);
    }

    private static Frame failureFrame(Anim a, float t) {
        // Sin wave shake, 4 cycles over the duration, settling as it shrinks.
        float shake = (float) Math.sin(t * Math.PI * 8) * 3f * (1f - t);
        return new Frame(a.stack, a.startX + shake, a.startY, 1f - t, 1f - t, true);
    }

    /** Runs {@code draw} with the frame's position and scale applied, item-centered. */
    private static void withTransform(GuiGraphicsExtractor g, Frame frame, Runnable draw) {
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(frame.x(), frame.y());
        pose.scale(frame.scale(), frame.scale());
        pose.translate(-8f, -8f);       // center the 16x16 item
        draw.run();
        pose.popMatrix();
    }

    private static int argb(int rgb, float alpha) {
        int a = Math.clamp((int) (alpha * 255f), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
