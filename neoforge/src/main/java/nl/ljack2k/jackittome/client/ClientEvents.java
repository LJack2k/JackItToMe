package nl.ljack2k.jackittome.client;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.jei.JeiViewerBridge;
import nl.ljack2k.jackittome.emi.EmiViewerBridge;
import nl.ljack2k.jackittome.rei.ReiViewerBridge;
import nl.ljack2k.jackittome.network.PullIngredientsPayload;
import nl.ljack2k.jackittome.network.PullMode;

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
 * REI category extension). This class only handles the P keybind.
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

        int pressedKey = event.getKeyCode();
        int boundKey   = KeyBindings.JACK_HOVERED.getKey().getValue();
        if (pressedKey != boundKey) return;

        handleJackHovered(mc);
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

    private static void handleJackHovered(Minecraft mc) {
        Ingredient ing = tryRecipeSlotIngredient(mc);

        if (ing == null) {
            ItemStack hovered = hoveredItemStack(mc);
            if (hovered.isEmpty()) {
                JackItToMe.LOGGER.debug("[JackItToMe] Jack key pressed but no hovered item found.");
                return;
            }
            ing = Ingredient.of(hovered);
        }

        PullMode mode;
        if (Screen.hasControlDown())     mode = PullMode.MAX;
        else if (Screen.hasShiftDown())  mode = PullMode.STACK;
        else                             mode = PullMode.SINGLE;
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
        return Ingredient.of(variants.toArray(ItemStack[]::new));
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
