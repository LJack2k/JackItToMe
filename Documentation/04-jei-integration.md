# JEI integration — patterns and undocumented gotchas

Just Enough Items is the de facto recipe-viewing mod. If your mod
integrates with it, you'll touch its API a lot. This doc captures the
**right** patterns plus the **undocumented quirks** that consumed
days of debugging during JackItToMe.

Versions referenced: JEI 19.x (Minecraft 1.21.x). Earlier JEI had a
different package layout (`mezz.jei.api.gui.IDrawable` vs
`mezz.jei.api.gui.drawable.IDrawable`, etc.).

## The plugin entry point

Every JEI integration starts with a `@JeiPlugin`-annotated class:

```java
@JeiPlugin
public final class MyJeiPlugin implements IModPlugin {

    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(MyMod.MODID, "jei_plugin");
    }

    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        // Add button factories, recipe-category decorators, etc.
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    /** Used by client code to query JEI at runtime. */
    public static IJeiRuntime runtime() {
        return runtime;
    }
}
```

The `@JeiPlugin` annotation is enough — JEI scans for it during init.
You do **not** need to register the class elsewhere.

JEI's plugin phases run in this order across all registered plugins:
`registerItemSubtypes` → `registerCategories` →
`registerVanillaCategoryExtensions` → `registerRecipes` →
`registerRecipeTransferHandlers` → `registerRecipeCatalysts` →
`registerGuiHandlers` → `registerAdvanced` → `registerRuntime` →
`onRuntimeAvailable`.

By the time `registerAdvanced` runs, all categories from all plugins
are already registered. By the time `onRuntimeAvailable` runs, JEI is
fully booted.

## Per-recipe buttons (the right way)

The most common integration: add a button to every recipe view. The
correct API is **`IRecipeButtonControllerFactory`** + **`IIconButtonController`**.

```java
@Override
public void registerAdvanced(IAdvancedRegistration registration) {
    registration.addRecipeButtonFactory(
            new MyButtonFactory(registration.getJeiHelpers()));
}
```

The factory is **universal** — called for every recipe layout JEI
displays, regardless of which mod registered the recipe type. There's
no per-type filtering, which is the whole point.

```java
public class MyButtonFactory implements IRecipeButtonControllerFactory {
    private final IJeiHelpers helpers;

    public MyButtonFactory(IJeiHelpers helpers) {
        this.helpers = helpers;
    }

    @Override
    @Nullable
    public <T> IIconButtonController createButtonController(
            IRecipeLayoutDrawable<T> layoutDrawable) {
        // Return null to skip this recipe (e.g. no input slots).
        if (layoutDrawable.getRecipeSlotsView()
                .getSlotViews(RecipeIngredientRole.INPUT).isEmpty()) {
            return null;
        }
        return new MyButtonController(layoutDrawable, helpers);
    }
}
```

**Important Java generic detail:** the method signature must include
the `<T>` type parameter. Substituting `<?>` (wildcard) doesn't compile
— you get a "name clash: same erasure, yet neither overrides the
other" error. Inside the controller you can store the layout as
`IRecipeLayoutDrawable<?>`; only the factory method's signature needs
the `<T>` form.

The controller:

```java
public class MyButtonController implements IIconButtonController {

    private final IRecipeLayoutDrawable<?> layoutDrawable;
    private final IDrawable icon;

    public MyButtonController(IRecipeLayoutDrawable<?> layoutDrawable,
                              IJeiHelpers helpers) {
        this.layoutDrawable = layoutDrawable;
        this.icon = helpers.getGuiHelper()
                .drawableBuilder(ICON_RESOURCE_LOCATION, 0, 0, 16, 16)
                .setTextureSize(16, 16)   // see "the icon texture-size gotcha" below
                .build();
    }

    @Override
    public void initState(IButtonState state) {
        state.setIcon(icon);
        state.setVisible(true);
        state.setActive(true);
    }

    @Override
    public void updateState(IButtonState state) {
        // Called every frame. Update visibility/active state if needed.
    }

    @Override
    public boolean onPress(IJeiUserInput input) {
        if (input.isSimulate()) return true;  // see "isSimulate" below

        // ... do the work ...
        return true;  // returning true tells JEI we handled it
    }

    @Override
    public void getTooltips(ITooltipBuilder tooltip) {
        tooltip.add(Component.translatable("mymod.button.tooltip"));
    }

    @Override
    public void drawExtras(GuiGraphics gg, Rect2i buttonArea,
                           int mouseX, int mouseY, float partialTicks) {
        // Optional: draw additional graphics over the button or on the
        // recipe. JEI calls this every frame on every visible recipe.
        // GuiGraphics is in screen coordinates here.
    }
}
```

### Gotcha: `IJeiUserInput.getModifiers()` always returns 0

JEI does **not** populate the modifier-key bits when calling
`onPress`. The field returns `0` regardless of whether Shift / Ctrl /
Alt are held.

**Wrong:**

```java
boolean shift = (input.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
```

**Right:**

```java
boolean shift = Screen.hasShiftDown();
boolean ctrl  = Screen.hasControlDown();
```

`Screen.hasShiftDown()` queries GLFW's keyboard state directly and is
reliable across all input paths. Belt-and-braces: combine both checks
with `||` so the code works if JEI ever fixes the bug.

### Gotcha: `isSimulate()` means "would you handle this?"

`onPress` is called with `input.isSimulate() == true` during hover —
JEI is asking "if the user clicked right now, would you do something?"
to decide whether to show the button as interactive. Return `true` to
say "yes, I'd handle a real click." Don't actually fire your action.

Pattern:

```java
@Override
public boolean onPress(IJeiUserInput input) {
    if (input.isSimulate()) return true;
    // do the actual work
    return true;
}
```

### Drawing on recipe slots from `drawExtras`

JEI's `drawExtras` runs with `GuiGraphics` in **screen coordinates**.
To draw on the recipe area (e.g. highlight slots), translate the pose
to the recipe's screen rect first:

```java
@Override
public void drawExtras(GuiGraphics gg, Rect2i buttonArea,
                       int mouseX, int mouseY, float partialTicks) {
    Rect2i recipeRect = layoutDrawable.getRect();

    gg.pose().pushPose();
    gg.pose().translate(recipeRect.getX(), recipeRect.getY(), 0);

    for (IRecipeSlotView slot : someSlots) {
        slot.drawHighlight(gg, 0x80FF4040);  // translucent red
    }

    gg.pose().popPose();
}
```

`slot.drawHighlight(gg, color)` draws relative to whatever the current
pose stack says. The recipe-rect translation puts it in the right
place.

This is the same pattern JEI itself uses for the "+" button's missing-
slot indicators (`RecipeTransferErrorMissingSlots.showError`).
**Drawing from `drawExtras` works for every recipe type** because
button factories aren't filtered by type. This is the right place for
any per-recipe rendering that needs universal coverage.

## Recipe category decorators (use sparingly)

JEI also has `IRecipeCategoryDecorator`:

```java
registration.addRecipeCategoryDecorator(recipeType, decorator);
```

This is the **wrong** API to use for "draw something on every recipe."
It requires per-`RecipeType` registration, and there's no "register
for everything" variant. You'd hardcode a list of vanilla types and
miss every modded type (Create, Mekanism, AE2 inscriber, etc.).

Use `IRecipeButtonControllerFactory` + draw from `drawExtras` instead.
Decorators are for the rare case where you specifically want to
decorate one particular recipe type (e.g., add an icon to crafting
recipes only).

## Hovered ingredient lookup

Three places JEI shows items, each with its own API:

### 1. Right sidebar (ingredient list)

```java
IJeiRuntime rt = MyJeiPlugin.runtime();
if (rt != null) {
    Optional<ITypedIngredient<ItemStack>> hov = rt.getIngredientListOverlay()
            .getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
    if (hov.isPresent()) {
        ItemStack stack = hov.get().getIngredient();
    }
}
```

### 2. Left sidebar (bookmarks + recipe history)

```java
Optional<ITypedIngredient<ItemStack>> hov = rt.getBookmarkOverlay()
        .getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
```

In modern JEI, the bookmark overlay also contains the recipe history,
so one call covers both.

### 3. Inside an open recipe view

```java
Optional<ITypedIngredient<ItemStack>> hov = rt.getRecipesGui()
        .getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
```

### Gotcha: return type varies across JEI versions

The return type of `getIngredientUnderMouse` has changed shape across
JEI versions: `T`, `Optional<T>`, `ITypedIngredient<T>`,
`Optional<ITypedIngredient<T>>`. If you call it with a specific
expected type, you may get a compile-time inference error like:

```
incompatible types: inference variable T has incompatible bounds
    equality constraints: ItemStack
    upper bounds: Optional<ItemStack>,Object
```

The defensive pattern is a reflective wrapper that handles any shape:

```java
private static ItemStack tryGetHover(Object overlay) {
    try {
        for (Method m : overlay.getClass().getMethods()) {
            if (!"getIngredientUnderMouse".equals(m.getName())) continue;
            if (m.getParameterCount() != 1) continue;
            Object result = m.invoke(overlay, VanillaTypes.ITEM_STACK);
            return unwrapToItemStack(result);
        }
    } catch (Throwable ignored) {}
    return ItemStack.EMPTY;
}

private static ItemStack unwrapToItemStack(Object o) {
    if (o == null) return ItemStack.EMPTY;
    if (o instanceof ItemStack s) return s.isEmpty() ? ItemStack.EMPTY : s;
    if (o instanceof Optional<?> opt) {
        return opt.map(YourClass::unwrapToItemStack).orElse(ItemStack.EMPTY);
    }
    // Likely ITypedIngredient — call getIngredient() reflectively
    try {
        Method m = o.getClass().getMethod("getIngredient");
        return unwrapToItemStack(m.invoke(o));
    } catch (Throwable t) {
        return ItemStack.EMPTY;
    }
}
```

Yes, it's ugly. It works across every JEI version since 11. Save the
"pretty" direct API call for the day JEI commits to a stable shape.

## Recipe-slot multi-variant resolution

For a recipe slot that cycles through "any planks" or similar tag
ingredients, JEI's `IRecipeSlotView.getItemStacks()` returns a stream
of every variant the slot accepts:

```java
List<ItemStack> variants = slot.getItemStacks().toList();
// e.g., for "any planks": [oak_planks, birch_planks, spruce_planks, ...]
```

Use these to build an `Ingredient` that accepts any variant:

```java
Ingredient ing = Ingredient.of(variants.toArray(ItemStack[]::new));
```

Send the full Ingredient to the server in a packet so the server can
pick whichever variant the player's storage actually has, instead of
locking to the one that happened to be displayed at the moment of
the click.

## Finding a recipe layout from outside the layout itself

JEI doesn't expose the active `IRecipeLayoutDrawable` through its
public API. If you need to walk the recipe state from elsewhere
(e.g., a global ScreenEvent), reflective field walking is the only
option. Pattern used in JackItToMe's `JeiRecipeSlotProbe`:

```java
private static List<ItemStack> walkForSlotUnderMouse(
        Object obj, double mx, double my, int depth) {
    if (obj == null || depth > 4) return List.of();

    // Lists/Collections: recurse
    if (obj instanceof List<?> list) {
        for (Object item : list) {
            List<ItemStack> r = walkForSlotUnderMouse(item, mx, my, depth + 1);
            if (!r.isEmpty()) return r;
        }
        return List.of();
    }

    // Try calling getRecipeSlotUnderMouse on this object
    try {
        Method m = obj.getClass().getMethod(
                "getRecipeSlotUnderMouse", double.class, double.class);
        Object slot = m.invoke(obj, mx, my);
        if (slot instanceof Optional<?> opt && opt.isPresent()) {
            return getAllIngredients(opt.get());
        }
    } catch (NoSuchMethodException ignored) {}

    // Skip JDK/Minecraft packages
    String pkg = obj.getClass().getPackageName();
    if (pkg.startsWith("java.") || pkg.startsWith("net.minecraft.")) {
        return List.of();
    }

    // Walk fields
    Class<?> c = obj.getClass();
    while (c != null && c != Object.class) {
        for (Field f : c.getDeclaredFields()) {
            try {
                f.setAccessible(true);
                Object value = f.get(obj);
                if (value != null && value != obj) {
                    List<ItemStack> r = walkForSlotUnderMouse(
                            value, mx, my, depth + 1);
                    if (!r.isEmpty()) return r;
                }
            } catch (Throwable ignored) {}
        }
        c = c.getSuperclass();
    }
    return List.of();
}
```

Bounded depth (4 is usually enough), skip non-JEI packages to avoid
running forever. The method calls (`getRecipeSlotUnderMouse`,
`getAllIngredients`) are public JEI API — only the path to reach the
object that exposes them is private. This pattern stays robust across
JEI versions because the methods stay stable even when their owning
class moves.

## Drawables and the texture-size gotcha

To create an icon `IDrawable` from a texture file:

```java
// WRONG — defaults to 256x256 atlas size, will sample garbage from a 16x16 PNG
IDrawable icon = helpers.getGuiHelper().createDrawable(loc, 0, 0, 16, 16);

// RIGHT — explicit texture size
IDrawable icon = helpers.getGuiHelper()
        .drawableBuilder(loc, 0, 0, 16, 16)
        .setTextureSize(16, 16)
        .build();
```

`createDrawable` assumes the texture file is 256×256 (Minecraft's
standard atlas size). For a standalone 16×16 PNG, the UV math is
wrong — JEI ends up sampling only the top-left pixel and your button
appears empty. **Always use `drawableBuilder` + `setTextureSize` for
non-atlas textures.**

If you have multiple icons in a sprite sheet (one larger PNG with
multiple regions), `setTextureSize(sheetWidth, sheetHeight)` and use
the u/v offsets to pick out each region.

## Identity instability: `IRecipeSlotsView`

`getRecipeSlotsView()` returns a **freshly-allocated wrapper** on
every render frame. Reference comparison (`==`) never matches between
calls — even for the "same" recipe.

If you need to track per-recipe state across frames (e.g., a cache
keyed on a particular recipe being hovered), key on the **recipe
object itself** (`layoutDrawable.getRecipe()`), not on the slot view.
The recipe object is stable — JEI holds the actual instance from
Minecraft's recipe manager.

This caused a flicker / "shortages always null" bug in JackItToMe
that wasn't obvious until diagnostic logs showed the identity hashes
changing between frames.

## Common JEI patterns quick-reference

| What you want | API |
| --- | --- |
| Add a button to every recipe view | `addRecipeButtonFactory` + `IIconButtonController` |
| Detect click in your button | `onPress(IJeiUserInput)` — check `isSimulate()` first |
| Draw on the recipe area | `drawExtras` + pose translation to `layoutDrawable.getRect()` |
| Highlight a slot | `slot.drawHighlight(gg, ARGB)` (inside translated pose) |
| Read recipe input slots | `getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT)` |
| Read all variants of a slot | `slot.getItemStacks().toList()` |
| Get the item under the cursor in the sidebar | `runtime.getIngredientListOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK)` |
| Get the recipe currently shown | `runtime.getRecipesGui().getIngredientUnderMouse(...)` |
| Modifier-key state | `Screen.hasShiftDown()` (NOT `input.getModifiers()`) |
