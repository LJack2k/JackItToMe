package nl.ljack2k.jackittome.source;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The fallback source: walks the slots of whatever {@link AbstractContainerMenu}
 * the player has open and treats every non-player-inventory slot as eligible.
 * <p>
 * Slot ownership detection uses {@link Slot#container} — if the backing
 * container is the player's own {@link Inventory}, we skip it (otherwise we'd
 * happily pull items out of one of the player's inventory slots into another).
 */
public class ContainerItemSource implements ItemSource {

    @Override
    public boolean matches(ServerPlayer player) {
        return player.containerMenu != null;
    }

    @Override
    public long count(ItemStack template, ServerPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        Inventory playerInv = player.getInventory();
        long total = 0;
        for (Slot slot : menu.slots) {
            if (slot.container == playerInv) continue;
            ItemStack inSlot = slot.getItem();
            if (sameItem(template, inSlot)) {
                total += inSlot.getCount();
            }
        }
        return total;
    }

    @Override
    public ItemStack extract(ItemStack template, int amount, ServerPlayer player) {
        if (amount <= 0) return ItemStack.EMPTY;
        AbstractContainerMenu menu = player.containerMenu;
        Inventory playerInv = player.getInventory();

        ItemStack out = ItemStack.EMPTY;
        int needed = amount;

        for (Slot slot : menu.slots) {
            if (needed == 0) break;
            if (slot.container == playerInv) continue;
            if (!slot.mayPickup(player)) continue;

            ItemStack inSlot = slot.getItem();
            if (!sameItem(template, inSlot)) continue;

            int take = Math.min(needed, inSlot.getCount());
            ItemStack taken = slot.remove(take);
            if (taken.isEmpty()) continue;

            if (out.isEmpty()) {
                out = taken;
            } else {
                out.grow(taken.getCount());
            }
            needed -= taken.getCount();
            slot.setChanged();
        }

        return out;
    }

    @Override
    public void insertOrDrop(ItemStack stack, ServerPlayer player) {
        if (stack.isEmpty()) return;
        AbstractContainerMenu menu = player.containerMenu;
        Inventory playerInv = player.getInventory();

        // Merge into existing matching slots first.
        for (Slot slot : menu.slots) {
            if (stack.isEmpty()) return;
            if (slot.container == playerInv) continue;
            if (!slot.mayPlace(stack)) continue;
            ItemStack inSlot = slot.getItem();
            if (inSlot.isEmpty()) continue;
            if (!sameItem(stack, inSlot)) continue;
            int space = Math.min(slot.getMaxStackSize(inSlot), inSlot.getMaxStackSize()) - inSlot.getCount();
            if (space <= 0) continue;
            int add = Math.min(space, stack.getCount());
            inSlot.grow(add);
            stack.shrink(add);
            slot.setChanged();
        }
        // Then empty slots.
        for (Slot slot : menu.slots) {
            if (stack.isEmpty()) return;
            if (slot.container == playerInv) continue;
            if (!slot.mayPlace(stack)) continue;
            if (!slot.getItem().isEmpty()) continue;
            slot.set(stack.copy());
            stack.setCount(0);
        }
        if (!stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    private static boolean sameItem(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty() && ItemStack.isSameItemSameComponents(a, b);
    }
}
