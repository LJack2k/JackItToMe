package nl.ljack2k.jackittome.server;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.network.RequestAutocraftPayload;
import nl.ljack2k.jackittome.source.ItemSource;
import nl.ljack2k.jackittome.source.ItemSourceRegistry;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side handler for {@link RequestAutocraftPayload}. Delegates straight
 * to whichever {@link ItemSource} matches the player's current menu — only
 * the AE2 and RS sources actually open a popup; the vanilla source's default
 * returns {@code false} and nothing happens.
 */
public final class AutocraftHandler {
    private AutocraftHandler() {}

    public static void handle(ServerPlayer player, RequestAutocraftPayload req) {
        ItemStack stack = req.stack();
        if (stack.isEmpty()) {
            JackItToMe.LOGGER.debug("[JackItToMe] Autocraft request received with empty stack — ignoring.");
            return;
        }
        ItemSource source = ItemSourceRegistry.findSource(player);
        if (source == null) {
            JackItToMe.LOGGER.debug("[JackItToMe] Autocraft request: no source for {}", player.getName().getString());
            return;
        }
        // The stack's count carries the aggregated shortfall: how many of the
        // item the player actually needs. The popup uses that as its initial
        // value so a "missing 2 planks" pull pre-fills the popup with 2.
        int amount = Math.max(1, stack.getCount());
        boolean opened = source.openAutoCraftPopup(stack, amount, player);
        if (!opened) {
            JackItToMe.LOGGER.debug("[JackItToMe] Autocraft popup refused for {} ({}): source returned false",
                    stack.getHoverName().getString(), source.getClass().getSimpleName());
        }
    }
}
