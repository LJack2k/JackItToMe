package nl.ljack2k.jackittome.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** No-op bridge used when no recipe viewer is installed. */
public final class NullRecipeViewerBridge implements RecipeViewerBridge {
    @Override public ItemStack getHoveredItem(Screen s, double mx, double my) { return ItemStack.EMPTY; }
    @Override public List<ItemStack> getRecipeSlotVariants(Screen s, double mx, double my) { return List.of(); }
    @Override public boolean isRecipeScreen(Screen s) { return false; }
    @Override public void onRecipeScreenClosed() {}
}
