package nl.ljack2k.jackittome.client;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.network.RequestAutocraftPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Client-side queue for sequential autocraft popups.
 * <p>
 * Triggered by a Shift-click on the JEI recipe-pull (J) button when there are
 * missing-but-craftable ingredients: the server sends back an
 * {@link nl.ljack2k.jackittome.network.AutocraftChainPayload} listing those
 * ingredients, and this controller fires their popups one at a time.
 * <p>
 * Why sequential and not parallel: Minecraft only renders one Screen at a
 * time. Opening another popup while one is up would replace the visible one
 * immediately — the user would never get to enter an amount on most of them.
 * So we open the first, wait until that popup is fully dismissed, then open
 * the next.
 * <p>
 * <b>"Popup dismissed" detection</b>: this is subtle because AE2's flow is
 * multi-screen — {@code CraftAmountScreen → CraftConfirmScreen → terminal}.
 * Triggering on the close of {@code CraftAmountScreen} alone would fire the
 * next popup mid-flow, replacing {@code CraftConfirmScreen} before the user
 * can confirm. Instead, we hook {@link ScreenEvent.Opening} and advance only
 * when an opened screen is <em>not</em> one of the known popup screens AND we
 * had previously opened a popup. That waits for the full sequence to exit.
 * <p>
 * Pop-up screen recognition is by class name substring — AE2 ships
 * {@code CraftAmountScreen} / {@code CraftConfirmScreen} / etc., RS ships
 * {@code AutocraftingPreviewScreen}. The substrings {@code "craftamount"},
 * {@code "craftconfirm"}, {@code "autocrafting"} cover all of them and don't
 * collide with regular terminal screens ({@code MEStorageScreen},
 * {@code GridScreen}).
 */
@EventBusSubscriber(modid = JackItToMe.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class AutocraftChainController {
    private AutocraftChainController() {}

    private static final Deque<ItemStack> PENDING = new ArrayDeque<>();
    /** True once we've sent the first request — guards against false advances. */
    private static boolean firstFired = false;
    /**
     * True while we're currently displaying a popup screen (or its successor
     * popup screen like AE2's CraftConfirmScreen). The advance trigger fires
     * on the transition from true → "opening a non-popup screen".
     */
    private static boolean inPopupFlow = false;

    /** Replace any in-progress chain with a new one and fire popup #1. */
    public static synchronized void startChain(List<ItemStack> items) {
        PENDING.clear();
        firstFired = false;
        inPopupFlow = false;
        for (ItemStack s : items) {
            if (s != null && !s.isEmpty()) PENDING.add(s.copy());
        }
        if (PENDING.isEmpty()) return;
        fireNext();
    }

    private static synchronized void fireNext() {
        ItemStack next = PENDING.poll();
        if (next == null) {
            JackItToMe.LOGGER.debug("[JackItToMe] Autocraft chain complete.");
            firstFired = false;
            inPopupFlow = false;
            return;
        }
        firstFired = true;
        JackItToMe.LOGGER.info("[JackItToMe] Chain: requesting autocraft popup for {} ({} left in queue)",
                next.getHoverName().getString(), PENDING.size());
        PacketDistributor.sendToServer(new RequestAutocraftPayload(next));
    }

    /**
     * Hook every screen-open event. Advance the chain when we transition from
     * "showing a popup" to "showing a regular screen" (terminal, grid, JEI
     * recipe view, no screen) — that's the moment the user has fully exited
     * the popup flow.
     */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!firstFired) return;
        Screen next = event.getNewScreen();
        boolean nextIsPopup = isPopupScreen(next);
        boolean wasPopup = inPopupFlow;
        inPopupFlow = nextIsPopup;

        if (wasPopup && !nextIsPopup && !PENDING.isEmpty()) {
            // Defer one tick so the open transition fully settles before we
            // open the next popup over it.
            Minecraft.getInstance().tell(AutocraftChainController::fireNext);
        }
    }

    /**
     * Also handle the case where the popup is closed without anything opening
     * after it (i.e. the world loses its screen entirely, like AE2 closing
     * back to no menu). The Opening event fires for new = null in that case,
     * but to be safe we also watch Closing.
     */
    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (!firstFired) return;
        if (PENDING.isEmpty()) return;
        if (!isPopupScreen(event.getScreen())) return;
        // Defer a couple ticks — the popup-flow successor (e.g. CraftConfirm)
        // might still be incoming. If by then no popup is open, advance.
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> mc.tell(() -> {
            if (!isPopupScreen(mc.screen) && !PENDING.isEmpty()) {
                inPopupFlow = false;
                fireNext();
            }
        }));
    }

    /**
     * Class-name substring check. Covers AE2's {@code CraftAmountScreen} and
     * {@code CraftConfirmScreen} plus the related popup-flow screens, and
     * RS's {@code AutocraftingPreviewScreen} / {@code FullscreenTreePreviewScreen}.
     * Doesn't collide with regular terminal / grid screens.
     */
    private static boolean isPopupScreen(Screen s) {
        if (s == null) return false;
        String name = s.getClass().getName().toLowerCase();
        return name.contains("craftamount")
                || name.contains("craftconfirm")
                || name.contains("autocrafting")
                || name.contains("crafterror"); // AE2 CraftErrorScreen — still part of the flow
    }
}
