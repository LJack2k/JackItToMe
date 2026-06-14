package nl.ljack2k.jackittome.server;

import nl.ljack2k.jackittome.network.AvailabilityResponsePayload;
import nl.ljack2k.jackittome.network.CheckAvailabilityPayload;
import nl.ljack2k.jackittome.source.ItemSource;
import nl.ljack2k.jackittome.source.ItemSourceRegistry;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side handler for {@link CheckAvailabilityPayload}.
 * <p>
 * Walks the requested ingredients in order, simulating a pull. For each slot
 * we pick the most-abundant acceptable variant from a running "remaining"
 * tally (mirroring what {@link PullHandler} would do for real), subtract one,
 * and mark the slot {@code false} (no shortage). If no remaining variant is
 * available we mark the slot {@code true} (shortage) and don't subtract.
 * <p>
 * The simulation is read-only — we don't touch the source, just sample its
 * counts once and decrement locally.
 */
public final class AvailabilityHandler {
    private AvailabilityHandler() {}

    public static void handle(ServerPlayer player, CheckAvailabilityPayload req) {
        Result r = simulate(player, req.ingredients());
        PacketDistributor.sendToPlayer(player,
                new AvailabilityResponsePayload(req.nonce(), r.shortages, r.craftable));
    }

    /**
     * Shortages and craftable flags, in the same order as the input list.
     * Public so {@link PullHandler} can use the same simulator for its
     * server-authoritative shortage gate and its Shift-click chain selection.
     */
    public static Result simulate(ServerPlayer player, List<Ingredient> ingredients) {
        List<Boolean> shortages = new ArrayList<>(ingredients.size());
        List<Boolean> craftable = new ArrayList<>(ingredients.size());

        ItemSource source = ItemSourceRegistry.findSource(player);
        if (source == null) {
            // No source at all → everything's a shortage, nothing is craftable.
            for (int i = 0; i < ingredients.size(); i++) {
                shortages.add(Boolean.TRUE);
                craftable.add(Boolean.FALSE);
            }
            return new Result(shortages, craftable);
        }

        // Sample what's available once, then track decrements locally.
        // Keyed by Item — components/NBT mismatches are handled by recipe slots
        // listing each variant separately, so Item is sufficient as a key.
        Map<Item, Long> remaining = new HashMap<>();

        for (Ingredient ing : ingredients) {
            if (ing.isEmpty()) {
                // Empty slot in the recipe — not a shortage, not craftable.
                shortages.add(Boolean.FALSE);
                craftable.add(Boolean.FALSE);
                continue;
            }

            // Find the variant with the most remaining (matching the live resolver).
            ItemStack bestStack = ItemStack.EMPTY;
            long bestCount = 0;
            for (ItemStack acceptable : ing.getItems()) {
                if (acceptable.isEmpty()) continue;
                Item item = acceptable.getItem();
                Long cached = remaining.get(item);
                long count = (cached != null) ? cached : source.count(acceptable, player);
                if (cached == null) remaining.put(item, count);
                if (count > bestCount) {
                    bestCount = count;
                    bestStack = acceptable;
                }
            }

            if (bestStack.isEmpty() || bestCount <= 0) {
                shortages.add(Boolean.TRUE);  // shortage
                // Check craftability across all acceptable variants — any one
                // that's craftable is enough to flag the slot green. We stop at
                // the first hit because both AE2 and RS make this a cheap O(1)
                // set/service lookup, so even N variants is fine.
                boolean anyCraftable = false;
                for (ItemStack acceptable : ing.getItems()) {
                    if (acceptable.isEmpty()) continue;
                    if (source.isAutocraftable(acceptable, player)) {
                        anyCraftable = true;
                        break;
                    }
                }
                craftable.add(anyCraftable);
            } else {
                remaining.put(bestStack.getItem(), bestCount - 1);
                shortages.add(Boolean.FALSE); // ok
                craftable.add(Boolean.FALSE);
            }
        }
        return new Result(shortages, craftable);
    }

    /** Bundle of parallel arrays — same length, indexed by ingredient slot. */
    public record Result(List<Boolean> shortages, List<Boolean> craftable) {}
}
