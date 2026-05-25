package nl.ljack2k.jackittome.network;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.server.AutocraftHandler;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server. "Open the native AE2/RS autocraft popup for this item
 * against whatever terminal/grid I currently have open."
 * <p>
 * The server resolves the {@link nl.ljack2k.jackittome.source.ItemSource} for
 * the player's currently-open menu and calls
 * {@link nl.ljack2k.jackittome.source.ItemSource#openAutoCraftPopup} on it.
 * AE2's source opens {@code CraftAmountMenu} via {@code MenuOpener}; RS's
 * source bounces back an {@link OpenRsAutocraftPayload} so the client can use
 * RS's own {@code AutocraftingRequest} helper (RS's popup is a regular
 * {@code Screen}, not a menu, so it has to be opened client-side).
 */
public record RequestAutocraftPayload(ItemStack stack) implements CustomPacketPayload {

    public static final Type<RequestAutocraftPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JackItToMe.MODID, "request_autocraft"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestAutocraftPayload> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, RequestAutocraftPayload::stack,
            RequestAutocraftPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                AutocraftHandler.handle(sp, this);
            }
        });
    }
}
