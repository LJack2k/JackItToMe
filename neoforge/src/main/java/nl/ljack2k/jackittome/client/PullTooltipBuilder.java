package nl.ljack2k.jackittome.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the recipe-pull button's tooltip lines from the shared
 * {@link AvailabilityCache}, so JEI, EMI, and REI all show the same text.
 * Mirrors what JEI's {@code JackPullButtonController.getTooltips} produced.
 */
public final class PullTooltipBuilder {
    private PullTooltipBuilder() {}

    /** Translucent red — missing and not autocraftable. */
    public static final int SHORTAGE_COLOR = 0x80FF4040;
    /** Translucent green — missing but autocraftable. */
    public static final int CRAFTABLE_COLOR = 0x8040FF40;

    /**
     * @param recipeKey   the object the availability data is cached under (the
     *                    viewer's recipe/display object)
     * @param ingredients the recipe's input ingredients, in slot order
     */
    public static List<Component> build(Object recipeKey, List<Ingredient> ingredients) {
        int total = (int) ingredients.stream().filter(i -> !i.isEmpty()).count();
        List<Boolean> shortages = AvailabilityCache.shortagesFor(recipeKey);
        List<Boolean> craftable = AvailabilityCache.craftableFor(recipeKey);

        int missingUncraftable = 0;
        int missingCraftable = 0;
        if (shortages != null) {
            int n = Math.min(shortages.size(), ingredients.size());
            for (int i = 0; i < n; i++) {
                if (!shortages.get(i)) continue;
                boolean canCraft = craftable != null && i < craftable.size() && craftable.get(i);
                if (canCraft) missingCraftable++;
                else missingUncraftable++;
            }
        }
        int totalMissing = missingUncraftable + missingCraftable;

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("jackittome.button.recipe_pull.title"));

        if (shortages != null && totalMissing == 0) {
            lines.add(Component.translatable("jackittome.button.recipe_pull.ready", total)
                    .withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("jackittome.button.recipe_pull.total", total)
                    .withStyle(ChatFormatting.GRAY));
        }

        if (missingUncraftable > 0) {
            lines.add(Component.translatable("jackittome.button.recipe_pull.missing", missingUncraftable)
                    .withStyle(ChatFormatting.RED));
        }
        if (missingCraftable > 0) {
            lines.add(Component.translatable("jackittome.button.recipe_pull.craftable", missingCraftable)
                    .withStyle(ChatFormatting.GREEN));
        }

        int inStock = total - totalMissing;
        if (shortages != null && inStock > 0 && totalMissing > 0) {
            String key = (missingCraftable > 0)
                    ? "jackittome.button.recipe_pull.shift_hint"
                    : "jackittome.button.recipe_pull.shift_partial";
            lines.add(Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        return lines;
    }
}
