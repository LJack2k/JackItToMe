package nl.ljack2k.jackittome.jei;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.network.PullIngredientsPayload;
import nl.ljack2k.jackittome.network.PullMode;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.RecipeIngredientRole;

import nl.ljack2k.jackittome.client.AvailabilityCache;
import nl.ljack2k.jackittome.network.CheckAvailabilityPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * One of these is created per recipe layout. JEI calls our methods to
 * populate the button's appearance, tooltip, and click behavior, and JEI
 * itself handles placement (alongside its own bookmark/+ side buttons on the
 * recipe's right edge).
 * <p>
 * On click, builds a {@link PullIngredientsPayload} from the recipe's input
 * slots — each slot becomes one {@link Ingredient} that lists every variant
 * the slot accepts. The server's most-abundant resolver then picks whichever
 * variant the player's storage actually has.
 */
public class JackPullButtonController implements IIconButtonController {

    /**
     * Path to the custom button icon, relative to {@code src/main/resources/assets/jackittome/}.
     * The file at {@code assets/jackittome/textures/gui/jack_button.png} is read as
     * a 16×16 region — JEI's standard side-button icon size.
     */
    private static final ResourceLocation ICON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(JackItToMe.MODID, "textures/gui/jack_button.png");

    private final IRecipeLayoutDrawable<?> layoutDrawable;
    private final IDrawable icon;

    public JackPullButtonController(IRecipeLayoutDrawable<?> layoutDrawable, IJeiHelpers helpers) {
        this.layoutDrawable = layoutDrawable;
        // 16x16 region starting at (0,0). Critical: setTextureSize(16, 16) tells
        // JEI the file is actually 16x16 — without this it assumes the standard
        // Minecraft atlas size (256x256) and ends up sampling only a tiny corner
        // of our texture, resulting in a blank-looking button.
        this.icon = helpers.getGuiHelper()
                .drawableBuilder(ICON_TEXTURE, 0, 0, 16, 16)
                .setTextureSize(16, 16)
                .build();
    }

    @Override
    public void initState(IButtonState state) {
        state.setIcon(icon);
        state.setVisible(true);
        state.setActive(true);
    }

    @Override
    public void updateState(IButtonState state) {
        // No per-frame state changes for now. (Could go inactive if the player has
        // no container open, but the server already handles that case cleanly.)
    }

    @Override
    public boolean onPress(IJeiUserInput input) {
        // isSimulate() means JEI is asking "would you handle this click?" without
        // actually wanting us to fire. Confirm yes so JEI shows hover affordances.
        if (input.isSimulate()) return true;

        List<Ingredient> ingredients = collectInputIngredients();
        if (ingredients.stream().allMatch(Ingredient::isEmpty)) {
            JackItToMe.LOGGER.debug("[JackItToMe] Recipe button clicked but recipe has no input slots.");
            return false;
        }

        // Shortage gate: if any ingredient is short and shift isn't held, block.
        // Note: IJeiUserInput.getModifiers() returns 0 in JEI 19.27 regardless of
        // actual modifier state — JEI doesn't propagate GLFW modifier bits to
        // button click handlers. We use Minecraft's Screen.hasShiftDown() which
        // queries GLFW's keyboard state directly and is reliable across all
        // input paths. The modifier-bit check is left in as a future-proof
        // belt-and-braces in case JEI starts populating it.
        boolean shift =
                (input.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0
                || Screen.hasShiftDown();

        if (!shift) {
            List<Boolean> shortages = nl.ljack2k.jackittome.client.AvailabilityCache
                    .shortagesFor(layoutDrawable.getRecipe());
            if (shortages != null && shortages.contains(Boolean.TRUE)) {
                JackItToMe.LOGGER.debug("[JackItToMe] Recipe button click blocked: shortage. Hold Shift to override.");
                return false;
            }
        }

        JackItToMe.LOGGER.info("[JackItToMe] Recipe button: pulling {} ingredient slot(s){}.",
       