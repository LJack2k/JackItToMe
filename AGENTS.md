# JackItToMe — agent brief

You are reading this because you (an AI coding agent) are about to make
changes to this Minecraft mod project. Read the whole file before touching
code. It is written densely on purpose — every section answers a question you
will otherwise have to answer by spelunking through source.

---

## 1. What this mod is

A NeoForge 1.21.x mod that pulls items from a player's currently-open
inventory/storage into the player's own inventory. Two complementary UIs:

**Keybind** (default <kbd>P</kbd>): hover any item anywhere in the open
screen, press the key, one of that item moves into the player's inventory.
Modifier keys change quantity — Shift = a full stack, Ctrl = as much as
will fit. The keybind path works against vanilla container slots, JEI's
ingredient list / bookmarks / recipe-history, and slots inside a JEI recipe
view (including cycling "any planks" tag slots — the resolver picks the
variant actually in storage).

**Per-recipe button**: a chest-icon button appears next to JEI's own
bookmark/+ buttons on every recipe view (vanilla, Create, Mekanism, AE2
inscriber, anything that registers a JEI category). Click pulls every input
ingredient at once. Hovering the button first triggers a server-side
availability check; ingredients the player can't fully fulfill get a
translucent red overlay on their slot, in declared order (top row first,
left-to-right). Normal click is blocked when any slot is short — Shift-click
overrides and pulls whatever's available.

Items come from whatever container is open behind the cursor: vanilla
containers (chests, shulkers, barrels, the player's own inventory), AE2 ME
networks (any terminal-shaped menu), or RS 2.x grids.

The point is to eliminate the "bookmark item → close JEI → drag from
terminal → repeat" loop that modpack players currently do dozens of times
per session.

---

## 2. Architecture in one paragraph (per path)

### Keybind path

`KeyBindings` registers the keybind on the mod event bus.
`ClientEvents.onScreenKey` intercepts every `ScreenEvent.KeyPressed.Pre`,
compares the key to the binding, builds an `Ingredient` from what the cursor
points at — first trying `JeiRecipeSlotProbe.getAllItemsUnderMouse` for
multi-variant recipe slots, falling back to `AbstractContainerScreen#getSlotUnderMouse`
(vanilla containers) or JEI's `IIngredientListOverlay` /
`IBookmarkOverlay#getIngredientUnderMouse` (sidebars). Modifier keys decide
`PullMode` (Ctrl → MAX, Shift → STACK, else SINGLE). Sends a
`PullIngredientsPayload(List<Ingredient>, PullMode)` to the server.

### Recipe-button path

`JackItToMeJeiPlugin.registerAdvanced` registers
`JackPullRecipeButtonFactory` via JEI's `addRecipeButtonFactory(...)` —
universal across every recipe type. JEI calls
`factory.createButtonController(layoutDrawable)` per recipe; we return a
`JackPullButtonController` for any recipe with at least one input slot. The
controller's `drawExtras` does two things every frame:

1. **Hover detection + availability query.** On hover-enter and every 750ms
   while hovered, fires `CheckAvailabilityPayload(nonce, ingredients)`. The
   server walks ingredients in order, simulates the most-abundant resolver
   against a running tally, returns `AvailabilityResponsePayload(nonce,
   shortages[])` — booleans per slot, true = can't fulfill.
   `AvailabilityCache` matches response to request by nonce and keys storage
   on the recipe object (not the slot view — see §5.1.2).

2. **Shortage rendering.** If `AvailabilityCache.shortagesFor(recipe)`
   returns a list, pushes a pose-stack translation to the recipe's screen
   rect and calls `slot.drawHighlight(gg, 0x80FF4040)` on each shortage
   slot. Exactly mirrors what JEI's `RecipeTransferErrorMissingSlots.showError`
   does for the "+" button — which is *why this works universally for any
   recipe type*: button factories aren't filtered by type.

The controller's `onPress`:
- Skips the click if `isSimulate()`.
- Reads `Screen.hasShiftDown()` (NOT `input.getModifiers()` — see §5.1.1).
- If shortage cached and Shift not held: block the click.
- Otherwise ship a `PullIngredientsPayload(List<Ingredient>, PullMode.SINGLE)`
  with one craft's worth — empty slots preserved as `Ingredient.EMPTY` for
  index alignment with the shortage list.

### Server-side fulfillment (shared by both paths)

`PullHandler` resolves the player's open menu against
`ItemSourceRegistry` (AE2 and RS sources register at high priority;
`ContainerItemSource` is the fallback). For each ingredient,
`IngredientResolver.pickMostAbundant` finds the variant the source has the
most of and extracts. Adds to the player's inventory.
`JackFeedbackPayload(item, moved)` ships back; `ClientFeedback.handle`
dispatches to `JackAnimations.start` (falling-into-hotbar) for success or
`JackAnimations.startFailure` (red shake + fade) for failure. No optimistic
animation — every animation reflects a server-confirmed outcome.

---

## 3. File map

```
JackItToMe/                   (repo root = project root)
├── README.md
├── LICENSE
├── AGENTS.md                 (this file)
├── build.gradle              ModDevGradle config + rasterizeIcons task
├── gradle.properties         All version pins live here
├── gradle/wrapper/           Wrapper pinned to 8.10.2 — do not bump to 9.x
├── settings.gradle
├── libs/                     Local jars fallback (mods not on a public maven)
└── src/main/
    ├── java/nl/ljack2k/jackittome/
    │   ├── JackItToMe.java                  Mod entry: packets, sources, client hooks
    │   ├── network/
    │   │   ├── PullMode.java                SINGLE | STACK | MAX
    │   │   ├── PullIngredientsPayload.java  C→S: pull these ingredients
    │   │   ├── JackFeedbackPayload.java     S→C: animation dispatch (success vs failure)
    │   │   ├── CheckAvailabilityPayload.java     C→S: simulate this recipe's pull
    │   │   ├── AvailabilityResponsePayload.java  S→C: per-slot shortage booleans
    │   │   └── ModPackets.java              RegisterPayloadHandlersEvent listener
    │   ├── server/
    │   │   ├── PullHandler.java             Orchestrates per-ingredient pull + feedback
    │   │   ├── IngredientResolver.java      Most-abundant variant picker
    │   │   └── AvailabilityHandler.java     Simulates the pull, returns shortage list
    │   ├── source/
    │   │   ├── ItemSource.java              Source abstraction (match/count/extract)
    │   │   ├── ItemSourceRegistry.java      Ordered list, first match wins
    │   │   └── ContainerItemSource.java     Vanilla AbstractContainerMenu walker (fallback)
    │   ├── client/
    │   │   ├── KeyBindings.java             Mod-bus keybind registration
    │   │   ├── ClientEvents.java            P-key handler, hover lookup, packet send
    │   │   ├── ClientFeedback.java          Dispatches animation from feedback packet
    │   │   ├── JackAnimations.java          Falling-into-hotbar + red-shake-fade visuals
    │   │   ├── AvailabilityCache.java       Recipe→shortages cache, nonce-matched
    │   │   └── JeiRecipeSlotProbe.java      Reflective: all-variants lookup for recipe-slot hover
    │   ├── jei/
    │   │   ├── JackItToMeJeiPlugin.java         @JeiPlugin; registers button factory
    │   │   ├── JackPullRecipeButtonFactory.java IRecipeButtonControllerFactory
    │   │   └── JackPullButtonController.java    IIconButtonController: button + shortage overlays
    │   └── compat/
    │       ├── ae2/Ae2ItemSource.java       AE2 MEStorage extraction (guarded by ModList)
    │       └── rs/RsItemSource.java         RS 2.x Grid storage extraction
    └── resources/
        ├── META-INF/neoforge.mods.toml      Dep declarations; processResources expands ${...}
        ├── pack.mcmeta
        ├── icon.svg                         Mod icon source (rendered to icon.png at build)
        ├── icon-transparent.svg             Button icon source (rendered to .../jack_button.png)
        └── assets/jackittome/lang/en_us.json
```

`icon.png`, `icon-transparent.png`, and `assets/jackittome/textures/gui/jack_button.png`
are gitignored — the `rasterizeIcons` Gradle task regenerates them from the
SVGs on every build.

---

## 4. Where the seams are

**`ItemSource`** — "where do we pull from". To add support for a new storage
mod (Sophisticated Storage, Iron Chests, Functional Storage), write a new
`ItemSource` implementation, register with priority in
`ItemSourceRegistry`. Do not add storage-mod-specific code to `PullHandler`
or `IngredientResolver`.

**`PullMode`** — input/policy. New quantity modes go here; the mode flows
through the packet unchanged, only the `switch` in `PullHandler.handle`
interprets it. `ClientEvents` wires `Screen.hasShiftDown()` / `hasControlDown()`
to STACK / MAX.

**`JackPullButtonController.drawExtras`** — anything that needs to render
per-recipe and work for all recipe types belongs here, not in a decorator.
JEI's decorator API requires per-type registration; the button factory is
universal, so drawing from the controller's drawExtras gives free coverage.
This is the same pattern JEI itself uses (see `RecipeTransferButtonController`).

---

## 5. Known fragile bits — read before editing

### 5.1 JEI hover lookups (`client/ClientEvents.java`, `client/JeiRecipeSlotProbe.java`)

Three reflective surfaces, all related to "what is the cursor over right now":

1. **Sidebar / bookmark / history hover**: calls
   `IIngredientListOverlay#getIngredientUnderMouse` and
   `IBookmarkOverlay#getIngredientUnderMouse`. The return type has varied
   across JEI versions: `T`, `Optional<T>`, `ITypedIngredient<T>`, or
   `Optional<ITypedIngredient<T>>`. The result goes through
   `unwrapToItemStack` which peels Optionals and ITypedIngredient wrappers
   recursively.

2. **Recipe-slot hover** (`JeiRecipeSlotProbe.getAllItemsUnderMouse`):
   walks the JEI screen reflectively (depth ≤4, skips JDK/Minecraft
   packages) looking for any object that responds to
   `getRecipeSlotUnderMouse(double, double)`. That method is on
   `IRecipeLayoutDrawable` but JEI doesn't expose the drawable through
   public-API channels.

If JEI ever returns yet another shape or renames the methods, debug-level
logs in both files show which call returned empty. The vanilla
container-slot path (`AbstractContainerScreen#getSlotUnderMouse`) is
independent of JEI and always works as a fallback.

#### 5.1.1 JEI's `IJeiUserInput.getModifiers()` returns 0

In JEI 19.27, `IJeiUserInput.getModifiers()` is **always 0** regardless of
actual modifier state. Use `Screen.hasShiftDown()` / `hasControlDown()` /
`hasAltDown()` which read GLFW's keyboard state directly. We keep an `OR`
against `getModifiers()` as future-proofing in case JEI starts populating
it.

#### 5.1.2 `IRecipeSlotsView` identity is unstable

Each call to `getRecipeSlotsView()` (and the slotsView parameter passed to
decorators) returns a **freshly-allocated wrapper** every render frame —
identity comparison (`==`) never matches. Key any per-recipe state on the
*recipe object itself* (`layoutDrawable.getRecipe()`) — those instances are
stable across renders because JEI holds them from Minecraft's recipe
manager. This is why `AvailabilityCache` keys on the recipe, not the view.

#### 5.1.3 Drawing on slots from `drawExtras` requires manual pose translation

JEI's button-controller `drawExtras(gg, buttonArea, mouseX, mouseY, partialTicks)`
receives the GuiGraphics in **screen coordinates** (verified empirically:
mouseX matches buttonArea.getX() screen position). To draw on a recipe's
slots, push the pose, translate to `layoutDrawable.getRect().getX(), getY()`,
then call `slot.drawHighlight(gg, color)` — the slot's drawHighlight expects
coordinates relative to the recipe origin. This is the same translation
`RecipeTransferErrorMissingSlots.showError` does internally for the "+"
button.

### 5.2 AE2 storage accessor (`compat/ae2/Ae2ItemSource.java#storageOf`)

The line `menu.getHost().getInventory()` is the AE2-specific guess. Verify
against the AE2 version in `gradle.properties`. Recent AE2 sometimes uses
`getStorage()` instead. The rest of the AE2 integration (MEStorage,
AEItemKey, Actionable, IActionSource) has been stable for multiple major
versions.

### 5.3 Refined Storage compat (`compat/rs/RsItemSource.java`)

Wired against RS 2.0.0's real API. Path: menu → `grid` field (reflective,
package-private) → `Grid#getItemStorage()` → `Storage` interface →
`extract` / `insert` with `Action.EXECUTE` and a `PlayerActor`. Counting
uses `extract(resource, Long.MAX_VALUE, SIMULATE, actor)` because RS doesn't
expose a direct getAmount method.

Only fragile bit: reading the `grid` field reflectively. If RS adds a public
getter (e.g. `menu.getGrid()`) or renames the field, swap `gridOf` for a
direct call.

Coverage: all standard grid menus (regular, crafting, pattern, wireless,
portable) inherit from `AbstractGridContainerMenu` so the `instanceof` check
in `matches` covers them all.

### 5.4 Gradle is pinned to 8.10.2

`gradle/wrapper/gradle-wrapper.properties` pins Gradle 8.10.2. Gradle 9.x
ships Groovy 4; ModDevGradle 1.x was compiled against Groovy 3 —
incompatible. If IntelliJ regenerates the wrapper with 9.x, change it back.

### 5.5 Version pins drift fast

`gradle.properties` holds pins for minecraft, neoforge, jei, ae2, rs,
guideme. Bump them together; CurseMaven file IDs in `gradle.properties` need
to be updated per-version. Vendor links:

- NeoForge: https://projects.neoforged.net/neoforged/neoforge
- JEI / AE2 / RS / guideme: each mod's CurseForge file page has a
  "Curse Maven Snippet" panel with the exact dep string.

### 5.6 SVG-to-PNG rasterization on build

`rasterizeIcons` Gradle task uses Apache Batik (buildscript-only classpath)
to convert each SVG in `src/main/resources/` to a PNG at a fixed size,
then bicubic-downsamples for crisp small-target results. `processResources`
depends on it. The mappings are at the top of `build.gradle` —
`iconMappings` list. Add an entry to register a new icon. SVGs are excluded
from the jar (`exclude '**/*.svg'`); only PNGs ship.

---

## 6. Build & test

```bash
# From the project root (D:\Projects\JackItToMe)
./gradlew rasterizeIcons     # regenerates PNGs from SVGs (also auto-runs in build)
./gradlew build              # produces build/libs/JackItToMe-neoforge-1.21.1-VERSION.jar
./gradlew runClient          # dev client with the mod loaded
./gradlew runServer          # dedicated server
```

JEI, AE2, RS, guideme are pulled via CurseMaven at runtime.

### Smoke tests

**Keybind path:**
1. New creative world, place a chest, put 8 oak planks in it.
2. Open the chest.
3. Hover an item — in the chest, in your inventory, or in JEI's sidebar.
4. Press P. One item moves chest → inventory.
5. Try Shift+P (one stack), Ctrl+P (as much as fits).

**Recipe-button path:**
1. With 7 oak planks in the chest (one short of a chest recipe).
2. Press R on a chest item to view its recipe.
3. Hover the J button. Within ~50ms the 8th plank slot gets a red overlay.
4. Click without modifier — blocked, log shows
   "click blocked: shortage".
5. Add an 8th plank to the chest, wait one refresh cycle (~750ms), red
   should disappear.
6. Now click — pulls all 8 planks at once.
7. Re-empty to 7 planks, hover, Shift-click — pulls 7 planks, leaves the
   one slot unfulfilled.

If hover triggers nothing visible: check `run/logs/latest.log` for
`[Jack-cache]` or `[Jack-button]` entries (diagnostic logs added during
development — left in for future debugging).

---

## 7. Things to deliberately not do

- Do **not** add a "place ingredients in crafting grid" mode. JEI's "+"
  button already does that. Our button always pulls into the player's
  inventory.
- Do **not** broaden the network protocol to allow the client to specify
  arbitrary ItemStacks without going through an `Ingredient`. Always wrap
  the hovered stack as `Ingredient.of(stack)` for exact-match — arbitrary
  stacks let a hostile client extract anything from any open container
  regardless of what they're viewing.
- Do **not** register a per-recipe-type decorator for cross-recipe
  rendering. Use `IRecipeButtonControllerFactory` + draw from `drawExtras`
  — that pattern is universal across all recipe types (vanilla, Create,
  Mekanism, anything) for free. Per-type decorator registration is what
  the old code did and it only covered hardcoded vanilla types.
- Do **not** assume the player is also the listener of `containerMenu`. On
  some modded GUIs the menu is shared. Always call `broadcastChanges()` on
  both `inventoryMenu` and the open `containerMenu` after a mutation.
- Do **not** key per-recipe client-side state on `IRecipeSlotsView`
  identity. Use the recipe object instead — see §5.1.2.

---

## 8. Reporting back

When you finish a change, mention:

1. Which file(s) you touched.
2. Whether you re-ran the smoke test (§6) or only compiled.
3. Whether you touched any of the fragile bits in §5 — if yes, what version
   of JEI/AE2/RS you verified against.
4. Anything you noticed that contradicts this brief. Then update this brief.
