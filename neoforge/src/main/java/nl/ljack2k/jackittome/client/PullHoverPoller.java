package nl.ljack2k.jackittome.client;

import nl.ljack2k.jackittome.network.CheckAvailabilityPayload;

import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Per-button hover state machine that drives the availability refresh while a
 * recipe-pull button is hovered. One instance per button widget. Mirrors the
 * polling JEI's {@code JackPullButtonController.drawExtras} does.
 * <p>
 * On hover-enter it clears stale data and fires a check; while hovered it
 * re-polls every {@value #REFRESH_MS} ms; on hover-exit it ends the hover so
 * the overlays/tooltip stop showing.
 */
public final class PullHoverPoller {
    private static final long REFRESH_MS = 750;

    private boolean wasHovered = false;
    private long lastQueryTime = 0;

    public void tick(boolean hovered, Object recipeKey, List<Ingredient> ingredients) {
        if (hovered && !wasHovered) {
            AvailabilityCache.clear();
            fire(recipeKey, ingredients);
            lastQueryTime = System.currentTimeMillis();
        } else if (hovered) {
            long now = System.currentTimeMillis();
            if (now - lastQueryTime > REFRESH_MS) {
                fire(recipeKey, ingredients);
                lastQueryTime = now;
            }
        } else if (wasHovered) {
            AvailabilityCache.endHover(recipeKey);
        }
        wasHovered = hovered;
    }

    private static void fire(Object recipeKey, List<Ingredient> ingredients) {
        if (ingredients.isEmpty()) return;
        long nonce = AvailabilityCache.beginHover(recipeKey);
        PacketDistributor.sendToServer(new CheckAvailabilityPayload(nonce, ingredients));
    }
}
