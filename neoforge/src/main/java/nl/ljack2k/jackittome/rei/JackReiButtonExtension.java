package nl.ljack2k.jackittome.rei;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.client.AvailabilityCache;
import nl.ljack2k.jackittome.client.PullHoverPoller;
import nl.ljack2k.jackittome.client.PullTooltipBuilder;

import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.DisplayRenderer;
import me.shedaniel.rei.api.client.gui.widgets.Button;
import me.shedaniel.rei.api.client.gui.widgets.Slot;
import me.shedaniel.rei.api.client.gui.widgets.Tooltip;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.category.extension.CategoryExtensionProvider;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.client.registry.display.DisplayCategoryView;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryStack;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds the JackItToMe pull button to every REI recipe display (the REI
 * equivalent of JEI's universal button factory / EMI's recipe decorator),
 * plus a hover tooltip and the per-slot shortage/craftable overlays that the
 * JEI version shows.
 *
 * @param <T> the display type of the category being extended
 */
public final class JackReiButtonExtension<T extends Display> implements CategoryExtensionProvider<T> {

    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath(JackItToMe.MODID, "textures/gui/jack_button.png");

    @Override
    public DisplayCategoryView<T> provide(T display, DisplayCategory<T> category, DisplayCategoryView<T> lastView) {
        return new DisplayCategoryView<T>() {
            @Override
            public DisplayRenderer getDisplayRenderer(T d) {
                return lastView.getDisplayRenderer(d);
            }

            @Override
            public List<Widget> setupDisplay(T d, Rectangle bounds) {
                List<Widget> widgets = new ArrayList<>(lastView.setupDisplay(d, bounds));

                // Derive ingredients FROM the input slot widgets (in render
                // order) rather than display.getInputEntries(), so the overlay
                // index, the availability request, and the slot positions all
                // line up 1:1 — categories don't always order the two the same.
                List<Rectangle> inputSlotBounds = new ArrayList<>();
                List<Ingredient> ingredients = new ArrayList<>();
                for (Widget w : widgets) {
                    if (w instanceof Slot slot && slot.getNoticeMark() == Slot.INPUT) {
                        inputSlotBounds.add(slot.getBounds());
                        List<ItemStack> stacks = new ArrayList<>();
                        for (EntryStack<?> entry : slot.getEntries()) {
                            ItemStack is = ReiDisplays.toItemStack(entry);
                            if (!is.isEmpty()) stacks.add(is);
                        }
                        ingredients.add(stacks.isEmpty() ? Ingredient.EMPTY
                                : Ingredient.of(stacks.toArray(ItemStack[]::new)));
                    }
                }
                if (ingredients.stream().allMatch(Ingredient::isEmpty)) {
                    return widgets; // no inputs to pull
                }

                int bx = bounds.getMaxX() - 18;
                int by = bounds.getMaxY() - 18;
                Rectangle buttonBounds = new Rectangle(bx, by, 16, 16);

                // The button (REI draws its native frame) fires the pull.
                widgets.add(Widgets.createButton(buttonBounds, Component.empty())
                        .onClick(b -> ReiDisplays.sendPull(ingredients)));

                // One overlay widget, added last so it draws over the slots:
                // renders the icon, polls availability while hovered, paints
                // shortage/craftable overlays, and queues the tooltip.
                PullHoverPoller poller = new PullHoverPoller();
                widgets.add(Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) -> {
                    graphics.blit(ICON, bx + 2, by + 2, 12, 12, 0.0F, 0.0F, 16, 16, 16, 16);

                    boolean hovered = buttonBounds.contains(mouseX, mouseY);
                    poller.tick(hovered, d, ingredients);

                    List<Boolean> shortages = AvailabilityCache.shortagesFor(d);
                    if (shortages != null) {
                        List<Boolean> craftable = AvailabilityCache.craftableFor(d);
                        int n = Math.min(shortages.size(), inputSlotBounds.size());
                        for (int i = 0; i < n; i++) {
                            if (!shortages.get(i)) continue;
                            boolean canCraft = craftable != null && i < craftable.size() && craftable.get(i);
                            Rectangle r = inputSlotBounds.get(i);
                            graphics.fill(r.x, r.y, r.getMaxX(), r.getMaxY(),
                                    canCraft ? PullTooltipBuilder.CRAFTABLE_COLOR : PullTooltipBuilder.SHORTAGE_COLOR);
                        }
                    }

                    if (hovered) {
                        Tooltip.create(PullTooltipBuilder.build(d, ingredients)).queue();
                    }
                }));
                return widgets;
            }
        };
    }
}
