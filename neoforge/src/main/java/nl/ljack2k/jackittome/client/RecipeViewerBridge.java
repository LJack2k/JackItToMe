package nl.ljack2k.jackittome.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Abstracts recipe-viewer-specific operations so {@link ClientEvents} never
 * directly imports JEI, EMI, or REI classes. A missing viewer means a
 * missing class, which would cause a {@link ClassNotFoundException} at
 * load time — this bridge prevents that by keeping all viewer code inside
 * subclasses that are only instantiated after we confirm the mod is loaded.
 * <p>
 * The recipe-screen pull button is NOT handled here — each viewer adds it via
 * its own native widget API (JEI button factory, EMI recipe decorator, REI
 * category extension). This bridge only serves the P keybind: finding what the
 * cursor is over.
 */
public interface RecipeViewerBridge {

    /** Single item currently highlighted in the viewer's overlay, sidebar, or recipe, or EMPTY. */
    ItemStack getHoveredItem(Screen screen, double mouseX, double mouseY);

    /**
     * All acceptable ItemStacks for the recipe slot under the cursor.
     * Returns an empty list when not in a recipe screen or no slot is found.
     * For viewers that cycle variants (JEI) this returns the full variant set;
     * for viewers that report one stack at a time (EMI, REI) it returns the
     * currently-highlighted one.
     */
    List<ItemStack> getRecipeSlotVariants(Screen screen, double mouseX, double mouseY);

    /** True when {@code screen} is this viewer's recipe-display screen. */
    boolean isRecipeScreen(Screen screen);

    /** Called when a recipe-viewer screen closes; clear any hover/availability caches. */
    void onRecipeScreenClosed();

    /**
     * The container screen this viewer's recipe view is hosted over (i.e. the
     * inventory/terminal/grid screen the player opened the recipe view from),
     * or {@code null} if unknown. Used as the return target when opening a
     * menu-backed popup (RS's autocraft preview) so it doesn't bounce against
     * the viewer's own recipe screen. Default: {@code null}.
     */
    default AbstractContainerScreen<?> getHostContainerScreen() {
        return null;
    }
}
