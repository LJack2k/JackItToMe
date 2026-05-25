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

import net.minecraft.ChatFormatting;
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

        // Autocraft always fires on the J button — whether or not Shift is
        // held. Shift is purely the "also pull what's in stock" toggle.
        // We read Screen.hasShiftDown() rather than input.getModifiers()
        // because JEI's IJeiUserInput.getModifiers() always returns 0 in
        // 19.27 (see AGENTS.md §5.1.1).
        boolean shift =
                (input.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0
                || Screen.hasShiftDown();
        boolean pullAvailable = shift;
        boolean triggerAutocraft = true;

        JackItToMe.LOGGER.info("[JackItToMe] Recipe button: {} ingredients, pull={}, autocraft={}.",
                ingredients.size(), pullAvailable, triggerAutocraft);
        PacketDistributor.sendToServer(new PullIngredientsPayload(
                ingredients, PullMode.SINGLE, pullAvailable, triggerAutocraft));
        return true;
    }

    @Override
    public void getTooltips(ITooltipBuilder tooltip) {
        // Count only non-empty ingredient slots — recipes with empty cells
        // (e.g. crafting table 3x3 with hollow centers) shouldn't inflate the count.
        List<Ingredient> ingredients = collectInputIngredients();
        int total = (int) ingredients.stream().filter(i -> !i.isEmpty()).count();

        // Pull the same availability data the overlays use. Null lists mean
        // the server response hasn't arrived yet (or hover just started);
        // in that case we show only the basic count.
        Object recipe = layoutDrawable.getRecipe();
        List<Boolean> shortages = AvailabilityCache.shortagesFor(recipe);
        List<Boolean> craftable = AvailabilityCache.craftableFor(recipe);

        int missingUncraftable = 0;
        int missingCraftable = 0;
        if (shortages != null) {
            int n = Math.min(shortages.size(), ingredients.size());
            for (int i = 0; i < n; i++) {
                if (!shortages.get(i)) continue;
                boolean canCraft = craftable != null && i < craftable.size() && craftable.get(i);
                if (canCraft) missingCraftable++;
                else missingUncraftable++;
            }
        }
        int totalMissing = missingUncraftable + missingCraftable;

        // Title.
        tooltip.add(Component.translatable("jackittome.button.recipe_pull.title"));

        // Counts. "All N available" reads nicer than "N ingredients needed, 0 missing".
        if (shortages != null && totalMissing == 0) {
            tooltip.add(Component.translatable("jackittome.button.recipe_pull.ready", total)
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("jackittome.button.recipe_pull.total", total)
                    .withStyle(ChatFormatting.GRAY));
        }

        // Breakdown — only show lines for non-zero categories.
        if (missingUncraftable > 0) {
            tooltip.add(Component.translatable(
                            "jackittome.button.recipe_pull.missing", missingUncraftable)
                    .withStyle(ChatFormatting.RED));
        }
        if (missingCraftable > 0) {
            tooltip.add(Component.translatable(
                            "jackittome.button.recipe_pull.craftable", missingCraftable)
                    .withStyle(ChatFormatting.GREEN));
        }

        // Hint. Show only when holding Shift would do strictly more than a
        // plain click — i.e. when there's BOTH a shortage AND something in
        // stock. When all is in stock, plain click already pulls (no Shift
        // needed). When nothing is in stock, Shift has nothing extra to pull.
        // Two wordings differ on whether autocraft is also happening:
        //   - mix of craftable + in-stock: "also pull" (plain click is
        //     already triggering autocraft; Shift adds the pull on top)
        //   - only uncraftable shortage + in-stock: plain click is a
        //     no-op, so "pull what's in stock" without the "also".
        int inStock = total - totalMissing;
        if (shortages != null && inStock > 0 && totalMissing > 0) {
            String key = (missingCraftable > 0)
                    ? "jackittome.button.recipe_pull.shift_hint"
                    : "jackittome.button.recipe_pull.shift_partial";
            tooltip.add(Component.translatable(key)
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    /** Whether the button was hovered last frame (for edge detection). */
    private boolean wasHovered = false;
    /** Last time we re-queried availability while hovering (millis). */
    private long lastQueryTime = 0;
    /** Refresh availability every this-many ms while the mouse stays on the button. */
    private static final long REFRESH_MS = 750;

    @Override
    public void drawExtras(GuiGraphics guiGraphics, Rect2i buttonArea, int mouseX, int mouseY, float partialTicks) {
        boolean isHovered =
                mouseX >= buttonArea.getX() && mouseX < buttonArea.getX() + buttonArea.getWidth() &&
                mouseY >= buttonArea.getY() && mouseY < buttonArea.getY() + buttonArea.getHeight();

        if (isHovered && !wasHovered) {
            // New hover session — wipe any leftover state from a previous
            // session (different recipe view, different open container,
            // anything). Without this, opening a recipe view, viewing it
            // through JEI's internal recipe navigation, then coming back
            // to the same recipe later would briefly flash the old
            // overlays until the new server response arrived.
            AvailabilityCache.clear();
            fireAvailabilityCheck();
            lastQueryTime = System.currentTimeMillis();
        } else if (isHovered) {
            long now = System.currentTimeMillis();
            if (now - lastQueryTime > REFRESH_MS) {
                fireAvailabilityCheck();
                lastQueryTime = now;
            }
        } else if (wasHovered) {
            AvailabilityCache.endHover(layoutDrawable.getRecipe());
        }
        wasHovered = isHovered;

        // Render shortage overlays on input slots — same technique JEI's own
        // "+" button uses (see RecipeTransferErrorMissingSlots). Critically,
        // this runs from drawExtras, which JEI calls for ANY recipe type
        // because button factories are registered universally. No decorator
        // registration per type, no hardcoded list — works for vanilla,
        // Create, AE2's inscriber, Mekanism, anything.
        drawShortageOverlays(guiGraphics);
    }

    /**
     * Walk the cached shortages list and call {@code slot.drawHighlight} on
     * each shortage slot. The pose stack is translated to the recipe's screen
     * origin first so slot drawHighlights land at the correct positions
     * (slot views report positions relative to the recipe origin, not screen).
     * <p>
     * Color follows the per-slot craftable flag: red for "missing and we can't
     * help you", green for "missing but AE2/RS can autocraft it". The green
     * variant tells the user that a Shift-click on the J button will queue
     * autocraft popups for these slots (and a single P-key press on the slot
     * itself will pop the popup directly for that one item).
     */
    private void drawShortageOverlays(GuiGraphics gg) {
        // Only render while this controller's button is the one being hovered
        // — protects against the cache holding data from a previous session
        // (different recipe view, different open container). The cache itself
        // also gets cleared on hover-enter and on JEI recipe-screen close
        // (see ClientEvents.onScreenClosing), but this gate is the last-line
        // defense: a freshly-instantiated controller for a recipe that was
        // hovered previously will not render anything until the user actually
        // hovers this button.
        if (!wasHovered) return;

        Object recipe = layoutDrawable.getRecipe();
        List<Boolean> shortages = AvailabilityCache.shortagesFor(recipe);
        if (shortages == null) return;
        List<Boolean> craftable = AvailabilityCache.craftableFor(recipe);

        IRecipeSlotsView slotsView = layoutDrawable.getRecipeSlotsView();
        List<IRecipeSlotView> inputs = slotsView.getSlotViews(RecipeIngredientRole.INPUT);
        Rect2i recipeRect = layoutDrawable.getRect();

        gg.pose().pushPose();
        gg.pose().translate(recipeRect.getX(), recipeRect.getY(), 0);

        int n = Math.min(inputs.size(), shortages.size());
        for (int i = 0; i < n; i++) {
            if (!shortages.get(i)) continue;
            boolean canCraft = craftable != null && i < craftable.size() && craftable.get(i);
            inputs.get(i).drawHighlight(gg, canCraft ? CRAFTABLE_OVERLAY_COLOR : SHORTAGE_OVERLAY_COLOR);
        }

        gg.pose().popPose();
    }

    /** Translucent red — matches the visual of JEI's own missing-slot error. */
    private static final int SHORTAGE_OVERLAY_COLOR = 0x80FF4040;
    /** Translucent green — "missing, but your AE2/RS network can autocraft it". */
    private static final int CRAFTABLE_OVERLAY_COLOR = 0x8040FF40;

    private void fireAvailabilityCheck() {
        List<Ingredient> ingredients = collectInputIngredients();
        if (ingredients.isEmpty()) return;
        long nonce = AvailabilityCache.beginHover(layoutDrawable.getRecipe());
        PacketDistributor.sendToServer(new CheckAvailabilityPayload(nonce, ingredients));
    }

    /**
     * Walk the recipe's input slots and build one Ingredient per slot, each
     * containing all the slot's acceptable items (the cycling-variant case).
     * <p>
     * Crucially, this preserves <em>empty</em> slots as {@link Ingredient#EMPTY}
     * rather than skipping them. Recipes like a vanilla chest are 3×3 with the
     * center cell empty — keeping the position in the list means the decorator's
     * iteration over slot views aligns 1:1 with the server's shortage list.
     * The server treats {@code Ingredient#EMPTY} as "not a shortage", so empty
     * slots never get a red overlay.
     */
    private List<Ingredient> collectInputIngredients() {
        List<Ingredient> out = new ArrayList<>();
        IRecipeSlotsView slots = layoutDrawable.getRecipeSlotsView();
        for (IRecipeSlotView slot : slots.getSlotViews(RecipeIngredientRole.INPUT)) {
            List<ItemStack> stacks = slot.getItemStacks().toList();
            if (stacks.isEmpty()) {
                out.add(Ingredient.EMPTY);
            } else {
                out.add(Ingredient.of(stacks.toArray(ItemStack[]::new)));
            }
        }
        return out;
    }
}
