package nl.ljack2k.jackittome.client;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.jei.JeiViewerBridge;
import nl.ljack2k.jackittome.emi.EmiViewerBridge;
import nl.ljack2k.jackittome.rei.ReiViewerBridge;
import nl.ljack2k.jackittome.network.PullIngredientsPayload;
import nl.ljack2k.jackittome.network.PullMode;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Client-side keybind and screen-event handler.
 * <p>
 * Viewer-specific logic (JEI / EMI / REI) is fully isolated behind
 * {@link RecipeViewerBridge} so this class never directly imports viewer
 * classes, avoiding {@link ClassNotFoundException} when a viewer is absent.
 * <p>
 * The recipe-screen pull button is added by each viewer's own plugin using
 * that viewer's native widget API (JEI button factory, EMI recipe decorator,
 * REI category extension). This class only handles the three pull keybinds
 * (defaults G / Shift+G / Ctrl+G — see {@link KeyBindings}).
 */
@EventBusSubscriber(modid = JackItToMe.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    // Lazily initialised on first use (all client events fire on the client thread).
    private static RecipeViewerBridge bridge;

    private static RecipeViewerBridge bridge() {
        if (bridge == null) bridge = initBridge();
        return bridge;
    }

    /** Package-private accessor for the active viewer bridge (used by RsAutocraftClient). */
    static RecipeViewerBridge activeBridge() {
        return bridge();
    }

    private static RecipeViewerBridge initBridge() {
        if (ModList.get().isLoaded("jei"))                return new JeiViewerBridge();
        if (ModList.get().isLoaded("emi"))                return new EmiViewerBridge();
        if (ModList.get().isLoaded("roughlyenoughitems")) return new ReiViewerBridge();
        return new NullRecipeViewerBridge();
    }

    // ---- Keybind --------------------------------------------------------

    @SubscribeEvent
    public static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Most specific first: with the default layout all three share the
        // physical G key and differ only in KeyModifier, and NONE-modifier
        // binds also match while a modifier is held — so checking MAX before
        // STACK before SINGLE keeps "Ctrl beats Shift beats plain".
        InputConstants.Key key = InputConstants.getKey(event.getKeyCode(), event.getScanCode());
        PullMode mode;
        if      (KeyBindings.JACK_MAX.isActiveAndMatches(key))     mode = PullMode.MAX;
        else if (KeyBindings.JACK_STACK.isActiveAndMatches(key))   mode = PullMode.STACK;
        else if (KeyBindings.JACK_HOVERED.isActiveAndMatches(key)) mode = PullMode.SINGLE;
        else return;

        handleJackHovered(mc, mode);
        event.setCanceled(true);
    }

    // ---- Render ---------------------------------------------------------

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        JackAnimations.render(event.getGuiGraphics(), event.getScreen());
    }

    // ---- Screen close ---------------------------------------------------

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        Screen s = event.getScreen();
        if (s != null && bridge().isRecipeScreen(s)) {
            bridge().onRecipeScreenClosed();
        }
    }

    // ---- Pull logic -----------------------------------------------------

    private static void handleJackHovered(Minecraft mc, PullMode mode) {
        Ingredient ing = tryRecipeSlotIngredient(mc);

        if (ing == null) {
            ItemStack hovered = hoveredItemStack(mc);
            if (hovered.isEmpty()) {
                JackItToMe.LOGGER.debug("[JackItToMe] Jack key pressed but no hovered item found.");
                return;
            }
            // Normalize to count 1: the server reads the ingredient's embedded
            // count as the per-slot request ("recipe wants 3"), but a hovered
            // container slot carries however many happen to be stacked there —
            // wrapping a 64-stack unnormalized made SINGLE pull the whole stack.
            ing = Ingredient.of(hovered.copyWithCount(1));
        }

        JackItToMe.LOGGER.info("[JackItToMe] Jack key pressed — mode={}", mode);

        PacketDistributor.sendToServer(new PullIngredientsPayload(
                List.of(ing), mode, /*pullAvailable=*/ true, /*triggerAutocraft=*/ true));
    }

    private static Ingredient tryRecipeSlotIngredient(Minecraft mc) {
        Screen screen = mc.screen;
        if (screen == null || !bridge().isRecipeScreen(screen)) return null;

        Window win = mc.getWindow();
        double mx = mc.mouseHandler.xpos() * win.getGuiScaledWidth()  / (double) win.getScreenWidth();
        double my = mc.mouseHandler.ypos() * win.getGuiScaledHeight() / (double) win.getScreenHeight();

        List<ItemStack> variants = bridge().getRecipeSlotVariants(screen, mx, my);
        if (variants.size() < 2) return null;

        JackItToMe.LOGGER.info("[JackItToMe] Requesting recipe slot with {} acceptable variants.", variants.size());
        // Same count normalization as the plain hover path: the keybind means
        // "pull one" (modifiers scale it) — recipe-slot display counts are not
        // the request.
        return Ingredient.of(variants.stream()
                .map(s -> s.copyWithCount(1))
                .toArray(ItemStack[]::new));
    }

    private static ItemStack hoveredItemStack(Minecraft mc) {
        Screen screen = mc.screen;
        if (screen == null) return ItemStack.EMPTY;

        // 1) Container slot under mouse — vanilla path.
        if (screen instanceof AbstractContainerScreen<?> acs) {
            Slot slot = acs.getSlotUnderMouse();
            if (slot != null && !slot.getItem().isEmpty()) return slot.getItem();
        }

        // 2) Recipe viewer overlay / sidebar / recipe screen.
        Window win = mc.getWindow();
        double mx = mc.mouseHandler.xpos() * win.getGuiScaledWidth()  / (double) win.getScreenWidth();
        double my = mc.mouseHandler.ypos() * win.getGuiScaledHeight() / (double) win.getScreenHeight();
        return bridge().getHoveredItem(screen, mx, my);
    }
}
