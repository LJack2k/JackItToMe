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

    /**
     * Mark this recipe as the currently-hovered one and allocate a fresh nonce.
     * Returns the nonce so the caller can include it in the outgoing request.
     */
    public static synchronized long beginHover(Object recipe) {
        hoveredRecipe = recipe;
        currentNonce = NEXT_NONCE.getAndIncrement();
        latestShortages = null;
        return currentNonce;
    }

    /** Called when the button is no longer hovered. */
    public static synchronized void endHover(Object recipe) {
        if (hoveredRecipe == recipe) {
            hoveredRecipe = null;
            latestShortages = null;
        }
    }

    /**
     * Apply a server response. Silently ignored if the nonce no longer matches
     * the current hover — i.e. the user moved to a different recipe before
     * the response landed.
     */
    public static synchronized void receive(long nonce, List<Boolean> shortages) {
        if (nonce == currentNonce && hoveredRecipe != null) {
            latestShortages = shortages;
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
}
