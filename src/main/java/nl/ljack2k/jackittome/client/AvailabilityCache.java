package nl.ljack2k.jackittome.client;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tiny shared state between the recipe-pull button controller and the
 * shortage decorator.
 * <p>
 * <b>Why we key on the recipe object</b>: JEI creates a fresh
 * {@code IRecipeSlotsView} instance on every call — using that as a cache key
 * means the decorator's lookup never matches the controller's stored key.
 * The underlying <em>recipe</em> object (a {@code RecipeHolder<CraftingRecipe>}
 * or equivalent) is stable across renders, so we use that instead.
 */
public final class AvailabilityCache {
    private AvailabilityCache() {}

    private static final AtomicLong NEXT_NONCE = new AtomicLong(1);

    /** The recipe object of the currently-hovered button. Null when nothing is hovered. */
    private static Object hoveredRecipe = null;

    /** Nonce of the most recent outgoing request; matched against responses. */
    private static long currentNonce = 0;

    /** Latest shortage data, or null if the response hasn't arrived (or the cache is stale). */
    private static List<Boolean> latestShortages = null;

    /** Latest craftable data; same length as {@link #latestShortages} when present. */
    private static List<Boolean> latestCraftable = null;

    /**
     * Mark this recipe as the currently-hovered one and allocate a fresh nonce.
     * Returns the nonce so the caller can include it in the outgoing request.
     * <p>
     * Crucially, {@code latestShortages} is only cleared when the hovered
     * recipe <em>changes</em>, not when the same recipe is just being
     * re-polled. Clearing on every refresh would cause a visible flicker
     * during the ~50ms server round-trip on each 750ms refresh: old red
     * overlay disappears, brief gap, new red overlay arrives. By keeping
     * the previous frame's shortages until the new response replaces them,
     * the redraw is invisible (worst case: ~750ms of slightly-stale data
     * if the storage actually changed mid-refresh).
     */
    public static synchronized long beginHover(Object recipe) {
        if (recipe != hoveredRecipe) {
            // Moving to a different recipe — old shortages are wrong, clear them.
            latestShortages = null;
            latestCraftable = null;
        }
        hoveredRecipe = recipe;
        currentNonce = NEXT_NONCE.getAndIncrement();
        return currentNonce;
    }

    /** Called when the button is no longer hovered. */
    public static synchronized void endHover(Object recipe) {
        if (hoveredRecipe == recipe) {
            hoveredRecipe = null;
            latestShortages = null;
            latestCraftable = null;
        }
    }

    /**
     * Forcibly clear everything regardless of which recipe is currently tracked.
     * Called when the JEI recipe screen closes — the button controller's
     * {@code drawExtras} stops being invoked at that moment, so the normal
     * mouse-leave path through {@link #endHover} never fires. Without this,
     * reopening a recipe view (potentially against a different open container
     * with different stock) would render the previous session's overlays
     * until the user re-hovered the J button.
     */
    public static synchronized void clear() {
        hoveredRecipe = null;
        latestShortages = null;
        latestCraftable = null;
    }

    /**
     * Apply a server response. Silently ignored if the nonce no longer matches
     * the current hover — i.e. the user moved to a different recipe before
     * the response landed.
     */
    public static synchronized void receive(long nonce, List<Boolean> shortages, List<Boolean> craftable) {
        if (nonce == currentNonce && hoveredRecipe != null) {
            latestShortages = shortages;
            latestCraftable = craftable;
        }
    }

    /**
     * Get the shortage list for the given recipe, or null if this recipe isn't
     * currently hovered or the response hasn't arrived yet.
     */
    public static synchronized List<Boolean> shortagesFor(Object recipe) {
        if (recipe != null && recipe == hoveredRecipe) return latestShortages;
        return null;
    }

    /**
     * Get the craftable list for the given recipe — same indexing as the
     * shortage list. {@code craftable[i]} is only meaningful when
     * {@code shortages[i]} is true. Null if this recipe isn't currently
     * hovered or the response hasn't arrived yet.
     */
    public static synchronized List<Boolean> craftableFor(Object recipe) {
        if (recipe != null && recipe == hoveredRecipe) return latestCraftable;
        return null;
    }
}
