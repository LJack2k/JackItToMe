package nl.ljack2k.jackittome.rei;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.client.AvailabilityCache;
import nl.ljack2k.jackittome.client.RecipeViewerBridge;

import me.shedaniel.rei.api.client.REIRuntime;
import me.shedaniel.rei.api.client.gui.screen.DisplayScreen;
import me.shedaniel.rei.api.client.gui.widgets.Slot;
import me.shedaniel.rei.api.client.overlay.OverlayListWidget;
import me.shedaniel.rei.api.client.overlay.ScreenOverlay;
import me.shedaniel.rei.api.common.entry.EntryStack;

import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ReiViewerBridge implements RecipeViewerBridge {

    /** Recursion cap for the screen widget-tree walk. */
    private static final int MAX_DEPTH = 8;

    @Override
    public ItemStack getHoveredItem(Screen screen, double mouseX, double mouseY) {
        // 1) Recipe screen: find the REI Slot widget under the cursor.
        //    (ScreenRegistry.getFocusedStack returns null for recipe-display
        //    slots in this REI version, so we walk the screen's widgets.)
        if (screen instanceof DisplayScreen) {
            Slot slot = findHoveredSlot(screen.children(), mouseX, mouseY, 0);
            if (slot != null) {
                ItemStack is = ReiDisplays.toItemStack(slot.getCurrentEntry());
                if (!is.isEmpty()) return is;
            }
        }

        // 2) Overlay sidebar (entry list + favorites) — the canonical hover.
        try {
            Optional<ScreenOverlay> overlayOpt = REIRuntime.getInstance().getOverlay();
            if (overlayOpt.isPresent()) {
                ScreenOverlay overlay = overlayOpt.get();
                ItemStack is = ReiDisplays.toItemStack(overlay.getEntryList().getFocusedStack());
                if (!is.isEmpty()) return is;
                Optional<OverlayListWidget> favorites = overlay.getFavoritesList();
                if (favorites.isPresent()) {
                    is = ReiDisplays.toItemStack(favorites.get().getFocusedStack());
                    if (!is.isEmpty()) return is;
                }
            }
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("[JackItToMe] REI overlay hover lookup failed: {}", t.toString());
        }
        return ItemStack.EMPTY;
    }

    @Override
    public List<ItemStack> getRecipeSlotVariants(Screen screen, double mouseX, double mouseY) {
        if (!(screen instanceof DisplayScreen)) return List.of();
        Slot slot = findHoveredSlot(screen.children(), mouseX, mouseY, 0);
        if (slot == null) return List.of();
        // Return every variant the slot cycles through, so the server can
        // substitute whichever the player actually has (the "any plank" case).
        List<ItemStack> out = new ArrayList<>();
        for (EntryStack<?> entry : slot.getEntries()) {
            ItemStack is = ReiDisplays.toItemStack(entry);
            if (!is.isEmpty()) out.add(is);
        }
        return out;
    }

    @Override
    public boolean isRecipeScreen(Screen screen) {
        return screen instanceof DisplayScreen;
    }

    @Override
    public void onRecipeScreenClosed() {
        AvailabilityCache.clear();
    }

    @Override
    public AbstractContainerScreen<?> getHostContainerScreen() {
        // The container screen the recipe view was opened from (the RS grid).
        try {
            return REIRuntime.getInstance().getPreviousContainerScreen();
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("[JackItToMe] REI getPreviousContainerScreen failed: {}", t.toString());
            return null;
        }
    }

    // ---- helpers --------------------------------------------------------

    /**
     * Recursively walk a screen's widget tree for the REI {@link Slot} under
     * the cursor that holds a non-empty entry. REI widgets are
     * {@link ContainerEventHandler}s, so nested groups/panels expose their
     * children and we can descend into them.
     */
    private static Slot findHoveredSlot(List<? extends GuiEventListener> children, double mx, double my, int depth) {
        if (children == null || depth > MAX_DEPTH) return null;
        for (GuiEventListener child : children) {
            if (child instanceof Slot slot && slot.containsMouse(mx, my)) {
                EntryStack<?> entry = slot.getCurrentEntry();
                if (entry != null && !entry.isEmpty()) return slot;
            }
            if (child instanceof ContainerEventHandler container) {
                Slot found = findHoveredSlot(container.children(), mx, my, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }
}
