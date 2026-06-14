package nl.ljack2k.jackittome.client;

import nl.ljack2k.jackittome.JackItToMe;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.api.RefinedStorageClientApi;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Client-side bridge for opening RS's autocrafting preview popup.
 * <p>
 * Why this exists separately from the AE2 path: AE2's autocraft popup is a
 * proper {@code AbstractContainerMenu} which the server opens via
 * {@code MenuOpener.open(...)}. RS's preview is opened by the client via
 * {@code RefinedStorageClientApi#openAutocraftingPreview} — a STABLE public
 * API (since RS 2.0.0-milestone.4.11) that wraps the
 * resource → AutocraftingRequest → AutocraftingPreviewScreen flow internally,
 * including the round-trip to the server for the preview calculation.
 * <p>
 * Safe-to-load: this class references RS classes directly (no reflection),
 * but it's only ever referenced from the lambda body of
 * {@link nl.ljack2k.jackittome.network.OpenRsAutocraftPayload#handle}, which
 * itself short-circuits when RS isn't loaded. On a dedicated server or in a
 * world without RS, this class never gets class-loaded.
 */
public final class RsAutocraftClient {
    private RsAutocraftClient() {}

    public static void openPopup(ItemStack stack, int amount) {
        if (stack.isEmpty()) return;
        try {
            ResourceKey resource = new ItemResource(stack.getItem(), stack.getComponentsPatch());
            // RS uses long for amounts; clamp to ≥1.
            long initial = Math.max(1, (long) amount);
            ResourceAmount request = new ResourceAmount(resource, initial);
            // RS's autocraft preview is a MENU-BACKED screen (AbstractAmountScreen
            // over AutocraftingPreviewContainerMenu). If we open it while a recipe
            // viewer (JEI/EMI/REI) screen is showing, the menu transition fights
            // the viewer's own recipe screen and the two bounce open/closed
            // forever. The cure is to return RS to the container screen the
            // recipe view is hosted over (the RS grid) — RS's normal context —
            // not the viewer's recipe screen.
            Screen current = Minecraft.getInstance().screen;
            Screen parent;
            if (current instanceof AbstractContainerScreen<?>) {
                parent = current; // already at the grid/terminal
            } else {
                // In a recipe viewer (or no screen): ask the active viewer bridge
                // for the underlying container screen to return to.
                parent = ClientEvents.activeBridge().getHostContainerScreen();
            }
            RefinedStorageClientApi.INSTANCE.openAutocraftingPreview(
                    List.of(request),
                    parent);
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("[RS] RsAutocraftClient.openPopup failed: {}", t.toString());
        }
    }
}
