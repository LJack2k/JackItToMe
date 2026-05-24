package nl.ljack2k.jackittome.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client-side animations for pull feedback.
 * <p>
 * Two flavors:
 * <ul>
 *   <li><strong>Success</strong> ({@link #start}): ghost item arcs from the
 *       cursor down to the center of the hotbar over ~500ms while shrinking
 *       and fading. Fired optimistically on P press.</li>
 *   <li><strong>Failure</strong> ({@link #startFailure}): ghost item stays at
 *       the cursor, shakes left-right rapidly, red-tinted, fades to nothing.
 *       Fired when the server reports the pull couldn't be fulfilled.</li>
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
        final long startTime;
        final Kind kind;

        Anim(ItemStack stack, float x, float y, Kind kind) {
            this.stack = stack.copyWithCount(1);
            this.startX = x;
            this.startY = y;
            this.startTime = System.currentTimeMillis();
            this.kind = kind;
        }
    }

    /** Falling-into-hotbar success animation. */
    public static void start(ItemStack stack, double mouseX, double mouseY) {
        if (stack == null || stack.isEmpty()) return;
        ACTIVE.add(new Anim(stack, (float) mouseX, (float) mouseY, Kind.SUCCESS));
    }

    /** Shake-and-fade failure animation. */
    public static void startFailure(ItemStack stack, double mouseX, double mouseY) {
        if (stack == null || stack.isEmpty()) return;
        ACTIVE.add(new Anim(stack, (float) mouseX, (float) mouseY, Kind.FAILURE));
    }

    public static void render(GuiGraphics g, Screen screen) {
        if (ACTIVE.isEmpty()) return;

        long now = System.currentTimeMillis();
        float targetX = screen.width / 2f;
        float targetY = screen.height - 11f;

        Iterator<Anim> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Anim a = it.next();
            long elapsed = now - a.startTime;
            if (elapsed >= DURATION_MS) {
                it.remove();
                continue;
            }
            float t = elapsed / (float) DURATION_MS;

            switch (a.kind) {
                case SUCCESS -> renderSuccess(g, a, t, targetX, targetY);
                case FAILURE -> renderFailure(g, a, t);
            }
        }
    }

    private static void renderSuccess(GuiGraphics g, Anim a, float t, float targetX, float targetY) {
        float tEase = t * t;
        float x = a.startX + (targetX - a.startX) * t;
        float y = a.startY + (targetY - a.startY) * tEase;
        float scale = 1f - 0.4f * t;
        float alpha = 1f - 0.8f * t;

        drawItem(g, a.stack, x, y, scale, alpha, 1f, 1f, 1f);
    }

    private static void renderFailure(GuiGraphics g, Anim a, float t) {
        // Sin wave shake, 4 cycles over the duration. Amplitude shrinks as alpha fades.
        float shake = (float) Math.sin(t * Math.PI * 8) * 3f * (1f - t);
        float x = a.startX + shake;
        float y = a.startY;
        float scale = 1f - 0.3f * t;
        float alpha = 1f - t;

        // Red-ish tint to read as "denied" without being totally unreadable
        drawItem(g, a.stack, x, y, scale, alpha, 1f, 0.45f, 0.45f);
    }

    private static void drawItem(GuiGraphics g, ItemStack stack, float x, float y, float scale,
                                 float alpha, float tintR, float tintG, float tintB) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 400f);     // z above tooltips (~200)
        pose.scale(scale, scale, 1f);
        pose.translate(-8f, -8f, 0f);   // center the 16x16 item

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(tintR, tintG, tintB, alpha);
        g.renderItem(stack, 0, 0);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        pose.popPose();
    }
}
