package nl.ljack2k.jackittome.network;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.client.ClientFeedback;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → client. Reports the outcome of the player's pull request.
 * <p>
 * {@code moved > 0} means at least one item went into the player's inventory;
 * the client plays the success (falling-into-hotbar) animation.
 * {@code moved == 0} means nothing was available; the client plays the
 * failure (shake-and-fade) animation plus a "denied" sound.
 * <p>
 * The packet is always sent after a pull request so the client only ever
 * animates after server confirmation — no more optimistic animations that
 * get contradicted by a late failure response.
 * <p>
 * Note on safety: this class references {@link ClientFeedback} from the
 * {@link #handle(IPayloadContext)} body. That's a client-only class, but
 * Java's class resolution is lazy at the method-body level — the JVM
 * doesn't try to load {@code ClientFeedback} until {@code handle()} is
 * invoked. The packet is registered as play-to-client only, so the server
 * never invokes {@code handle()}, so the server never tries to load the
 * client-only class. Safe.
 */
public record JackFeedbackPayload(ItemStack item, int moved) implements CustomPacketPayload {

    public static final Type<JackFeedbackPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JackItToMe.MODID, "jack_feedback"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JackFeedbackPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC, JackFeedbackPayload::item,
                    ByteBufCodecs.VAR_INT,           JackFeedbackPayload::moved,
                    JackFeedbackPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientFeedback.handle(item, moved));
    }
}
