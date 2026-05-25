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
 * Client → server. "Operate on these ingredients in the context of my
 * currently-open container."
 * <p>
 * The two booleans are independent and decide what the server does:
 * <ul>
 *   <li>{@code pullAvailable} — extract every ingredient the source has in
 *       stock and place it in the player's inventory.</li>
 *   <li>{@code triggerAutocraft} — for ingredients the source can't fulfill
 *       but reports as autocraftable, queue the chain of native AE2/RS
 *       autocraft popups (via {@link AutocraftChainPayload}) or, for a
 *       single-ingredient pull, escalate directly to a popup.</li>
 * </ul>
 * Both flags can be set together (Shift+click on the J button: pull what's
 * there AND start autocrafting). Both can be set on a single-item P
 * keybind too — pull if in stock, else autocraft if craftable.
 *
 * @param ingredients       ingredients to operate on
 * @param mode              how much to pull (single craft / stack / max).
 *                          Irrelevant if {@code pullAvailable} is false.
 * @param pullAvailable     pull in-stock ingredients into the player's inventory
 * @param triggerAutocraft  open autocraft popups for missing-but-craftable
 *                          ingredients
 */
public record PullIngredientsPayload(List<Ingredient> ingredients,
                                     PullMode mode,
                                     boolean pullAvailable,
                                     boolean triggerAutocraft) implements CustomPacketPayload {

    public static final Type<PullIngredientsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JackItToMe.MODID, "pull_ingredients"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PullIngredientsPayload> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()),
            PullIngredientsPayload::ingredients,
            ByteBufCodecs.VAR_INT.map(PullMode::fromOrdinal, PullMode::ordinal),
            PullIngredientsPayload::mode,
            ByteBufCodecs.BOOL,
            PullIngredientsPayload::pullAvailable,
            ByteBufCodecs.BOOL,
            PullIngredientsPayload::triggerAutocraft,
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
