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
will fit. If the hovered item isn't in stock but the open AE2/RS network
can autocraft it, the keybind escalates to the native autocraft popup,
pre-filled with the modifier amount. The keybind path works against
vanilla container slots, JEI's ingredient list / bookmarks / recipe-
history, and slots inside a JEI recipe view (including cycling "any
planks" tag slots — the resolver picks the variant actually in storage).

**Per-recipe button**: a chest-icon button appears next to JEI's own
bookmark/+ buttons on every recipe view (vanilla, Create, Mekanism, AE2
inscriber, anything that registers a JEI category). Hovering the button
triggers a server-side availability check; ingredients the player can't
fully fulfill get a translucent red overlay (uncraftable) or green overlay
(craftable in AE2/RS) on their slot, in declared order (top row first,
left-to-right).

Clicking the button has two flags driven by Shift:
- **Plain click**:
  - If everything is in stock (no shortage of any kind): pulls every
    ingredient. The trivial case.
  - If any ingredient is missing: triggers the AE2/RS autocraft chain
    for the missing-but-craftable ones and does **not** pull anything —
    even ingredients that are in stock stay in storage.
- **Shift+click** always pulls every in-stock ingredient *and* triggers
  the autocraft chain for any missing-but-craftable ones.

Autocraft fires on both click types when any ingredient is
missing-but-craftable. Shift is the "always pull" modifier; without it,
plain click pulls only when there's no shortage. There is no "block on
shortage" failure animation — plain click against an uncraftable-only
shortage is simply a no-op.

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
- Always ships `PullIngredientsPayload(ingredients, PullMode.SINGLE,
  pullAvailable=shift, triggerAutocraft=true)`. Plain click sends
  `(false, true)`, Shift+click sends `(true, true)`. Empty recipe slots are
  preserved as `Ingredient.EMPTY` for index alignment with the shortage list.

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
JackItToMe/                          (repo root, holds shared config)
├── README.md
├── LICENSE
├── AGENTS.md                        (this file)
├── build.gradle                     Minimal root build (empty stub)
├── gradle.properties                Version pins (shared across subprojects)
├── settings.gradle                  Registers the neoforge/ subproject
├── gradle/wrapper/                  Wrapper pinned to 8.10.2 — do not bump to 9.x
├── gradlew, gradlew.bat
├── libs/                            Local jars fallback (shared)
├── .github/workflows/               Per-branch CI (release.yml + publish-*.yml)
└── neoforge/                        NeoForge subproject — the actual mod build
    ├── build.gradle                 ModDevGradle config + rasterizeIcons task
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

## 3a. Autocraft escalation (added after the original brief)

When the underlying source is AE2 or RS, ingredients that are missing-but-
autocraftable get extra handling. Three surfaces:

**Green vs red overlay** on the recipe-button hover preview. The server's
`AvailabilityHandler.simulate` now returns a `Result(shortages, craftable)`.
Both lists are sent in `AvailabilityResponsePayload`. `JackPullButtonController.
drawShortageOverlays` paints red (`0x80FF4040`) for non-craftable shortages
and green (`0x8040FF40`) for craftable ones.

**P keybind on a missing-but-craftable item**: `PullHandler` notices the
single-ingredient pull moved nothing AND the source reports it craftable. It
calls `source.openAutoCraftPopup(stack, amount, player)` instead of shipping
a failure feedback packet — so no red shake animation, the native popup
appears (pre-filled with the shortfall amount).

**Recipe-button autocraft chain**: every J-button click sends
`PullIngredientsPayload(pullAvailable=shift, triggerAutocraft=true)`.
Server-side `PullHandler` walks the ingredients: if `pullAvailable` it
pulls them, and (independently) if `triggerAutocraft` it collects
unfulfilled-but-craftable ingredients into `chainCandidates`. After
aggregating by item (see §3a.2 below), it ships
`AutocraftChainPayload(List<ItemStack>)` to the client. Client-side
`AutocraftChainController` holds the queue, fires `RequestAutocraftPayload`
for item #0, advances on the *Opening* of a non-popup screen (§3a.1), then
fires #1, etc. Plain click = autocraft only. Shift+click = autocraft +
pull what's in stock.

**AE2 vs RS popup mechanics differ:**

- **AE2** popup is a real `AbstractContainerMenu` (`CraftAmountMenu`). Server
  opens it via `MenuOpener.open(CraftAmountMenu.TYPE, player, locator)` —
  where `locator` comes from `((AEBaseMenu) terminal).getLocator()` so we
  reuse the locator that opened the terminal — and then calls
  `setWhatToCraft(AEKey, int)` on the new menu. All direct API calls.
  `ITerminalHost extends ISubMenuHost`, so closing the popup auto-returns
  the player to the terminal — that's what makes the chain work for AE2.
- **RS** popup is opened via the STABLE public API
  `RefinedStorageClientApi.INSTANCE.openAutocraftingPreview(
  List<ResourceAmount>, @Nullable Screen)`. RS opens it client-side, so
  `RsItemSource.openAutoCraftPopup` sends `OpenRsAutocraftPayload` back to
  the client; `RsAutocraftClient` calls the API. Closing returns to the
  parent screen we passed in.

**Craftability lookups (cheap, called per slot during hover):**

- **AE2**: `IGrid.getService(ICraftingService.class).isCraftable(AEKey)`
  — O(1) lookup. Path: `menu.getActionHost().getActionableNode().getGrid()`.
- **RS**: `Grid.getAutocraftableResources(): Set<PlatformResourceKey>`
  followed by `.contains(itemResource)` — O(1) hash lookup. STABLE since
  RS 2.0.0-milestone.3.0.

### 3a.1 Chain advance detection — the subtle bit

AE2's autocraft flow is multi-screen: `CraftAmountScreen → CraftConfirmScreen
→ submit → terminal`. Triggering the next chain step on close-of-popup would
fire popup #2 while the user is still in `CraftConfirmScreen` for #1,
replacing the confirm screen and losing the in-flight selection. Instead,
`AutocraftChainController` hooks `ScreenEvent.Opening` and advances only
when the *new* screen is **not** one of the popup screens AND the previous
state was "in popup flow". Popup recognition is by class-name substring on
{`craftamount`, `craftconfirm`, `autocrafting`, `crafterror`}; collision-free
with regular terminal/grid screen names. A `ScreenEvent.Closing` fallback
covers the "popup closed to no screen" edge case (deferred two ticks so a
successor popup, if any, gets a chance to open first).

### 3a.2 Network protocol version

`ModPackets.register` calls `.versioned("5")`. Bump on every wire-format
break. Current history:
- `"1"`: initial release
- `"2"`: `AvailabilityResponsePayload` gained `craftable[]`
- `"3"`: `OpenRsAutocraftPayload` gained `amount`; chain `ItemStack`s now
  carry shortfall in their count
- `"4"`: `PullIngredientsPayload` swapped `respectShortageGate` (single
  bool) for two independent flags: `pullAvailable` + `triggerAutocraft`.
  Plain click = autocraft only. Shift adds pulling.
- `"5"`: `JackFeedbackPayload` changed from `(item, moved)` to
  `(List<ItemStack> successItems, ItemStack failureItem)`. Server now
  collects per-`Item` totals during the extract loop; client fans out
  one staggered success animation per unique pulled ingredient type.

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
# From the repo root (D:\Projects\JackItToMe). All loader-specific output
# lives in neoforge/. Bare invocations cascade into all subprojects
# (currently just :neoforge), so either form below works.
./gradlew rasterizeIcons              # regenerates PNGs from SVGs (also auto-runs in build)
./gradlew build                       # → neoforge/build/libs/JackItToMe-neoforge-1.21.1-VERSION.jar
./gradlew runClient                   # dev client with the mod loaded
./gradlew runServer                   # dedicated server

# Equivalent qualified forms (preferred once more loaders exist):
./gradlew :neoforge:build
./gradlew :neoforge:runClient
```

JEI, AE2, RS, guideme are pulled via CurseMaven at runtime.

### Smoke tests

**Keybind path (vanilla container):**
1. New creative world, place a chest, put 8 oak planks in it.
2. Open the chest.
3. Hover an item — in the chest, in your inventory, or in JEI's sidebar.
4. Press P. One item moves chest → inventory.
5. Try Shift+P (one stack), Ctrl+P (as much as fits).

**Recipe-button path (vanilla container, no autocraft available):**
1. Stand at the chest with 7 oak planks in it (one short of a chest recipe).
2. Press R on a chest item to view its recipe.
3. Hover the J button. Within ~50ms the 8th plank slot gets a **red** overlay
   (no AE2/RS, so no green).
4. Click without modifier — **no-op** (no items pulled, no animation, no
   autocraft path on a vanilla container). Tooltip hint shows "Hold Shift
   to pull what's in stock".
5. Shift+click — pulls 7 planks. One slot stays unfulfilled.
6. Add the 8th plank to the chest, wait one refresh cycle (~750ms), red
   overlay should disappear.
7. Now plain click — pulls all 8 (no shortage, so plain click pulls).

**Recipe-button path (AE2 terminal with autocraft):**
1. Set up an AE2 ME network with a pattern producer for oak planks
   (e.g. 1 oak log → 4 planks via a Molecular Assembler with a Pattern Provider).
2. Put 32 logs in the network. Network now has 0 planks but can craft them.
3. Open the ME terminal, view the wooden chest recipe in JEI.
4. Hover the J button — all 8 plank slots get a **green** overlay (within ~100ms).
5. Tooltip shows `8 ingredient(s) needed` / `8 can be autocrafted` /
   no shift hint (nothing in stock to pull).
6. Plain click — AE2's CraftAmountMenu opens, pre-filled with **8**.
   No items move into the player's inventory yet.
7. Cancel out, fill the network with 6 planks, hover J — 6 slots clear,
   2 stay green. Tooltip: `8 ingredient(s) needed` / `2 can be autocrafted` /
   `Hold Shift to also pull what's in stock`.
8. Plain click — popup opens pre-filled with **2** (the shortfall, not 8).
9. Cancel, then Shift+click — 6 planks pull into inventory with fanned-out
   animation, popup opens for 2 more.

**Cache-clear test:**
1. Hover J button on a recipe with a shortage (any overlays visible).
2. Press Esc while still hovering to close the recipe view.
3. Open a different inventory / different container.
4. Open the same recipe again, do NOT hover J.
5. Expect: no overlays visible. They should only appear when you re-hover.

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

---

## 9. Multi-MC-version strategy

This branch builds the mod for Minecraft 1.21.1 / NeoForge 21.1.x. Other MC
versions live on parallel branches (`26.1.2`, etc.). Within each branch the
layout is identical: root holds shared config, `neoforge/` holds the loader
build. If Forge or Fabric ever land, they slot in as sibling subprojects
(`forge/`, `fabric/`) registered in `settings.gradle` — no other
restructuring needed.

**Tag convention:** `v{mcversion}-{modversion}`, e.g. `v1.21.1-0.4.2`. Each
branch's tag triggers its own `release.yml`. The mc-publish step uploads to
CurseForge + Modrinth tagged with that branch's MC version.

**Feature development workflow:** Develop on the oldest supported branch
(currently `1.21.1`) and cherry-pick forward to newer branches with
`git cherry-pick -x` (the `-x` records the original SHA in the message).
Enable `git rerere` globally (`git config --global rerere.enabled true`) so
recurring conflict resolutions auto-apply across the chain of cherry-picks.

**CI maintenance:** Workflow files live in each branch's
`.github/workflows/`. When fixing a CI bug, cherry-pick the workflow edits
to every supported branch — just like any other change.
