package nl.ljack2k.jackittome.mixin.rei;

import nl.ljack2k.jackittome.rei.ReiButtonColumn;

import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.impl.client.gui.screen.DefaultDisplayViewingScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Widens every display on REI's default recipe screen by
 * {@link ReiButtonColumn#WIDTH}, reserving the strip the pull button is drawn in.
 * <p>
 * {@code getDisplayWidth} is redirected rather than the resulting rectangle being
 * patched, because REI asks for the width in two places that must agree:
 * {@code initDisplays} lays each display out, and {@code lambda$new$0} finds the
 * widest display to size the screen. Widening only one would either clip the
 * column or leave a gap.
 *
 * @see ReiButtonColumn
 */
@Mixin(value = DefaultDisplayViewingScreen.class, remap = false)
public abstract class DefaultDisplayViewingScreenMixin {

    /** Per-display layout. */
    @Redirect(
            method = "initDisplays",
            at = @At(value = "INVOKE",
                    target = "Lme/shedaniel/rei/api/client/registry/display/DisplayCategory;"
                            + "getDisplayWidth(Lme/shedaniel/rei/api/common/display/Display;)I"))
    private int jackittome$widenForLayout(DisplayCategory<?> category, Display display) {
        return jackittome$widen(category, display);
    }

    /** Widest-display measurement used to size the screen; static context. */
    @Redirect(
            method = "lambda$new$0",
            at = @At(value = "INVOKE",
                    target = "Lme/shedaniel/rei/api/client/registry/display/DisplayCategory;"
                            + "getDisplayWidth(Lme/shedaniel/rei/api/common/display/Display;)I"))
    private static int jackittome$widenForMeasure(DisplayCategory<?> category, Display display) {
        return jackittome$widen(category, display);
    }

    @SuppressWarnings("unchecked")
    private static int jackittome$widen(DisplayCategory<?> category, Display display) {
        int width = ((DisplayCategory<Display>) category).getDisplayWidth(display);
        return ReiButtonColumn.isReserved() ? width + ReiButtonColumn.WIDTH : width;
    }
}
