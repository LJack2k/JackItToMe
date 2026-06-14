package nl.ljack2k.jackittome.rei;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.network.PullIngredientsPayload;
import nl.ljack2k.jackittome.network.PullMode;

import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Conversion helpers between REI's {@link EntryStack} type and vanilla
 * {@link ItemStack}/{@link Ingredient}, plus the pull dispatch. Shared by
 * {@link ReiViewerBridge}
 * (P-key hover) and {@link JackReiButtonExtension} (recipe pull button).
 */
final class ReiDisplays {
    private ReiDisplays() {}

    /** Unwrap an REI entry to a vanilla ItemStack, or EMPTY for non-item entries. */
    @SuppressWarnings("unchecked")
    static ItemStack toItemStack(EntryStack<?> entry) {
        if (entry == null || entry.isEmpty()) return ItemStack.EMPTY;
        try {
            if (entry.getType() == VanillaEntryTypes.ITEM) {
                ItemStack is = ((EntryStack<ItemStack>) entry).getValue();
                return (is == null || is.isEmpty()) ? ItemStack.EMPTY : is;
            }
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("[JackItToMe] REI entry unwrap failed: {}", t.toString());
        }
        return ItemStack.EMPTY;
    }

    /**
     * Send the pull payload for the given input ingredients (built by the
     * button extension from the recipe's input slots, in slot order).
     */
    static void sendPull(List<Ingredient> ingredients) {
        if (ingredients.stream().allMatch(Ingredient::isEmpty)) {
            JackItToMe.LOGGER.debug("[JackItToMe] REI recipe button clicked but recipe has no input slots.");
            return;
        }
        // Shift = also pull what's in stock; autocraft always fires. Mirrors the
        // JEI/EMI button semantics.
        boolean shift = Screen.hasShiftDown();
        JackItToMe.LOGGER.info("[JackItToMe] REI recipe button: {} ingredients, pull={}, autocraft=true.",
                ingredients.size(), shift);
        PacketDistributor.sendToServer(new PullIngredientsPayload(
                ingredients, PullMode.SINGLE, /*pullAvailable=*/ shift, /*triggerAutocraft=*/ true));
    }
}
