package nl.ljack2k.jackittome.compat.id;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.source.ItemSource;
import nl.ljack2k.jackittome.source.ItemSourceRegistry;

// --- Integrated Dynamics / Terminals / CommonCapabilities API ---
// Package layout verified against the versions shipped in the pack this was
// built for: Integrated Terminals 1.7.0, Integrated Dynamics 1.33.3,
// CommonCapabilities 2.11.5 (all MC 1.21.1 / NeoForge). The storage layer is
// CommonCapabilities' generic ingredient system (T = ItemStack, M = Integer
// match-condition bitmask), reached through the ID network.
import org.cyclops.commoncapabilities.api.capability.itemhandler.ItemMatch;
import org.cyclops.commoncapabilities.api.ingredient.IngredientComponent;
import org.cyclops.commoncapabilities.api.ingredient.storage.IIngredientComponentStorage;
import org.cyclops.integrateddynamics.api.network.INetwork;
import org.cyclops.integrateddynamics.api.network.IPositionedAddonsNetwork;
import org.cyclops.integrateddynamics.core.helper.NetworkHelpers;
import org.cyclops.integratedterminals.api.terminalstorage.ITerminalStorageTabServer;
import org.cyclops.integratedterminals.api.terminalstorage.crafting.ITerminalCraftingOption;
import org.cyclops.integratedterminals.api.terminalstorage.crafting.ITerminalStorageTabIngredientCraftingHandler;
import org.cyclops.integratedterminals.core.client.gui.CraftingOptionGuiData;
import org.cyclops.integratedterminals.core.terminalstorage.TerminalStorageTabIngredientComponentServer;
import org.cyclops.integratedterminals.core.terminalstorage.crafting.HandlerWrappedTerminalCraftingOption;
import org.cyclops.integratedterminals.core.terminalstorage.crafting.TerminalStorageTabIngredientCraftingHandlers;
import org.cyclops.integratedterminals.inventory.container.ContainerTerminalStorageBase;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;

/**
 * Integrated Dynamics source. Active when the player has any Integrated
 * Terminals storage terminal open — both the cabled Terminal Storage part and
 * the portable/handheld terminal, since both container menus extend
 * {@link ContainerTerminalStorageBase}.
 * <p>
 * Counts and extracts go through the network's item ingredient storage
 * ({@code IIngredientComponentStorage<ItemStack, Integer>}) rather than the
 * menu's slots, so the whole network is reachable — not just the page of
 * virtual slots currently scrolled into view. The base menu exposes its
 * {@code INetwork} through the public {@link ContainerTerminalStorageBase#getNetwork()},
 * so unlike the Refined Storage source this needs no reflection.
 * <p>
 * <b>Autocrafting</b> is supported when the Integrated Crafting addon is
 * present: {@link ItemSource#isAutocraftable} and
 * {@link ItemSource#openAutoCraftPopup} go through the terminal's own crafting
 * options, so the player gets the terminal's native amount-selection popup.
 * Without the addon both stay false and this is a pure pull source - every
 * Integrated Crafting reference lives in {@link IdCraftingSupport}, which is
 * never loaded on a storage-only network.
 * <p>
 * Implementation notes:
 * <ul>
 *   <li>Match condition is {@code ItemMatch.ITEM | ItemMatch.DATA} — same item
 *       plus same data components, ignoring stack size. This mirrors the
 *       mod's own {@code isSameItemSameComponents} contract (and is exactly
 *       what Integrated Dynamics uses internally for NBT-sensitive matching).</li>
 *   <li>Counting iterates the channel's matching entries and sums them. The
 *       item network clamps every extract to a rate limit, so the simulated
 *       extract the other sources use would report that limit rather than the
 *       stored amount. Extraction is unaffected: the pull loop already asks in
 *       chunks of at most one stack.</li>
 *   <li>Channel {@link IPositionedAddonsNetwork#WILDCARD_CHANNEL} (-1) spans
 *       every channel on the network, so we pull from the whole network the
 *       way AE2/RS do, regardless of which channel tab the player is viewing.</li>
 * </ul>
 */
public final class IntegratedDynamicsItemSource implements ItemSource {

    /** Same item + same data components, ignore count — matches the mod's pull contract. */
    private static final int MATCH = ItemMatch.ITEM | ItemMatch.DATA;

    /** Aggregate across all channels, like a whole-network view. */
    private static final int CHANNEL = IPositionedAddonsNetwork.WILDCARD_CHANNEL;

    /**
     * Whether the Integrated Crafting addon is present. Computed once at class
     * load (which happens from {@link #register()}, well after mod construction,
     * so {@link ModList} is ready). Gates every call into {@link IdCraftingSupport}
     * so its {@code org.cyclops.integratedcrafting.*} references stay dormant on
     * networks that only have storage.
     */
    private static final boolean CRAFTING_AVAILABLE = ModList.get().isLoaded("integratedcrafting");

    private IntegratedDynamicsItemSource() {}

    /** Called from the main mod constructor only if Integrated Terminals is loaded. */
    public static void register() {
        try {
            ItemSourceRegistry.registerHighPriority(new IntegratedDynamicsItemSource());
        } catch (Throwable t) {
            JackItToMe.LOGGER.error("Failed to register Integrated Dynamics item source — ID API mismatch?", t);
        }
    }

    @Override
    public boolean matches(ServerPlayer player) {
        return player.containerMenu instanceof ContainerTerminalStorageBase;
    }

    @Override
    public long count(ItemStack template, ServerPlayer player) {
        if (template.isEmpty()) return 0;
        IIngredientComponentStorage<ItemStack, Integer> storage = storageOf(player);
        if (storage == null) return 0;

        // Sum the channel's matching entries. A simulated extract cannot answer
        // this: the item network clamps every extract to its rate limit, so it
        // reports how much can move in one operation, not how much is stored.
        long total = 0;
        Iterator<ItemStack> matching = storage.iterator(template.copyWithCount(1), MATCH);
        while (matching.hasNext()) {
            ItemStack found = matching.next();
            if (found != null && !found.isEmpty()) total += found.getCount();
        }
        return total;
    }

    @Override
    public ItemStack extract(ItemStack template, int amount, ServerPlayer player) {
        if (amount <= 0 || template.isEmpty()) return ItemStack.EMPTY;
        IIngredientComponentStorage<ItemStack, Integer> storage = storageOf(player);
        if (storage == null) return ItemStack.EMPTY;
        // The prototype's quantity is the max to extract; the match flags decide
        // what "the same item" means (item + components, not count).
        ItemStack request = template.copyWithCount(amount);
        ItemStack extracted = storage.extract(request, MATCH, false);
        return extracted == null ? ItemStack.EMPTY : extracted;
    }

    @Override
    public void insertOrDrop(ItemStack stack, ServerPlayer player) {
        if (stack.isEmpty()) return;
        IIngredientComponentStorage<ItemStack, Integer> storage = storageOf(player);
        if (storage == null) {
            player.drop(stack, false);
            return;
        }
        // insert returns the remainder that didn't fit; drop whatever's left.
        ItemStack remainder = storage.insert(stack, false);
        if (remainder != null && !remainder.isEmpty()) {
            player.drop(remainder, false);
        }
    }

    /**
     * Does the network know how to craft {@code template}? Only answered when the
     * Integrated Crafting addon is present — otherwise there's no crafting network
     * and this stays false (pure storage-only behaviour). The actual lookup lives
     * in {@link IdCraftingSupport} so Integrated Crafting classes never load on a
     * storage-only network.
     * <p>
     * This is the cheap recipe-index check (see {@link IdCraftingSupport#isCraftable});
     * it powers the J-button hover's green "craftable" highlight for ID networks.
     */
    @Override
    public boolean isAutocraftable(ItemStack template, ServerPlayer player) {
        if (template.isEmpty() || !CRAFTING_AVAILABLE) return false;
        INetwork network = networkOf(player);
        if (network == null) return false;
        return IdCraftingSupport.isCraftable(network, template, MATCH);
    }

    /**
     * Open the Storage Terminal's native "how many to craft?" popup for
     * {@code template}, pre-filled with {@code amount}.
     * <p>
     * Unlike AE2 (a standalone {@code CraftAmountMenu}) or RS (a client-driven
     * screen), Integrated Terminals' amount popup is a sub-menu of the terminal —
     * but it's opened server-side via {@code player.openMenu(...)}, exactly the
     * way a real "craft" click does. We reproduce that click: resolve the
     * {@code ITerminalCraftingOption} that outputs {@code template} from the
     * registered crafting handlers, wrap it, build the same
     * {@link CraftingOptionGuiData} the client would send, and hand it to the
     * location's {@code openContainerCraftingOptionAmount}. The matching client
     * screen ({@code ContainerScreenTerminalStorageCraftingOptionAmount}) opens
     * automatically — no custom client packet needed.
     * <p>
     * Returns false (and the mod falls back to a plain feedback animation) if the
     * network has no crafting option producing {@code template} — e.g. Integrated
     * Crafting absent, or nothing on the network can make it.
     */
    @Override
    public boolean openAutoCraftPopup(ItemStack template, int amount, ServerPlayer player) {
        if (template.isEmpty() || !CRAFTING_AVAILABLE) return false;
        if (!(player.containerMenu instanceof ContainerTerminalStorageBase<?> menu)) return false;
        try {
            return openCraftingPopup(menu, template, Math.max(1, amount), player);
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("[ID] openAutoCraftPopup failed: {}", t.toString());
            return false;
        }
    }

    /**
     * Generic worker split out so the terminal's location type {@code L}
     * (PartPos for the cabled terminal, ItemLocation for the portable one) is
     * captured consistently between {@code getLocation()}, {@code getLocationInstance()},
     * and the {@link CraftingOptionGuiData} we build.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <L> boolean openCraftingPopup(ContainerTerminalStorageBase<L> menu,
                                                 ItemStack template, int amount, ServerPlayer player) {
        IngredientComponent<ItemStack, Integer> component = IngredientComponent.ITEMSTACK;
        String tabName = component.getName().toString();

        ITerminalStorageTabServer tabServerRaw = menu.getTabServer(tabName);
        if (!(tabServerRaw instanceof TerminalStorageTabIngredientComponentServer)) {
            JackItToMe.LOGGER.debug("[ID] terminal has no item crafting tab");
            return false;
        }
        TerminalStorageTabIngredientComponentServer<ItemStack, Integer> tab =
                (TerminalStorageTabIngredientComponentServer<ItemStack, Integer>) tabServerRaw;

        // Same channel the terminal itself uses for a real craft click.
        int channel = menu.getSelectedChannel();

        // Find the first crafting handler that reports an option producing this
        // item. Only the Integrated Crafting handler will — others (fluid/energy,
        // or none) return empty or reject the item tab, so we skip them.
        for (ITerminalStorageTabIngredientCraftingHandler handler
                : TerminalStorageTabIngredientCraftingHandlers.REGISTRY.getHandlers()) {
            Collection<? extends ITerminalCraftingOption<?>> options;
            try {
                options = handler.getCraftingOptionsWithOutput(tab, channel, template, MATCH);
            } catch (Throwable t) {
                continue; // handler bound to a different ingredient component
            }
            if (options == null || options.isEmpty()) continue;

            ITerminalCraftingOption<ItemStack> option =
                    (ITerminalCraftingOption<ItemStack>) options.iterator().next();
            HandlerWrappedTerminalCraftingOption<ItemStack> wrapped =
                    new HandlerWrappedTerminalCraftingOption<>(handler, option);

            // Integrated Crafting counts recipe executions, not output items:
            // the amount handed to the popup is multiplied by the recipe, so
            // asking for 2 paper from a recipe that yields 3 would craft 6.
            // AE2 and RS take the item count and divide it themselves.
            int batches = craftsFor(option, template, amount);

            CraftingOptionGuiData<ItemStack, Integer, L> data = new CraftingOptionGuiData<>(
                    component, tabName, channel, wrapped, batches, null,
                    menu.getLocation(), menu.getLocationInstance());

            // Opens the amount sub-menu via player.openMenu — replaces the
            // terminal menu, exactly as the real click flow does.
            menu.getLocation().openContainerCraftingOptionAmount(data, player.level(), player);

            if (player.containerMenu != menu) {
                JackItToMe.LOGGER.debug("[ID] opened crafting amount popup for {} ({} needed, {} craft(s))",
                        template.getHoverName().getString(), amount, batches);
                return true;
            }
            JackItToMe.LOGGER.debug("[ID] openContainerCraftingOptionAmount ran but menu didn't change");
            return false;
        }
        JackItToMe.LOGGER.debug("[ID] no crafting option produces {}", template.getHoverName().getString());
        return false;
    }

    /**
     * How many executions of {@code option}'s recipe are needed to yield
     * {@code needed} of {@code template}. A recipe with no readable output for
     * this item is treated as producing one per craft, which is the neutral
     * assumption and never overshoots.
     */
    private static int craftsFor(ITerminalCraftingOption<ItemStack> option, ItemStack template, int needed) {
        int perCraft = 0;
        try {
            for (ItemStack output : option.getOutputs(IngredientComponent.ITEMSTACK)) {
                if (output != null && !output.isEmpty()
                        && ItemStack.isSameItemSameComponents(output, template)) {
                    perCraft += output.getCount();
                }
            }
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("[ID] could not read recipe outputs: {}", t.toString());
        }
        if (perCraft < 1) perCraft = 1;
        return Math.max(1, (needed + perCraft - 1) / perCraft);
    }

    /** The Integrated Dynamics network behind the open terminal, or null. */
    private static INetwork networkOf(ServerPlayer player) {
        if (!(player.containerMenu instanceof ContainerTerminalStorageBase<?> menu)) return null;
        try {
            return menu.getNetwork().orElse(null);
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("[ID] network lookup failed: {}", t.toString());
            return null;
        }
    }

    /**
     * Resolve the network's item ingredient storage behind whatever terminal the
     * player has open: menu → {@code INetwork} → item ingredient network →
     * wildcard channel. Any step can be absent (network booting, disconnected,
     * addon not present); we short-circuit to null and the caller treats the
     * source as empty.
     */
    private static IIngredientComponentStorage<ItemStack, Integer> storageOf(ServerPlayer player) {
        INetwork network = networkOf(player);
        if (network == null) return null;
        try {
            return NetworkHelpers.getIngredientNetwork(Optional.of(network), IngredientComponent.ITEMSTACK)
                    .map(net -> net.getChannel(CHANNEL))
                    .orElse(null);
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("[ID] storage lookup failed: {}", t.toString());
            return null;
        }
    }
}
