package nl.ljack2k.jackittome.network;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.server.AvailabilityHandler;

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
 * Client → server. "Tell me which of these ingredients I would fall short on
 * if I tried to pull all of them right now."
 * <p>
 * Sent when the player hovers the recipe-pull button. The server simulates the
 * pull (in the player's currently open container/network) and responds with a
 * per-ingredient shortage list. The {@code nonce} lets the client match the
 * response to the exact hover that triggered it — if the user mouses over a
 * different button before the response arrives, we ignore the stale data.
 */
public record CheckAvailabilityPayload(long nonce, List<Ingredient> ingredients) implements CustomPacketPayload {

    public static final Type<CheckAvailabilityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JackItToMe.MODID, "check_availability"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CheckAvailabilityPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,                                           CheckAvailabilityPayload::nonce,
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()),     CheckAvailabilityPayload::ingredients,
            CheckAvailabilityPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                AvailabilityHandler.handle(sp, this);
            }
        });
    }
}
