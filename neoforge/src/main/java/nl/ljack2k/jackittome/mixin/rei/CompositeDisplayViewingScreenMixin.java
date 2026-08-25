package nl.ljack2k.jackittome.mixin.rei;

import nl.ljack2k.jackittome.rei.ReiButtonColumn;

import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.impl.client.gui.screen.CompositeDisplayViewingScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The composite recipe screen's half of the column reservation. REI ships two
 * recipe screens and lets the player pick between them, so both have to widen
 * displays or the button would overlap recipe content on whichever was missed.
 *
 * @see ReiButtonColumn
 * @see DefaultDisplayViewingScreenMixin
 */
@Mixin(value = CompositeDisplayViewingScreen.class, remap = false)
public abstract class CompositeDisplayViewingScreenMixin {

    @Redirect(
            method = "init",
            at = @At(value = "INVOKE",
                    target = "Lme/shedaniel/rei/api/client/registry/display/DisplayCategory;"
                            + "getDisplayWidth(Lme/shedaniel/rei/api/common/display/Display;)I"))
    @SuppressWarnings("unchecked")
    private int jackittome$widenForLayout(DisplayCategory<?> category, Display display) {
        int width = ((DisplayCategory<Display>) category).getDisplayWidth(display);
        return ReiButtonColumn.isReserved() ? width + ReiButtonColumn.WIDTH : width;
    }
}
