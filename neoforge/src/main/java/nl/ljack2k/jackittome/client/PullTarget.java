package nl.ljack2k.jackittome.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * Whether there is anything for the recipe button to pull FROM.
 * <p>
 * Every pull draws from the container menu open behind the recipe view, and the
 * server-side sources ignore the player's own slots — {@code ContainerItemSource}
 * skips any slot whose {@code container} is the player inventory. So with nothing
 * but the player's inventory open there is, by definition, nothing to pull, and
 * the button would be dead weight.
 * <p>
 * The test is deliberately conservative: only the player's own inventory screens
 * are treated as having no source. Checking "does this menu expose a non-player
 * slot?" would be closer to the server rule, but storage terminals (AE2 ME
 * terminals, RS grids) present their contents through a custom view rather than
 * menu slots, so that test would wrongly hide the button exactly where it is most
 * useful. Erring this way can only leave the button visible somewhere it does
 * nothing — never hide it somewhere it works.
 */
public final class PullTarget {
    private PullTarget() {}

    /** True when {@code hostScreen} is a container we could pull items out of. */
    public static boolean isPullable(AbstractContainerScreen<?> hostScreen) {
        if (hostScreen == null) return false;                        // recipe view opened with no container
        if (hostScreen instanceof InventoryScreen) return false;     // player inventory only
        if (hostScreen instanceof CreativeModeInventoryScreen) return false;
        return true;
    }
}
