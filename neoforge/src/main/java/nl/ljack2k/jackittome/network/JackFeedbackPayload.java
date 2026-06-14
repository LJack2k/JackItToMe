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

import java.util.List;

/**
 * Server → client. Reports the outcome of a pull request.
 * <p>
 * Two independent fields:
 * <ul>
 *   <li>{@code successItems} — one entry per unique {@code Item} that was
 *       extracted from the source. Each stack's count is the actual amount
 *       moved. The client fires one falling-into-hotbar animation per
 *       entry, fanned out horizontally and staggered in time so they don't
 *       overlap visually.</li>
 *   <li>{@code failureItem} — if non-empty, the client plays the red-shake
 *       failure animation for it. Used for the "P on a missing-and-
 *       uncraftable item" path.</li>
 * </ul>
 * Either or both lists can be present, though in practice we set exactly
 * one: a pull either succeeded (with one or more items) or failed.
 * <p>
 * Note on safety: this class references {@link ClientFeedback} from the
 * {@link #handle(IPayloadContext)} body. That's a client-only class, but
 * Java's class resolution is lazy at the method-body level — the JVM
 * doesn't try to load {@code ClientFeedback} until {@code handle()} is
 * invoked. The packet is registered as play-to-client only, so the server
 * never invokes {@code handle()}, so the server never tries to load the
 * client-only class. Safe.
 */
public record JackFeedbackPayload(List<ItemStack> successItems, ItemStack failureItem) implements CustomPacketPayload {

    public static final Type<JackFeedbackPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JackItToMe.MODID, "jack_feedback"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JackFeedbackPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()),
                    JackFeedbackPayload::successItems,
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    JackFeedbackPayload::failureItem,
                    JackFeedbackPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientFeedback.handle(successItems, failureItem));
    }
}
