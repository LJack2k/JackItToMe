package nl.ljack2k.jackittome.network;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.server.PullHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Client → server. "Pull these ingredients into my inventory from whatever
 * container I currently have open."
 *
 * @param ingredients ingredients to pull
 * @param mode        how much to pull (single craft / stack / max)
 */
public record PullIngredientsPayload(List<Ingredient> ingredients, PullMode mode) implements CustomPacketPayload {

    public static final Type<PullIngredientsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JackItToMe.MODID, "pull_ingredients"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PullIngredientsPayload> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()),
            PullIngredientsPayload::ingredients,
            ByteBufCodecs.VAR_INT.map(PullMode::fromOrdinal, PullMode::ordinal),
            PullIngredientsPayload::mode,
            PullIngredientsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server-side handler. Called on the network thread — bounce to the main thread. */
    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                PullHandler.handle(sp, this);
            }
        });
    }
}
