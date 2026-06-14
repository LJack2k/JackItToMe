package nl.ljack2k.jackittome.jei;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.client.AvailabilityCache;
import nl.ljack2k.jackittome.client.JeiRecipeSlotProbe;
import nl.ljack2k.jackittome.client.RecipeViewerBridge;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Bridge implementation for JEI. Extracts the JEI-specific hover and slot
 * probe logic that previously lived inline in {@code ClientEvents}.
 * <p>
 * The recipe-screen <em>button</em> is still handled entirely by JEI itself
 * via {@link JackPullRecipeButtonFactory} + {@link JackPullButtonController};
 * {@link #getDisplayedRecipeIngredients} and {@link #getRecipeButtonPosition}
 * therefore return empty / null — JEI doesn't need the generic button path.
 */
public final class JeiViewerBridge implements RecipeViewerBridge {

    @Override
    public ItemStack getHoveredItem(Screen screen, double mouseX, double mouseY) {
        IJeiRuntime rt = JackItToMeJeiPlugin.runtime();
        if (rt == null) return ItemStack.EMPTY;

        ItemStack hov = tryOverlayHover(rt.getIngredientListOverlay());
        if (!hov.isEmpty()) return hov;

        hov = tryOverlayHover(rt.getBookmarkOverlay());
        if (!hov.isEmpty()) return hov;

        return tryOverlayHover(rt.getRecipesGui());
    }

    @Override
    public List<ItemStack> getRecipeSlotVariants(Screen screen, double mouseX, double mouseY) {
        return JeiRecipeSlotProbe.getAllItemsUnderMouse(screen, mouseX, mouseY);
    }

    @Override
    public boolean isRecipeScreen(Screen screen) {
        if (screen == null) return false;
        String fqn = screen.getClass().getName().toLowerCase();
        String simple = screen.getClass().getSimpleName().toLowerCase();
        return fqn.contains("jei") && simple.contains("recipe");
    }

    @Override
    public void onRecipeScreenClosed() {
        AvailabilityCache.clear();
    }

    // ---- helpers --------------------------------------------------------

    private static ItemStack tryOverlayHover(Object overlay) {
        if (overlay == null) return ItemStack.EMPTY;
        try {
            for (java.lang.reflect.Method m : overlay.getClass().getMethods()) {
                if (!"getIngredientUnderMouse".equals(m.getName())) continue;
                if (m.getParameterCount() != 1) continue;
                Object result = m.invoke(overlay, VanillaTypes.ITEM_STACK);
                ItemStack unwrapped = unwrap(result);
                if (!unwrapped.isEmpty()) return unwrapped;
                break;
            }
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("[JackItToMe] JEI overlay hover lookup failed ({}): {}",
                    overlay.getClass().getSimpleName(), t.toString());
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack unwrap(Object o) {
        if (o == null) return ItemStack.EMPTY;
        if (o instanceof ItemStack s) return s.isEmpty() ? ItemStack.EMPTY : s;
        if (o instanceof Optional<?> opt) return opt.map(JeiViewerBridge::unwrap).orElse(ItemStack.EMPTY);
        try {
            java.lang.reflect.Method m = o.getClass().getMethod("getIngredient");
            return unwrap(m.invoke(o));
        } catch (NoSuchMethodException ignored) {
            return ItemStack.EMPTY;
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("[JackItToMe] JEI unwrap failed for {}: {}", o.getClass().getName(), t.toString());
            return ItemStack.EMPTY;
        }
    }
}
