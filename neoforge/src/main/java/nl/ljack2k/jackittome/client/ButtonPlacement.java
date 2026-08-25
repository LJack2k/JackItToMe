package nl.ljack2k.jackittome.client;

import nl.ljack2k.jackittome.JackItToMe;

/**
 * Where the recipe button sits, as a nudge away from its default corner
 * position. Recipe layouts differ wildly between mods and packs, so the corner
 * that suits a vanilla crafting grid can crowd a machine recipe — this lets the
 * player move it once and have it stick.
 * <p>
 * Adjustable two ways, both writing to the same client-config values:
 * <ul>
 *   <li>In-game: hold Ctrl and press the arrow keys with a recipe screen open
 *       (Ctrl+Home resets). Handled in {@code ClientEvents.onScreenKey}.</li>
 *   <li>By hand: {@code buttonOffsetX} / {@code buttonOffsetY} in
 *       {@code config/jackittome-client.toml}, or the mod's config screen.</li>
 * </ul>
 * Values are cached in plain fields because the button reads them every frame;
 * {@link #refresh()} re-syncs the cache whenever the config loads or reloads.
 */
public final class ButtonPlacement {
    private ButtonPlacement() {}

    private static int dx = 0;
    private static int dy = 0;

    /** Horizontal nudge in GUI pixels; positive = right. */
    public static int dx() {
        return dx;
    }

    /** Vertical nudge in GUI pixels; positive = down. */
    public static int dy() {
        return dy;
    }

    /**
     * Re-reads the cached offsets from the config. Called on config load/reload,
     * which also covers the player editing the toml or using the config screen.
     */
    public static void refresh() {
        try {
            dx = JackConfig.BUTTON_OFFSET_X.get();
            dy = JackConfig.BUTTON_OFFSET_Y.get();
        } catch (IllegalStateException e) {
            // Config not loaded yet — keep the defaults until it is.
        }
    }

    /** Applies a nudge and persists it, so the placement survives a restart. */
    public static void nudge(int ddx, int ddy) {
        set(dx + ddx, dy + ddy);
    }

    /** Restores the built-in corner position. */
    public static void reset() {
        set(0, 0);
    }

    /** Turns placement mode on or off and persists it (the overlay's Done button). */
    public static void setPlacementKeys(boolean enabled) {
        JackConfig.PLACEMENT_KEYS.set(enabled);
        JackConfig.PLACEMENT_KEYS.save();
    }

    private static void set(int newX, int newY) {
        dx = newX;
        dy = newY;
        // save() writes the toml; the resulting reload event calls refresh(),
        // which is a no-op since the cache already holds these values.
        JackConfig.BUTTON_OFFSET_X.set(newX);
        JackConfig.BUTTON_OFFSET_Y.set(newY);
        JackConfig.BUTTON_OFFSET_X.save();
        JackItToMe.LOGGER.debug("[JackItToMe] Recipe button offset: dx={}, dy={}", newX, newY);
    }
}
