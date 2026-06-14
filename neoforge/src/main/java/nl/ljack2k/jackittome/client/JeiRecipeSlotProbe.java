package nl.ljack2k.jackittome.client;

import nl.ljack2k.jackittome.JackItToMe;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reflectively walk JEI's recipe screen object graph to find the
 * {@code IRecipeSlotView} under the cursor and return every ItemStack it
 * accepts — including all variants that JEI's recipe slot would normally
 * cycle through.
 * <p>
 * JEI's public API only exposes the currently-displayed ingredient via
 * {@code IRecipesGui#getIngredientUnderMouse}. There's no public method
 * for "give me all variants of the slot under the mouse." So we reach into
 * the underlying screen objects, looking for anything that responds to the
 * public-API method {@code getRecipeSlotUnderMouse(double, double)} on
 * {@code IRecipeLayoutDrawable}. Once we find one and get a slot, we call
 * the public {@code getAllIngredients(IIngredientType)} to extract the full
 * variant list.
 * <p>
 * Reflection is bounded: we cap recursion depth at 4 and refuse to descend
 * into {@code java.*} or {@code net.minecraft.*} classes.
 */
public final class JeiRecipeSlotProbe {
    private JeiRecipeSlotProbe() {}

    /** Maximum reflective recursion depth into the JEI object graph. */
    private static final int MAX_DEPTH = 4;

    /**
     * @return all acceptable ItemStacks for the recipe slot at the given
     *         cursor position, or an empty list if no slot was found there.
     */
    public static List<ItemStack> getAllItemsUnderMouse(Screen jeiScreen, double mouseX, double mouseY) {
        if (jeiScreen == null) return List.of();
        try {
            return probe(jeiScreen, mouseX, mouseY, 0);
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("[JackItToMe] Recipe slot probe failed: {}", t.toString());
            return List.of();
        }
    }

    private static List<ItemStack> probe(Object obj, double mx, double my, int depth) {
        if (obj == null || depth > MAX_DEPTH) return List.of();

        // Lists: recurse into each element.
        if (obj instanceof List<?> list) {
            for (Object item : list) {
                List<ItemStack> r = probe(item, mx, my, depth + 1);
                if (!r.isEmpty()) return r;
            }
            return List.of();
        }

        // Try calling getRecipeSlotUnderMouse(double, double) directly on this object.
        List<ItemStack> direct = tryCallSlotUnderMouse(obj, mx, my);
        if (!direct.isEmpty()) return direct;

        // Don't recurse into vanilla / JDK classes; they don't contain JEI layouts.
        String pkg = obj.getClass().getPackageName();
        if (pkg.startsWith("java.") || pkg.startsWith("net.minecraft.")
                || pkg.startsWith("com.mojang.") || pkg.startsWith("net.neoforged.")) {
            return List.of();
        }

        // Walk fields and recurse.
        Class<?> c = obj.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object value = f.get(obj);
                    if (value != null && value != obj) {
                        List<ItemStack> r = probe(value, mx, my, depth + 1);
                        if (!r.isEmpty()) return r;
                    }
                } catch (Throwable ignored) {}
            }
            c = c.getSuperclass();
        }
        return List.of();
    }

    private static List<ItemStack> tryCallSlotUnderMouse(Object obj, double mx, double my) {
        Method method;
        try {
            method = obj.getClass().getMethod("getRecipeSlotUnderMouse", double.class, double.class);
        } catch (NoSuchMethodException e) {
            return List.of();
        }
        try {
            Object slot = method.invoke(obj, mx, my);
            if (slot instanceof Optional<?> opt) {
                if (opt.isEmpty()) return List.of();
                slot = opt.get();
            }
            if (slot == null) return List.of();
            return getAllIngredients(slot);
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("[JackItToMe] getRecipeSlotUnderMouse invoke failed on {}: {}",
                    obj.getClass().getSimpleName(), t.toString());
            return List.of();
        }
    }

    private static List<ItemStack> getAllIngredients(Object slotView) {
        // Try the typed variant first: getAllIngredients(IIngredientType<T>) → Stream<T>.
        // Then the untyped variant: getAllIngredients() → Stream<ITypedIngredient<?>>.
        // Items in either stream may arrive as bare ItemStack or wrapped in
        // ITypedIngredient, so we unwrap recursively.
        List<ItemStack> r = streamFromMethod(slotView, "getAllIngredients",
                new Class<?>[]{IIngredientType.class}, new Object[]{VanillaTypes.ITEM_STACK});
        if (!r.isEmpty()) return r;
        return streamFromMethod(slotView, "getAllIngredients", new Class<?>[]{}, new Object[]{});
    }

    private static List<ItemStack> streamFromMethod(Object target, String name, Class<?>[] paramTypes, Object[] args) {
        try {
            Method m = target.getClass().getMethod(name, paramTypes);
            Object result = m.invoke(target, args);
            if (result instanceof Stream<?> stream) {
                List<ItemStack> out = new ArrayList<>();
                stream.forEach(o -> {
                    ItemStack s = unwrap(o);
                    if (!s.isEmpty()) out.add(s);
                });
                return out;
            }
        } catch (NoSuchMethodException ignored) {
            // try the other variant
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("[JackItToMe] {}({}) failed: {}", name,
                    paramTypes.length == 0 ? "" : "IIngredientType", t.toString());
        }
        return List.of();
    }

    /** Peel ItemStack out of ITypedIngredient / Optional wrappers. */
    private static ItemStack unwrap(Object o) {
        if (o == null) return ItemStack.EMPTY;
        if (o instanceof ItemStack s) return s.isEmpty() ? ItemStack.EMPTY : s;
        if (o instanceof Optional<?> opt) {
            return opt.map(JeiRecipeSlotProbe::unwrap).orElse(ItemStack.EMPTY);
        }
        // Try ITypedIngredient.getIngredient() reflectively.
        try {
            Method m = o.getClass().getMethod("getIngredient");
            return unwrap(m.invoke(o));
        } catch (NoSuchMethodException ignored) {
            return ItemStack.EMPTY;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }
}
