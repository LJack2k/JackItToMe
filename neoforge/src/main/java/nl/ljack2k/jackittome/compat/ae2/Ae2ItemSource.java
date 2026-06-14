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
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.menu.locator.MenuHostLocator;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.crafting.CraftAmountMenu;

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
     * Ask AE2's crafting service whether the current network can autocraft
     * {@code template}. Walks the host → grid node →
     * {@link ICraftingService#isCraftable(AEKey)}.
     * <p>
     * The path uses {@code MEStorageMenu.getHost()} (public, returns
     * {@link appeng.api.storage.ITerminalHost ITerminalHost}) and tests
     * whether that host implements {@link IActionHost}. ITerminalHost itself
     * doesn't statically extend IActionHost, but every real implementation
     * (block entities for ME terminals, pattern terminals, etc., and item-
     * based wireless terminals) does — so the {@code instanceof} narrows
     * correctly at runtime. Going through the protected
     * {@code AEBaseMenu.getActionHost()} would compile in AE2's own packages
     * but not in ours.
     */
    @Override
    public boolean isAutocraftable(ItemStack template, ServerPlayer player) {
        if (template.isEmpty()) return false;
        try {
            ICraftingService crafting = craftingServiceOf(player);
            if (crafting == null) return false;
            AEItemKey key = AEItemKey.of(template);
            if (key == null) return false;
            return crafting.isCraftable(key);
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("AE2 isAutocraftable check failed: {}", t.toString());
            return false;
        }
    }

    /**
     * Open AE2's "how much to craft?" amount-selection popup for {@code template}.
     * <p>
     * Uses {@link CraftAmountMenu#open(ServerPlayer, MenuHostLocator, AEKey, int)}
     * — AE2's public static helper that:
     * <ol>
     *   <li>Closes the terminal menu and opens CraftAmountMenu against the
     *       given locator's host.</li>
     *   <li>Calls the internal private {@code setWhatToCraft} so the popup
     *       opens already primed with the item.</li>
     * </ol>
     * The locator we pass is the one the terminal menu was opened with —
     * available via the public {@code AEBaseMenu#getLocator()} inherited by
     * every menu (verified public in AE2 19.2.17).
     * <p>
     * Closing the popup returns the player to the terminal automatically via
     * AE2's {@code ISubMenuHost#returnToMainMenu} — which is what makes the
     * Shift-click chain in {@link nl.ljack2k.jackittome.client.AutocraftChainController}
     * work seamlessly: the player is back at the terminal in time for the
     * next chained popup to open against it.
     */
    @Override
    public boolean openAutoCraftPopup(ItemStack template, int amount, ServerPlayer player) {
        if (template.isEmpty()) return false;
        if (!(player.containerMenu instanceof MEStorageMenu menu)) return false;
        try {
            AEItemKey key = AEItemKey.of(template);
            if (key == null) return false;

            MenuHostLocator locator = menu.getLocator();
            if (locator == null) {
                JackItToMe.LOGGER.debug("AE2 openAutoCraftPopup: menu has no locator");
                return false;
            }

            // Clamp to at least 1 — opening with 0 is undefined and AE2's
            // pattern matcher may still round up, but starting at 1 is the
            // safe default. The shortfall is usually small (single-digit) so
            // overflow on int isn't a concern.
            int initial = Math.max(1, amount);

            // AE2 returns void here, but throws nothing in the happy path. If
            // it fails internally (bad locator, dead host, ...) the player's
            // containerMenu won't transition; we treat that as "not opened".
            CraftAmountMenu.open(player, locator, key, initial);

            if (player.containerMenu instanceof CraftAmountMenu) {
                return true;
            }
            JackItToMe.LOGGER.debug("AE2 CraftAmountMenu.open ran but containerMenu is {} (expected CraftAmountMenu)",
                    player.containerMenu == null ? "null" : player.containerMenu.getClass().getName());
            return false;
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("AE2 openAutoCraftPopup failed: {}", t.toString());
            return false;
        }
    }

    /**
     * Reach the network's {@link ICraftingService} for the player's open terminal.
     * <p>
     * Path: {@code menu.getHost()} (public, ITerminalHost) →
     * {@code instanceof IActionHost} (narrowing — every real terminal host
     * implements it) → {@code IActionHost.getActionableNode()} →
     * {@code IGridNode.getGrid()} → {@code IGrid.getService(...)}. Each step
     * can legitimately return null (host being booted, node not yet attached
     * to a grid, ...); we short-circuit on the first null and let the caller
     * fall back to "not autocraftable" — the rest of the mod keeps working.
     */
    private static ICraftingService craftingServiceOf(ServerPlayer player) {
        if (!(player.containerMenu instanceof MEStorageMenu menu)) return null;
        try {
            // Cast the public ITerminalHost to IActionHost at runtime.
            // ITerminalHost doesn't statically extend IActionHost, but the
            // concrete host classes (block entities, item hosts) all do.
            if (!(menu.getHost() instanceof IActionHost actionHost)) return null;
            IGridNode node = actionHost.getActionableNode();
            if (node == null) return null;
            IGrid grid = node.getGrid();
            if (grid == null) return null;
            return grid.getService(ICraftingService.class);
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("AE2 crafting service lookup failed: {}", t.toString());
            return null;
        }
    }

    /**
     * Resolve the {@link MEStorage} behind whatever terminal the player has open.
     * Verified against AE2 19.2.17: {@code ITerminalHost#getInventory()} returns
     * {@code MEStorage} directly.
     */
    private static MEStorage storageOf(ServerPlayer player) {
        if (!(player.containerMenu instanceof MEStorageMenu menu)) return null;
        try {
            return menu.getHost().getInventory();
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("AE2 storage lookup failed: {}", t.toString());
            return null;
        }
    }
}
