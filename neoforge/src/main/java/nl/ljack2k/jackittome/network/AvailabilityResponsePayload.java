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
 * Server → client. Per-slot availability data for a
 * {@link CheckAvailabilityPayload}.
 * <p>
 * Each slot gets two parallel booleans:
 * <ul>
 *   <li>{@code shortages[i]} — {@code true} when the i-th ingredient cannot be
 *       fulfilled from raw stock alone (i.e. it'd need autocrafting or it's
 *       simply unavailable). Drives the red/green overlay.</li>
 *   <li>{@code craftable[i]} — {@code true} when the source reports this
 *       ingredient as autocraftable. Used to paint the overlay green instead
 *       of red and to drive the Shift-click chain of autocraft popups.</li>
 * </ul>
 * The two lists must have the same length as the request's ingredient list;
 * the simulator fills both in lockstep. {@code craftable[i]} is meaningful
 * only when {@code shortages[i]} is true — if the ingredient was fulfilled
 * from stock, we don't bother checking craftability.
 * <p>
 * The {@code nonce} is echoed from the request so the client can match this
 * response to the correct hover; if a newer request has since fired, this
 * response is silently dropped.
 */
public record AvailabilityResponsePayload(long nonce,
                                          List<Boolean> shortages,
                                          List<Boolean> craftable) implements CustomPacketPayload {

    public static final Type<AvailabilityResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JackItToMe.MODID, "availability_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AvailabilityResponsePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,                          AvailabilityResponsePayload::nonce,
            ByteBufCodecs.BOOL.apply(ByteBufCodecs.list()),  AvailabilityResponsePayload::shortages,
            ByteBufCodecs.BOOL.apply(ByteBufCodecs.list()),  AvailabilityResponsePayload::craftable,
            AvailabilityResponsePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> AvailabilityCache.receive(nonce, shortages, craftable));
    }
}
