package nl.ljack2k.jackittome.client;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.network.IngredientRequest;
import nl.ljack2k.jackittome.network.PullIngredientsPayload;
import nl.ljack2k.jackittome.network.PullMode;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Shared click handling for the per-recipe pull button, so JEI and REI
 * both map modifiers to the same payload — and the quantity mapping matches
 * the pull keybinds (Shift = stack, Ctrl = max, Ctrl beats Shift):
 * <ul>
 *   <li><b>plain</b> — SINGLE (the recipe's own amounts), pull only when
 *       nothing is missing (server-gated); with any shortage it triggers
 *       autocraft popups without pulling</li>
 *   <li><b>Alt</b> (rebindable — {@link KeyBindings#PULL_OVERRIDE}) — SINGLE,
 *       but bypass the gate: pull in-stock ingredients at recipe amounts
 *       while popups open for the missing ones</li>
 *   <li><b>Shift</b> — STACK, a full stack of each ingredient</li>
 *   <li><b>Ctrl</b> — MAX, as much of each as fits</li>
 * </ul>
 * Autocraft always fires for missing-but-craftable ingredients; the quantity
 * modes raise the shortfall the popups pre-fill toward (stack / max).
 */
public final class PullButtonClick {
    private PullButtonClick() {}

    /**
     * @param viewer          display name for the log line ("JEI" / "EMI" / "REI")
     * @param ingredients     recipe inputs in slot order (empties preserved)
     * @param resultsPerCraft how many output items one craft produces — drives
     *                        STACK mode's "a stack of the result" target; pass
     *                        1 if the viewer can't tell
     * @param viewerShift     a shift state the viewer itself reported, OR'd with
     *                        the live GLFW state (JEI's {@code getModifiers()}
     *                        future-proofing — see AGENTS.md §5.1.1); pass false
     *                        where the viewer has no modifier source of its own
     * @return true if a payload was sent
     */
    public static boolean send(String viewer, List<IngredientRequest> ingredients, int resultsPerCraft, boolean viewerShift) {
        if (ingredients.stream().allMatch(IngredientRequest::isEmpty)) {
            JackItToMe.LOGGER.debug("[JackItToMe] {} recipe button clicked but recipe has no input slots.", viewer);
            return false;
        }

        boolean shift    = viewerShift || isEitherHeld(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean ctrl     = isEitherHeld(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean override = isOverrideHeld();
        PullMode mode = ctrl ? PullMode.MAX : (shift ? PullMode.STACK : PullMode.SINGLE);
        // Bulk modes are an explicit "gather" intent and the override key is
        // an explicit gate bypass — any of them implies pulling; a bare plain
        // click keeps the review-the-popups-first gate. Combined with a bulk
        // modifier, the override additionally relaxes whole-crafts-only into
        // fill-each-ingredient-toward-the-target (fillPartial).
        boolean pullAvailable = ctrl || shift || override;

        JackItToMe.LOGGER.info("[JackItToMe] {} recipe button: {} ingredients, mode={}, results/craft={}, pull={}, partial={}, autocraft=true.",
                viewer, ingredients.size(), mode, resultsPerCraft, pullAvailable, override);
        ClientPacketDistributor.sendToServer(new PullIngredientsPayload(
                ingredients, mode, Math.max(1, resultsPerCraft),
                pullAvailable, /*fillPartial=*/ override, /*triggerAutocraft=*/ true));
        return true;
    }

    /**
     * Whether the rebindable pull-override key is held right now. Sampled
     * directly from GLFW because it's a hold-modifier, not a press action —
     * keyboard binds only (a mouse-bound override is treated as never held).
     */
    public static boolean isOverrideHeld() {
        var mapping = KeyBindings.PULL_OVERRIDE;
        if (mapping.isUnbound()) return false;
        var key = mapping.getKey();
        if (key.getType() != InputConstants.Type.KEYSYM) return false;
        return isHeld(key.getValue());
    }

    /**
     * Live keyboard state. A button click arrives without a key event, so
     * there are no modifier flags to read off it - GLFW is the only source
     * that knows what is held at this instant.
     */
    private static boolean isHeld(int glfwKey) {
        Window window = Minecraft.getInstance().getWindow();
        return window != null && InputConstants.isKeyDown(window, glfwKey);
    }

    private static boolean isEitherHeld(int leftKey, int rightKey) {
        return isHeld(leftKey) || isHeld(rightKey);
    }
}
