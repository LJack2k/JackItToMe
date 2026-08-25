package nl.ljack2k.jackittome.emi.mixin;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.client.JackConfig;
import nl.ljack2k.jackittome.client.PullTarget;
import nl.ljack2k.jackittome.emi.JackEmiButton;
import nl.ljack2k.jackittome.emi.JackEmiButtonFactory;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.screen.RecipeDisplay;
import dev.emi.emi.screen.WidgetGroup;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives the JackItToMe pull button a dedicated slot in EMI's recipe layout.
 * <p>
 * <b>Why a mixin at all.</b> {@code EmiRecipeDecorator} is the only official way
 * to add a widget to an EMI recipe, and {@code getWidgets} runs the whole
 * decorator loop behind {@code EmiConfig.showRecipeDecorators}, which ships
 * <b>disabled</b> and is presented as a developer option. On a stock EMI install
 * our decorator is simply never called.
 * <p>
 * <b>Why the left column.</b> A decorator can only draw inside the recipe body,
 * and recipe bodies vary wildly — an anvil or fuel entry is barely taller than
 * one slot, so a corner button lands on top of the output. EMI reserves a button
 * column on each side of the body and sizes the panel around them
 * ({@code getWidth() == leftWidth + width + rightWidth}); its own buttons live in
 * the right one, and the left is empty in practice. So we widen {@code leftWidth}
 * and take that column outright. The panel grows to fit, the button never
 * overlaps recipe content and never falls outside the panel, at any recipe size.
 * <p>
 * Slotting into the RIGHT column instead was the other option, but EMI lays that
 * one out with vertical centring, upward stacking, a {@code rows} cap and a
 * two-column wrap that silently drops buttons which do not fit — private maths we
 * would have to mirror exactly and re-check on every EMI release.
 * <p>
 * X is {@code -(leftWidth + COLUMN_WIDTH)} in group-relative coordinates, mirroring
 * EMI's own left column at -17 ({@code addButtons(group, leftButtons, -17, -14)}).
 */
@Mixin(value = RecipeDisplay.class, remap = false)
public abstract class RecipeDisplayMixin {

    /** Button (12) plus the same 5px gap EMI leaves between its column and the body. */
    @Unique
    private static final int JACKITTOME_COLUMN_WIDTH = JackEmiButton.SIZE + 5;

    @Shadow
    private int leftWidth;

    /** Group-relative X of our reserved column, or MIN_VALUE when we take no slot. */
    @Unique
    private int jackittome$columnX = Integer.MIN_VALUE;

    /**
     * Reserve the column while the display is still sizing itself, so
     * {@code getWidth()} already accounts for it by the time the screen lays out.
     */
    @Inject(method = "<init>(Ldev/emi/emi/api/recipe/EmiRecipe;)V", at = @At("RETURN"))
    private void jackittome$reserveColumn(EmiRecipe recipe, CallbackInfo ci) {
        if (recipe == null || recipe.getInputs() == null || recipe.getInputs().isEmpty()) return;
        if (!JackConfig.SHOW_RECIPE_BUTTON.get()) return;
        // Nothing to pull from behind the recipe view — don't reserve the column
        // either, so the panel keeps its normal width instead of showing a gap.
        if (!PullTarget.isPullable(EmiApi.getHandledScreen())) return;

        // Sit outside anything EMI already put in the left column, in the
        // unlikely event a future version uses it.
        jackittome$columnX = -(leftWidth + JACKITTOME_COLUMN_WIDTH);
        leftWidth += JACKITTOME_COLUMN_WIDTH;
    }

    @Inject(method = "getWidgets", at = @At("RETURN"))
    private void jackittome$addPullButton(int x, int y, int width, int height,
                                          CallbackInfoReturnable<WidgetGroup> cir) {
        if (jackittome$columnX == Integer.MIN_VALUE) return;

        WidgetGroup group = cir.getReturnValue();
        if (group == null || group.recipe == null) return;

        try {
            // Vertically centred in the body, matching how EMI centres its own columns.
            int by = Math.max(0, (group.getHeight() - JackEmiButton.SIZE) / 2);
            JackEmiButtonFactory.INSTANCE.decorate(group.recipe, group, jackittome$columnX, by);
        } catch (Throwable t) {
            // A decoration failure must never take EMI's recipe screen down with it.
            JackItToMe.LOGGER.error("[JackItToMe] Failed to add the EMI pull button; "
                    + "the recipe view still works without it.", t);
        }
    }
}
