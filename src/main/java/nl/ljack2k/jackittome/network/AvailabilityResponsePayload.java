package nl.ljack2k.jackittome.network;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.client.AvailabilityCache;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Server → client. The shortage result for a {@link CheckAvailabilityPayload}.
 * {@code shortages[i]} is {@code true} when the i-th ingredient (in the order
 * sent) cannot be filled given the simulated pull.
 * <p>
 * The {@code nonce} is echoed from the request so the client can match this
 * response to the correct hover; if a newer request has since fired, this
 * response is silently dropped.
 */
public record AvailabilityResponsePayload(long nonce, List<Boolean> shortages) implements CustomPacketPayload {

    public static final Type<AvailabilityResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JackItToMe.MODID, "availability_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AvailabilityResponsePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,                          AvailabilityResponsePayload::nonce,
            ByteBufCodecs.BOOL.apply(ByteBufCodecs.list()),  AvailabilityResponsePayload::shortages,
            AvailabilityResponsePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> AvailabilityCache.receive(nonce, shortages));
    }
}
