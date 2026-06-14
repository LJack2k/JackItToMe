package nl.ljack2k.jackittome.network;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.client.RsAutocraftClient;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → client. "Hey client, please open RS's autocraft preview popup for
 * this item, pre-filled with this amount." Sent only by
 * {@code RsItemSource#openAutoCraftPopup} because RS's preview screen is a
 * regular {@code Screen} (not a menu) and must be opened from the client.
 * <p>
 * {@code amount} is the shortfall the user needs (aggregated across recipe
 * slots), so a missing-2-planks situation pre-fills the popup with 2, not 1.
 */
public record OpenRsAutocraftPayload(ItemStack stack, int amount) implements CustomPacketPayload {

    public static final Type<OpenRsAutocraftPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JackItToMe.MODID, "open_rs_autocraft"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRsAutocraftPayload> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, OpenRsAutocraftPayload::stack,
            ByteBufCodecs.VAR_INT,           OpenRsAutocraftPayload::amount,
            OpenRsAutocraftPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ModList.get().isLoaded("refinedstorage")) {
                JackItToMe.LOGGER.debug("Received OpenRsAutocraftPayload but RS isn't loaded — ignoring");
                return;
            }
            RsAutocraftClient.openPopup(stack, amount);
        });
    }
}
