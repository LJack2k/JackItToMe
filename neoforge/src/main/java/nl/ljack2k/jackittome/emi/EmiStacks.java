package nl.ljack2k.jackittome.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

/**
 * Conversion helpers between EMI's stack/ingredient types and vanilla
 * {@link ItemStack} / {@link Ingredient}. Shared by {@link EmiViewerBridge}
 * (P-key hover) and {@link JackEmiRecipeDecorator} (recipe pull button).
 */
final class EmiStacks {
    private EmiStacks() {}

    /**
     * Extract an {@link ItemStack} from an {@link EmiStack}. EMI's NeoForge
     * implementation stores the full {@code ItemStack} as the key; if that
     * fails, fall back to building one from the raw {@link Item}.
     */
    static ItemStack toItemStack(EmiStack emiStack) {
        if (emiStack == null || emiStack.isEmpty()) return ItemStack.EMPTY;
        ItemStack direct = emiStack.getKeyOfType(ItemStack.class);
        if (direct != null && !direct.isEmpty()) return direct;
        Item item = emiStack.getKeyOfType(Item.class);
        if (item != null) {
            return new ItemStack(item, (int) Math.max(1, emiStack.getAmount()));
        }
        return ItemStack.EMPTY;
    }

    /** First non-empty {@link ItemStack} variant of an EMI ingredient, or EMPTY. */
    static ItemStack first(EmiIngredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return ItemStack.EMPTY;
        for (EmiStack emiStack : ingredient.getEmiStacks()) {
            ItemStack is = toItemStack(emiStack);
            if (!is.isEmpty()) return is;
        }
        return ItemStack.EMPTY;
    }

    /** All {@link ItemStack} variants of an EMI ingredient. */
    static List<ItemStack> variants(EmiIngredient ingredient) {
        List<ItemStack> out = new ArrayList<>();
        if (ingredient == null || ingredient.isEmpty()) return out;
        for (EmiStack emiStack : ingredient.getEmiStacks()) {
            ItemStack is = toItemStack(emiStack);
            if (!is.isEmpty()) out.add(is);
        }
        return out;
    }

    /**
     * Build one {@link Ingredient} per recipe input slot, each listing every
     * accepted variant (the cycling-variant case). Empty slots map to
     * {@link Ingredient#EMPTY} so positions stay aligned with the server's
     * shortage list — same contract as the JEI button controller.
     */
    static List<Ingredient> inputIngredients(EmiRecipe recipe) {
        List<Ingredient> out = new ArrayList<>();
        if (recipe == null) return out;
        for (EmiIngredient ing : recipe.getInputs()) {
            List<ItemStack> stacks = variants(ing);
            out.add(stacks.isEmpty() ? Ingredient.EMPTY
                    : Ingredient.of(stacks.toArray(ItemStack[]::new)));
        }
        return out;
    }
}
