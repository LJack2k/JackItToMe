package nl.ljack2k.jackittome.source;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * An abstract "place items can be pulled from on behalf of the player".
 * <p>
 * Implementations:
 * <ul>
 *   <li>{@code ContainerItemSource} — any vanilla-style {@code AbstractContainerMenu}
 *       (chest, barrel, shulker, double chest, etc.)</li>
 *   <li>{@code Ae2ItemSource} — AE2 ME network behind a terminal-like menu</li>
 *   <li>{@code RsItemSource} — Refined Storage network behind a Grid menu</li>
 * </ul>
 * The contract is intentionally tiny: match, count, extract. That's all the pull
 * orchestrator needs.
 */
public interface ItemSource {

    /**
     * Does this source apply to the player's currently-open menu?
     * <p>
     * Sources are queried in registration order and the first match wins, so
     * register specific sources (AE2 terminal, RS grid) <em>before</em> the
     * generic vanilla container source.
     */
    boolean matches(ServerPlayer player);

    /**
     * How many of {@code template}'s item exist in this source right now?
     * Match by item identity + components ({@code ItemStack.isSameItemSameComponents}).
     */
    long count(ItemStack template, ServerPlayer player);

    /**
     * Try to extract up to {@code amount} of {@code template} from this source.
     *
     * @return a stack of the same item with count ≤ {@code amount}, or
     *         {@link ItemStack#EMPTY} if nothing was available. Never null.
     */
    ItemStack extract(ItemStack template, int amount, ServerPlayer player);

    /**
     * Best-effort return path. If the player's inventory filled up mid-pull and
     * we have leftover items, we try to put them back where they came from. If
     * even that fails, drop them at the player's feet.
     */
    default void insertOrDrop(ItemStack stack, ServerPlayer player) {
        if (stack.isEmpty()) return;
        player.drop(stack, false);
    }

    /**
     * Optional: does this source's backing storage system know how to autocraft
     * {@code template}? Implemented by AE2 (via {@code ICraftingService}) and
     * Refined Storage (via {@code Grid#getAutocraftableResources}). Vanilla
     * containers obviously can't autocraft anything, so the default is false.
     * <p>
     * Used in two places:
     * <ol>
     *   <li>{@link nl.ljack2k.jackittome.server.AvailabilityHandler} flags
     *       missing recipe slots as "missing-but-craftable" when this returns
     *       true, so the J button's hover preview can paint them green.</li>
     *   <li>{@link nl.ljack2k.jackittome.server.PullHandler} escalates a P-key
     *       press on a 0-in-stock craftable item to a popup, rather than
     *       playing the red failure animation.</li>
     * </ol>
     */
    default boolean isAutocraftable(ItemStack template, ServerPlayer player) {
        return false;
    }

    /**
     * Optional: ask this source to open its native "how many to craft?" popup
     * for {@code template}, pre-filled with {@code amount} as the desired
     * count. Returns true if the popup was opened (the player's
     * {@code containerMenu} now points at AE2's CraftAmountMenu or RS's
     * autocrafting preview), false on any failure or if the source doesn't
     * support autocrafting popups.
     * <p>
     * {@code amount} is the shortfall the user actually needs (aggregated
     * across all recipe slots that wanted this item). The popup still lets
     * the user adjust before submitting — we just pre-fill it with the right
     * number so a recipe needing 2 planks doesn't make AE2 craft 8 (a full
     * batch from a 1→4 plank pattern * 2 popups).
     * <p>
     * Implementations should be defensive — catch and log; never throw.
     */
    default boolean openAutoCraftPopup(ItemStack template, int amount, ServerPlayer player) {
        return false;
    }
}
