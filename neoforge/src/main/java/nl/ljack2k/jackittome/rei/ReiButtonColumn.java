package nl.ljack2k.jackittome.rei;

import nl.ljack2k.jackittome.client.PullTarget;

import me.shedaniel.math.Rectangle;

/**
 * The strip of a REI display reserved for the JackItToMe pull button.
 * <p>
 * REI, like EMI, gives extensions no say in how big a display is: an extension
 * wraps {@code DisplayCategoryView} (setupDisplay / getDisplayRenderer), while
 * {@code getDisplayWidth} lives on {@code DisplayCategory}, owned by whichever
 * mod registered the category. Drawing inside the display therefore lands on top
 * of recipe content whenever the layout is tight.
 * <p>
 * So the display is widened by {@link #WIDTH} in {@code rei/mixin}, and
 * {@link #contentBounds} hands the category the original, unwidened rectangle.
 * The category lays out exactly as it always did and the freed strip on the left
 * belongs to us — the same arrangement as the EMI button's column.
 */
public final class ReiButtonColumn {
    private ReiButtonColumn() {}

    /** Button (12) plus a 2px gap before the recipe content. */
    public static final int WIDTH = 14;

    /** Whether displays should be widened at all — matches what the extension draws. */
    public static boolean isReserved() {
        return PullTarget.shouldShowButton();
    }

    /** The area the recipe itself gets: everything right of the reserved strip. */
    public static Rectangle contentBounds(Rectangle full) {
        if (!isReserved()) return full;
        return new Rectangle(full.x + WIDTH, full.y, full.width - WIDTH, full.height);
    }
}
