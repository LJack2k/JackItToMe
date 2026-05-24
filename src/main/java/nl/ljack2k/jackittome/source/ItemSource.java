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
}
