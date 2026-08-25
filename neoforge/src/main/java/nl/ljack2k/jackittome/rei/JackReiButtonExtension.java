package nl.ljack2k.jackittome.rei;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.client.AvailabilityCache;
import nl.ljack2k.jackittome.client.ButtonPlacement;
import nl.ljack2k.jackittome.client.PullButtonTooltip;
import nl.ljack2k.jackittome.client.PullHoverPoller;
import nl.ljack2k.jackittome.client.PullTooltipBuilder;

import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.DisplayRenderer;
import me.shedaniel.rei.api.client.gui.widgets.Button;
import me.shedaniel.rei.api.client.gui.widgets.Slot;
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

    /**
     * The complete button sprite, border included — the same 12x24 sheet EMI uses
     * (normal state at v=0, hover at v=12). REI's own button frame is drawn
     * underneath and covered completely: the sprite is fully opaque and exactly
     * button-sized, which avoids the frame-inside-a-frame look that a borderless
     * glyph inset into REI's button produced.
     */
    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath(JackItToMe.MODID, "textures/gui/jack_button.png");

    /** Sprite and button size in GUI units — blitted 1:1, never scaled. */
    private static final int BUTTON_SIZE = 12;

    /** Full sheet height: two stacked {@link #BUTTON_SIZE} state cells. */
    private static final int SHEET_HEIGHT = BUTTON_SIZE * 2;

    @Override
    public DisplayCategoryView<T> provide(T display, DisplayCategory<T> category, DisplayCategoryView<T> lastView) {
        return new DisplayCategoryView<T>() {
            @Override
            public DisplayRenderer getDisplayRenderer(T d) {
                return lastView.getDisplayRenderer(d);
            }

            @Override
            public List<Widget> setupDisplay(T d, Rectangle fullBounds) {
                // The display was widened by the rei/mixin classes; hand the
                // category the rectangle it would have had, so its layout is
                // untouched and the strip on the left is ours.
                Rectangle bounds = ReiButtonColumn.contentBounds(fullBounds);
                List<Widget> widgets = new ArrayList<>(lastView.setupDisplay(d, bounds));

                // Derive ingredients FROM the input slot widgets (in render
                // order) rather than display.getInputEntries(), so the overlay
                // index, the availability request, and the slot positions all
                // line up 1:1 — categories don't always order the two the same.
                List<Rectangle> inputSlotBounds = new ArrayList<>();
                List<Ingredient> ingredients = new ArrayList<>();
                int resultsPerCraft = 1;
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
                    } else if (w instanceof Slot slot && slot.getNoticeMark() == Slot.OUTPUT
                            && resultsPerCraft == 1) {
                        // First output slot's count drives STACK mode's
                        // "a stack of the result" target.
                        for (EntryStack<?> entry : slot.getEntries()) {
                            ItemStack is = ReiDisplays.toItemStack(entry);
                            if (!is.isEmpty()) {
                                resultsPerCraft = Math.max(1, is.getCount());
                                break;
                            }
                        }
                    }
                }
                if (ingredients.stream().allMatch(Ingredient::isEmpty)) {
                    return widgets; // no inputs to pull
                }
                // Disabled in the config, or no container behind the recipe view to
                // pull from. REI rebuilds displays on open, so checking here is enough.
                if (!nl.ljack2k.jackittome.client.PullTarget.shouldShowButton()) {
                    return widgets;
                }

                // In the reserved strip, vertically centred — the same placement
                // the EMI button uses, and immune to how tall the recipe is.
                int baseX = fullBounds.x + 1;
                int baseY = fullBounds.y + Math.max(0, (fullBounds.height - BUTTON_SIZE) / 2);

                // REI fixes a widget's bounds when the display is built, but
                // Rectangle is mutable and the button keeps this instance — so
                // moving it each frame lets the placement offset apply live,
                // with clicks following, instead of only after a reopen.
                Rectangle buttonBounds = new Rectangle(baseX, baseY, BUTTON_SIZE, BUTTON_SIZE);

                // The button (REI draws its native frame) fires the pull.
                int results = resultsPerCraft;
                widgets.add(Widgets.createButton(buttonBounds, Component.empty())
                        .onClick(b -> ReiDisplays.sendPull(ingredients, results)));

                // One overlay widget, added last so it draws over the slots:
                // renders the icon, polls availability while hovered, paints
                // shortage/craftable overlays, and queues the tooltip.
                PullHoverPoller poller = new PullHoverPoller();
                widgets.add(Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) -> {
                    // Re-apply the configured offset before anything reads the
                    // bounds, so drawing, hovering and clicking all agree.
                    buttonBounds.x = baseX + ButtonPlacement.dx();
                    buttonBounds.y = baseY + ButtonPlacement.dy();
                    int bx = buttonBounds.x;
                    int by = buttonBounds.y;

                    boolean hovered = buttonBounds.contains(mouseX, mouseY);
                    // Hover swaps to the second cell of the sheet, as in EMI.
                    graphics.blit(ICON, bx, by, BUTTON_SIZE, BUTTON_SIZE,
                            0.0F, hovered ? BUTTON_SIZE : 0.0F,
                            BUTTON_SIZE, BUTTON_SIZE, BUTTON_SIZE, SHEET_HEIGHT);

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
                        // Drawn to the LEFT by PullButtonTooltip; REI would open it
                        // at the cursor, over the recipe and its shortage highlights.
                        PullButtonTooltip.show(bx, by,
                                PullTooltipBuilder.build(d, ingredients, results));
                    }
                }));
                return widgets;
            }
        };
    }
}
