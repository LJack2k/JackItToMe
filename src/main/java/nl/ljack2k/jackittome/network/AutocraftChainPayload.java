package nl.ljack2k.jackittome.network;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.client.AutocraftChainController;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Server → client. "These items came back missing-but-craftable for your
 * Shift-clicked recipe — queue an autocraft popup for each, one after the
 * next."
 * <p>
 * Sent by {@link nl.ljack2k.jackittome.server.PullHandler} after a Shift-click
 * pull of a recipe, but only when there are missing-but-craftable ingredients
 * left over. Client-side, {@link AutocraftChainController} holds the queue and
 * fires the next {@link RequestAutocraftPayload} every time the previous
 * AE2/RS popup closes.
 */
public record AutocraftChainPayload(List<ItemStack> queue) implements CustomPacketPayload {

    public static final Type<AutocraftChainPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JackItToMe.MODID, "autocraft_chain"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AutocraftChainPayload> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()),
            AutocraftChainPayload::queue,
            AutocraftChainPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            JackItToMe.LOGGER.info("[JackItToMe] Autocraft chain queued: {} item(s)", queue.size());
            AutocraftChainController.startChain(queue);
        });
    }
}
