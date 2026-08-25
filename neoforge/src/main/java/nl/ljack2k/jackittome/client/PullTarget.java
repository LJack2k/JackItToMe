package nl.ljack2k.jackittome.client;

import net.minecraft.client.Minecraft;

/**
 * Whether there is anything for the recipe button to pull FROM.
 * <p>
 * Every pull draws from the container menu open behind the recipe view, and the
 * server-side sources ignore the player's own slots — {@code ContainerItemSource}
 * skips any slot whose {@code container} is the player inventory. So with nothing
 * but the player's own inventory open there is, by definition, nothing to take,
 * and the button would do nothing if clicked.
 * <p>
 * Tested on the open menu rather than the screen: the client's
 * {@code player.containerMenu} is exactly what the server reads, it is the same
 * check for every recipe viewer, and it recognises storage terminals (AE2 ME
 * terminals, RS grids) which expose their contents through a custom view rather
 * than through menu slots.
 */
public final class PullTarget {
    private PullTarget() {}

    /** True when a container other than the player's own inventory is open. */
    public static boolean hasSource() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return mc.player.containerMenu != null
                && mc.player.containerMenu != mc.player.inventoryMenu;
    }

    /** Whether the recipe button should be drawn at all, for any viewer. */
    public static boolean shouldShowButton() {
        return JackConfig.SHOW_RECIPE_BUTTON.get() && hasSource();
    }
}
