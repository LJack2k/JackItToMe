package nl.ljack2k.jackittome.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * One recipe slot's requirement: how many items the slot needs, and every item
 * variant that would satisfy it. "Any plank" is one request with four variants;
 * "3 paper" is one request with a single variant and an amount of three.
 * <p>
 * The record normalizes on construction so there is exactly one valid shape:
 * {@link #variants} are count-1 templates and {@link #amount} is the only place
 * a quantity lives. Nothing downstream has to decide which of the two to trust.
 *
 * @param amount   how many items the slot needs; never negative
 * @param variants count-1 templates, any of which satisfies the slot. Empty for
 *                 a recipe's blank slots, which every consumer skips.
 */
public record IngredientRequest(int amount, List<ItemStack> variants) {

    /** A recipe slot with nothing in it. Kept in the list so slot indices line up. */
    public static final IngredientRequest EMPTY = new IngredientRequest(0, List.of());

    public static final StreamCodec<RegistryFriendlyByteBuf, IngredientRequest> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,                        IngredientRequest::amount,
                    ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), IngredientRequest::variants,
                    IngredientRequest::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, List<IngredientRequest>> LIST_STREAM_CODEC =
            STREAM_CODEC.apply(ByteBufCodecs.list());

    public IngredientRequest {
        amount = Math.max(0, amount);
        variants = variants.stream()
                .filter(stack -> !stack.isEmpty())
                .map(stack -> stack.copyWithCount(1))
                .toList();
    }

    /**
     * A slot wanting {@code amount} of one specific item.
     */
    public static IngredientRequest of(int amount, ItemStack variant) {
        return new IngredientRequest(amount, List.of(variant));
    }

    /**
     * A slot wanting {@code amount} of any one of {@code variants}, or
     * {@link #EMPTY} when the slot turned out to hold nothing.
     */
    public static IngredientRequest of(int amount, List<ItemStack> variants) {
        return variants.isEmpty() ? EMPTY : new IngredientRequest(amount, variants);
    }

    /** True for a recipe's blank slots. */
    public boolean isEmpty() {
        return variants.isEmpty();
    }

    /** The first acceptable variant, or {@link ItemStack#EMPTY} for a blank slot. */
    public ItemStack firstVariant() {
        return variants.isEmpty() ? ItemStack.EMPTY : variants.getFirst();
    }
}
