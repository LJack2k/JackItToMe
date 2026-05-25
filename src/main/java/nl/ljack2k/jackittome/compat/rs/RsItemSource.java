package nl.ljack2k.jackittome.compat.rs;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.source.ItemSource;
import nl.ljack2k.jackittome.source.ItemSourceRegistry;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.Storage;
import com.refinedmods.refinedstorage.common.api.grid.Grid;
import com.refinedmods.refinedstorage.common.api.storage.PlayerActor;
import com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

import nl.ljack2k.jackittome.network.OpenRsAutocraftPayload;
import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;

/**
 * Refined Storage 2.0+ item source. Active when the player has any
 * {@link AbstractGridContainerMenu} open — covers the normal Grid, Crafting
 * Grid, Pattern Grid, Wireless Grid, and Portable Grid.
 * <p>
 * Counts and extracts go through the network's item storage exposed by
 * {@code Grid#getItemStorage()}, so RS's filters, access modes, and storage
 * disks are all respected. We never touch the menu's slots directly.
 * <p>
 * Implementation notes:
 * <ul>
 *   <li>Counting uses {@link Action#SIMULATE} extract — RS doesn't expose a
 *       direct "how much of X is available" call, but a simulated extract of
 *       {@link Long#MAX_VALUE} returns exactly that.</li>
 *   <li>The {@code grid} field on {@code AbstractGridContainerMenu} is
 *       package-private with no public getter, so we read it via reflection
 *       once per call. Cheap.</li>
 *   <li>{@link PlayerActor} is the canonical RS actor for player-driven
 *       operations — RS uses it for logging and security checks.</li>
 * </ul>
 */
public final class RsItemSource implements ItemSource {
    private RsItemSource() {}

    /** Called from the main mod constructor only if Refined Storage is loaded. */
    public static void register() {
        try {
            ItemSourceRegistry.registerHighPriority(new RsItemSource());
        } catch (Throwable t) {
            JackItToMe.LOGGER.error("Failed to register Refined Storage item source — RS API mismatch?", t);
        }
    }

    @Override
    public boolean matches(ServerPlayer player) {
        return player.containerMenu instanceof AbstractGridContainerMenu;
    }

    @Override
    public long count(ItemStack template, ServerPlayer player) {
        Storage storage = storageOf(player);
        if (storage == null) return 0;
        ResourceKey resource = resourceOf(template);
        Actor actor = new PlayerActor(player);
        // SIMULATE returns how much could be extracted right now — that's our count.
        return storage.extract(resource, Long.MAX_VALUE, Action.SIMULATE, actor);
    }

    @Override
    public ItemStack extract(ItemStack template, int amount, ServerPlayer player) {
        if (amount <= 0) return ItemStack.EMPTY;
        Storage storage = storageOf(player);
        if (storage == null) return ItemStack.EMPTY;

        ResourceKey resource = resourceOf(template);
        Actor actor = new PlayerActor(player);

        long extracted = storage.extract(resource, amount, Action.EXECUTE, actor);
        if (extracted <= 0) return ItemStack.EMPTY;

        ItemStack out = template.copy();
        out.setCount((int) Math.min(extracted, Integer.MAX_VALUE));
        return out;
    }

    @Override
    public void insertOrDrop(ItemStack stack, ServerPlayer player) {
        if (stack.isEmpty()) return;
        Storage storage = storageOf(player);
        if (storage == null) {
            player.drop(stack, false);
            return;
        }
        ResourceKey resource = resourceOf(stack);
        Actor actor = new PlayerActor(player);
        long inserted = storage.insert(resource, stack.getCount(), Action.EXECUTE, actor);
        if (inserted < stack.getCount()) {
            ItemStack leftover = stack.copyWithCount((int) (stack.getCount() - inserted));
            player.drop(leftover, false);
        }
    }

    /**
     * Does RS's grid currently know how to autocraft {@code template}? Uses
     * {@code Grid#getAutocraftableResources} — a STABLE, cheap set lookup
     * exposed since RS 2.0.0-milestone.3.0. The returned set contains every
     * autocraftable {@code PlatformResourceKey}; our {@link ItemResource}
     * implements that interface, so a plain {@code Set#contains} works.
     */
    @Override
    public boolean isAutocraftable(ItemStack template, ServerPlayer player) {
        if (template.isEmpty()) return false;
        try {
            Grid grid = gridOf(player);
            if (grid == null || !grid.isGridActive()) return false;
            ResourceKey resource = resourceOf(template);
            return grid.getAutocraftableResources().contains(resource);
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("[RS] isAutocraftable failed: {}", t.toString());
            return false;
        }
    }

    /**
     * Open RS's autocrafting preview popup for {@code template}.
     * <p>
     * Unlike AE2 (which uses a server-driven {@code MenuOpener} call) RS's
     * preview screen is a regular {@code Screen}, opened on the client after
     * the client sends a {@code AutocraftingPreviewRequestPacket} and the
     * response packet arrives. The whole flow is normally driven from the
     * client (RS's {@code AutocraftingRequest} helper).
     * <p>
     * Our server-side hook therefore can't directly open the screen — it sends
     * a tiny {@link OpenRsAutocraftPayload} to the client, which then invokes
     * RS's own {@code AutocraftingRequest} machinery. That keeps the whole RS
     * UI flow inside RS, which means cancellation, preview tree, etc. all
     * Just Work.
     */
    @Override
    public boolean openAutoCraftPopup(ItemStack template, int amount, ServerPlayer player) {
        if (template.isEmpty()) return false;
        if (!(player.containerMenu instanceof AbstractGridContainerMenu)) return false;
        try {
            int initial = Math.max(1, amount);
            PacketDistributor.sendToPlayer(player, new OpenRsAutocraftPayload(template, initial));
            return true;
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("[RS] openAutoCraftPopup failed: {}", t.toString());
            return false;
        }
    }

    /** Convenience wrapper that hands us the Grid for the player's open menu. */
    private static Grid gridOf(ServerPlayer player) {
        if (!(player.containerMenu instanceof AbstractGridContainerMenu menu)) return null;
        return gridOf(menu);
    }

    /** Build the RS ResourceKey for this ItemStack — Item + data components. */
    private static ResourceKey resourceOf(ItemStack stack) {
        return new ItemResource(stack.getItem(), stack.getComponentsPatch());
    }

    /** Reach into the menu's Grid and ask it for its item storage. */
    private static Storage storageOf(ServerPlayer player) {
        if (!(player.containerMenu instanceof AbstractGridContainerMenu menu)) return null;
        try {
            Grid grid = gridOf(menu);
            if (grid == null) {
                JackItToMe.LOGGER.debug("[RS] no grid field on {}", menu.getClass().getSimpleName());
                return null;
            }
            if (!grid.isGridActive()) {
                JackItToMe.LOGGER.debug("[RS] grid is not active (network offline?)");
                return null;
            }
            return grid.getItemStorage();
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("[RS] storage lookup failed: {}", t.toString());
            return null;
        }
    }

    /**
     * Read the {@code grid} field off the menu via reflection — it's package-private
     * on {@code AbstractGridContainerMenu} with no public getter. We walk the class
     * hierarchy because grid menus subclass each other.
     */
    private static Grid gridOf(AbstractGridContainerMenu menu) {
        Class<?> c = menu.getClass();
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField("grid");
                f.setAccessible(true);
                Object value = f.get(menu);
                if (value instanceof Grid g) return g;
            } catch (NoSuchFieldException ignored) {
                // try parent class
            } catch (Throwable t) {
                JackItToMe.LOGGER.debug("[RS] grid field read failed on {}: {}",
                        c.getSimpleName(), t.toString());
                return null;
            }
            c = c.getSuperclass();
        }
        return null;
    }
}
