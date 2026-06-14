package nl.ljack2k.jackittome.emi;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.client.AvailabilityCache;
import nl.ljack2k.jackittome.client.RecipeViewerBridge;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStackInteraction;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.List;

public final class EmiViewerBridge implements RecipeViewerBridge {

    /** EMI's recipe-display screen (lives in the runtime jar, not the api jar). */
    private static final String RECIPE_SCREEN_CLASS = "dev.emi.emi.screen.RecipeScreen";

    @Override
    public ItemStack getHoveredItem(Screen screen, double mouseX, double mouseY) {
        // 1) Inside a recipe display, EMI exposes the hovered ingredient on the
        //    screen itself — EmiApi.getHoveredStack only covers the sidebars.
        if (isRecipeScreen(screen)) {
            ItemStack inRecipe = hoveredInRecipeScreen(screen);
            if (!inRecipe.isEmpty()) return inRecipe;
        }
        // 2) Sidebars (index / favorites) — works on any screen with EMI open.
        try {
            EmiStackInteraction interaction = EmiApi.getHoveredStack((int) mouseX, (int) mouseY, false);
            if (interaction != null && !interaction.isEmpty()) {
                return EmiStacks.first(interaction.getStack());
            }
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("[JackItToMe] EMI sidebar hover lookup failed: {}", t.toString());
        }
        return ItemStack.EMPTY;
    }

    @Override
    public List<ItemStack> getRecipeSlotVariants(Screen screen, double mouseX, double mouseY) {
        ItemStack hov = getHoveredItem(screen, mouseX, mouseY);
        return hov.isEmpty() ? List.of() : List.of(hov);
    }

    @Override
    public boolean isRecipeScreen(Screen screen) {
        if (screen == null) return false;
        // Match EMI's recipe screen specifically (and subclasses) so we don't
        // false-trigger on EMI's other screens (config, etc.).
        for (Class<?> c = screen.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.getName().equals(RECIPE_SCREEN_CLASS)) return true;
        }
        return false;
    }

    @Override
    public void onRecipeScreenClosed() {
        AvailabilityCache.clear();
    }

    @Override
    public AbstractContainerScreen<?> getHostContainerScreen() {
        // The inventory/terminal screen EMI's recipe view is shown over.
        try {
            return EmiApi.getHandledScreen();
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("[JackItToMe] EMI getHandledScreen failed: {}", t.toString());
            return null;
        }
    }

    // ---- helpers --------------------------------------------------------

    /**
     * Reflectively call {@code RecipeScreen.getHoveredStack()} — the method is
     * public but the class lives in EMI's runtime jar (we compile only against
     * the api jar). Returns the hovered ingredient's first ItemStack variant.
     */
    private static ItemStack hoveredInRecipeScreen(Screen screen) {
        try {
            Method m = screen.getClass().getMethod("getHoveredStack");
            Object result = m.invoke(screen);
            if (result instanceof EmiIngredient ing) {
                return EmiStacks.first(ing);
            }
        } catch (NoSuchMethodException ignored) {
            // Not the screen we expected — fall through to sidebar lookup.
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("[JackItToMe] EMI recipe-screen hover lookup failed: {}", t.toString());
        }
        return ItemStack.EMPTY;
    }
}
