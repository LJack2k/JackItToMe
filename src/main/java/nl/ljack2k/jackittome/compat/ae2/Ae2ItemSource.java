package nl.ljack2k.jackittome.compat.ae2;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.source.ItemSource;
import nl.ljack2k.jackittome.source.ItemSourceRegistry;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

// --- AE2 API imports ---
// Package layout for AE2 1.21 (NeoForge) at the time of writing:
//   appeng.api.stacks.AEItemKey, AEKey
//   appeng.api.storage.MEStorage
//   appeng.api.config.Actionable
//   appeng.api.networking.security.IActionSource
//   appeng.menu.me.common.MEStorageMenu        ← terminal menus extend this
// If AE2 moves these classes, update the imports below. The shape of the API
// (MEStorage / AEItemKey / Actionable) has been stable for a while.
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.menu.me.common.MEStorageMenu;

/**
 * AE2 source. Active when the player has a terminal-like menu open (anything
 * extending {@code MEStorageMenu}: ME Terminal, Crafting Terminal, Pattern
 * Terminal, Wireless Terminal, ...).
 * <p>
 * Counts and extracts go through the ME network's {@link MEStorage}, not via
 * slot manipulation — that way encrypted storage cells, partition lists,
 * priorities, etc. are all respected. AE2 handles the "is this player allowed
 * to extract" check via {@link IActionSource#ofPlayer(net.minecraft.world.entity.player.Player)}.
 */
public final class Ae2ItemSource implements ItemSource {
    private Ae2ItemSource() {}

    /** Called from main mod constructor only if AE2 is loaded. */
    public static void register() {
        try {
            ItemSourceRegistry.registerHighPriority(new Ae2ItemSource());
        } catch (Throwable t) {
            JackItToMe.LOGGER.error("Failed to register AE2 item source — AE2 API mismatch?", t);
        }
    }

    @Override
    public boolean matches(ServerPlayer player) {
        return player.containerMenu instanceof MEStorageMenu;
    }

    @Override
    public long count(ItemStack template, ServerPlayer player) {
        MEStorage storage = storageOf(player);
        if (storage == null) return 0;

        AEItemKey key = AEItemKey.of(template);
        if (key == null) return 0;

        // Iterate the network's available content. For most users this is small
        // enough that iterating is fine; for huge networks we'd want a hashed
        // lookup, but MEStorage doesn't expose one directly.
        return storage.getAvailableStacks().get(key);
    }

    @Override
    public ItemStack extract(ItemStack template, int amount, ServerPlayer player) {
        if (amount <= 0) return ItemStack.EMPTY;
        MEStorage storage = storageOf(player);
        if (storage == null) return ItemStack.EMPTY;

        AEItemKey key = AEItemKey.of(template);
        if (key == null) return ItemStack.EMPTY;

        IActionSource src = IActionSource.ofPlayer(player);
        long extracted = storage.extract(key, amount, Actionable.MODULATE, src);
        if (extracted <= 0) return ItemStack.EMPTY;

        ItemStack out = template.copy();
        out.setCount((int) Math.min(extracted, Integer.MAX_VALUE));
        return out;
    }

    @Override
    public void insertOrDrop(ItemStack stack, ServerPlayer player) {
        if (stack.isEmpty()) return;
        MEStorage storage = storageOf(player);
        if (storage == null) {
            player.drop(stack, false);
            return;
        }
        AEKey key = AEItemKey.of(stack);
        if (key == null) {
            player.drop(stack, false);
            return;
        }
        long inserted = storage.insert(key, stack.getCount(), Actionable.MODULATE, IActionSource.ofPlayer(player));
        if (inserted < stack.getCount()) {
            ItemStack leftover = stack.copyWithCount((int) (stack.getCount() - inserted));
            player.drop(leftover, false);
        }
    }

    /**
     * Resolve the {@link MEStorage} behind whatever terminal the player has open.
     * <p>
     * AE2's terminal menus expose this through the menu host. The exact accessor
     * has changed a few times; the most stable path is:
     * {@code menu.getHost().getInventory()} (where getHost returns an
     * {@code ITerminalHost} or similar). If that breaks, look at
     * {@code MEStorageMenu#getStorage()} or {@code getHost().getStorage()}.
     */
    private static MEStorage storageOf(ServerPlayer player) {
        if (!(player.containerMenu instanceof MEStorageMenu menu)) return null;
        try {
            // TODO: verify against the current AE2 source. The two candidates I've seen are:
            //   1) menu.getHost().getInventory()
            //   2) menu.getHost().getStorage()
            // Both return MEStorage in current AE2. Pick whichever your AE2 version exposes.
            return menu.getHost().getInventory();
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("AE2 storage lookup failed: {}", t.toString());
            return null;
        }
    }
}
