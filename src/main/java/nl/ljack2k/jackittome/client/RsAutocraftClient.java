package nl.ljack2k.jackittome.client;

import nl.ljack2k.jackittome.JackItToMe;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.api.RefinedStorageClientApi;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import net.minecraft.client.Minecraft;
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
            // parentScreen is @Nullable in the RS API; we pass the current screen
            // when there is one so RS's "back" navigation lands on whatever the
            // player was looking at (JEI recipe view, the grid screen, etc.).
            RefinedStorageClientApi.INSTANCE.openAutocraftingPreview(
                    List.of(request),
                    Minecraft.getInstance().screen);
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("[RS] RsAutocraftClient.openPopup failed: {}", t.toString());
        }
    }
}
