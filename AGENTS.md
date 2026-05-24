# JackItToMe — agent brief

You are reading this because you (an AI coding agent) are about to make
changes to this Minecraft mod project. Read the whole file before touching
code. It is written densely on purpose — every section answers a question you
will otherwise have to answer by spelunking through source.

---

## 1. What this mod is

A NeoForge 1.21.x mod that adds a single client-side keybind. Hover the cursor
over any item — in a container slot, in your inventory, or in JEI's sidebar —
and press the bound key (default <kbd>P</kbd>). One of that item moves into
your inventory, sourced from whatever inventory or storage network you have
open behind the current screen.

It is intentionally **not** a crafting helper. It does not place items in a
crafting grid, it does not auto-craft, it does not move bookmarks. It just
moves an item out of an open storage source into the player's main inventory.

The point is to remove the "bookmark item → close JEI → drag item out of
terminal" loop that modpack players currently do dozens of times per session.

### User-facing behavior

| Action                          | Effect                            |
| ------------------------------- | --------------------------------- |
| Hover any item, press P         | Pull one of that item into inventory |
| Hover empty slot, press P       | Nothing                           |
| Hover item not in any open source, press P | "Nothing matching available here" toast |

The keybind is remappable from **Options → Controls → JackItToMe**.

### Why a keybind and not a button

An earlier iteration added a button to JEI's recipe view that would jack the
displayed recipe's ingredients. That was discarded because:
- A recipe view paginates through multiple recipes; one button per pane is
  ambiguous (which recipe does it pull?).
- The button required reading the displayed recipe out of JEI's private state
  via reflection — fragile and version-dependent.
- A keybind covers more ground (any item, any container) and sidesteps both
  problems.

A per-recipe button is still a reasonable feature, but it's a separate
project from the keybind. Do not resurrect the deleted button code; treat it
as a fresh design problem if you ever tackle it.

---

## 2. Architecture in one paragraph

`KeyBindings` registers the keybind on the mod event bus.
`ClientEvents.onScreenKey` intercepts every `ScreenEvent.KeyPressed.Pre`,
compares the key to the binding, and if matched builds an `Ingredient`
representing what the player pointed at. The ingredient is built by trying
two paths: first `JeiRecipeSlotProbe.getAllItemsUnderMouse` (reflectively
walks JEI's recipe layout to find the slot under the cursor and returns all
variants the slot accepts — crucial for the cycling "any planks" case);
if that yields fewer than two variants, fall back to a single-item lookup
via `AbstractContainerScreen#getSlotUnderMouse` (vanilla containers) or
JEI's `IIngredientListOverlay` / `IBookmarkOverlay` (sidebars and history).
The resulting ingredient is shipped to the server as a `PullIngredientsPayload`.
The server resolves the player's open menu against an ordered
`ItemSourceRegistry`, picks the most-abundant variant (trivially one variant
for an exact-match ingredient), extracts one, adds it to the player's
inventory, then ships back a `JackFeedbackPayload(item, moved)`. The client
receives that payload in `ClientFeedback.handle`, which dispatches to
`JackAnimations.start` (falling-into-hotbar animation) for success or
`JackAnimations.startFailure` (red-shake-and-fade) for failure. No
optimistic animation — every animation reflects an outcome the server
already confirmed.

---

## 3. File map

```
JackItToMe/                   (repo root = project root, no Source/ wrapper)
├── README.md
├── LICENSE
├── AGENTS.md                 (this file)
├── build.gradle              ModDevGradle config; pin versions in gradle.properties
├── gradle.properties         All version pins live here
├── gradle/wrapper/           Gradle wrapper (pinned to 8.10.2 — do not bump to 9.x)
├── settings.gradle
├── libs/                     Local jars fallback for mods not on a public maven
└── src/main/
    ├── java/nl/ljack2k/jackittome/
    │   ├── JackItToMe.java                  Mod entry; wires packets, sources, client hooks
    │   ├── network/
    │   │   ├── PullMode.java                SINGLE | STACK | MAX
    │   │   ├── PullIngredientsPayload.java  Client → server: what to pull
    │   │   ├── JackFeedbackPayload.java     Server → client: what actually moved (animation dispatch)
    │   │   └── ModPackets.java              RegisterPayloadHandlersEvent listener
    │   ├── server/
    │   │   ├── PullHandler.java             Orchestrates per-ingredient pull + sends feedback
    │   │   └── IngredientResolver.java      Most-abundant variant picker
    │   ├── source/
    │   │   ├── ItemSource.java              Source abstraction (match/count/extract)
    │   │   ├── ItemSourceRegistry.java      Ordered list, first match wins
    │   │   └── ContainerItemSource.java     Vanilla AbstractContainerMenu walker (fallback)
    │   ├── client/
    │   │   ├── KeyBindings.java             Registers the keybind on the mod bus
    │   │   ├── ClientEvents.java            Keybind detection, hover lookup, packet send
    │   │   ├── ClientFeedback.java          Dispatches success/failure animation from feedback packet
    │   │   ├── JackAnimations.java          Falling-into-hotbar + red-shake-and-fade visuals
    │   │   └── JeiRecipeSlotProbe.java      Reflective: all-variants lookup for recipe-slot hover
    │   ├── jei/
    │   │   └── JackItToMeJeiPlugin.java     @JeiPlugin; holds IJeiRuntime reference for sidebar lookup
    │   └── compat/
    │       ├── ae2/Ae2ItemSource.java       AE2 MEStorage extraction (guarded by ModList)
    │       └── rs/RsItemSource.java         Refined Storage — currently reflective stubs (see §5.3)
    └── resources/
        ├── META-INF/neoforge.mods.toml      Dep declarations; processResources expands ${...}
        ├── pack.mcmeta
        └── assets/jackittome/lang/en_us.json
```

---

## 4. Where the seams are

**`ItemSource`** is the seam between "what's open" and "how do we pull from
it". To add support for a new storage mod (Sophisticated Storage, Iron
Chests, Functional Storage, etc.), write a new `ItemSource` implementation
and register it with priority. Do **not** add storage-mod-specific code to
`PullHandler` or `IngredientResolver`.

**`PullMode`** is the seam between input and policy. New modes (e.g.
"hold-key-repeats" or shift-modified quantities) go here. The mode flows
through the packet unchanged; the only place it's interpreted is the switch
in `PullHandler.handle`. Currently `ClientEvents` always sends `SINGLE`;
adding shift/ctrl for STACK/MAX is a small change to `onScreenKey`.

---

## 5. Known fragile bits — read before editing

### 5.1 JEI hover lookups (`client/ClientEvents.java#tryJeiOverlayHover`, `client/JeiRecipeSlotProbe.java`)

We touch JEI through three reflective surfaces, all related to "what is the
cursor over right now":

1. **Sidebar / bookmark overlay hover**: calls
   `IIngredientListOverlay#getIngredientUnderMouse` and
   `IBookmarkOverlay#getIngredientUnderMouse`. The return type has varied
   across JEI versions: `T`, `Optional<T>`, `ITypedIngredient<T>`, or
   `Optional<ITypedIngredient<T>>`. The result goes through `unwrapToItemStack`
   which peels Optionals and ITypedIngredient wrappers recursively.

2. **Recipe-slot hover**: `JeiRecipeSlotProbe.getAllItemsUnderMouse` walks
   the JEI screen reflectively (bounded depth 4, skips JDK/Minecraft packages)
   looking for any object that responds to `getRecipeSlotUnderMouse(double, double)`
   — that's a public method on `IRecipeLayoutDrawable` but JEI doesn't expose
   the drawable through public-API channels. Once found, calls
   `getAllIngredients(IIngredientType)` (typed) or `getAllIngredients()`
   (untyped) and unwraps the stream contents.

If JEI ever returns yet another shape or renames `getRecipeSlotUnderMouse` /
`getAllIngredients`, debug-level logs in both files show which call returned
empty. The vanilla container-slot path
(`AbstractContainerScreen#getSlotUnderMouse`) is independent of JEI and
always works as a fallback for non-recipe-view hovers.

#### 5.1.1 JEI's `IJeiUserInput.getModifiers()` returns 0

When JEI dispatches a button click through `IIconButtonController.onPress`,
the `IJeiUserInput.getModifiers()` field is **always 0** regardless of
whether Shift/Ctrl/Alt were actually held. This is undocumented behavior
that bit us when implementing the "Shift to override shortage" gate in
`JackPullButtonController`. The fix is to query Minecraft's
`Screen.hasShiftDown()` (and `hasControlDown()` / `hasAltDown()`) which
reads GLFW's keyboard state directly. We keep an `OR` against
`getModifiers()` for future-proofing in case JEI starts populating it.

#### 5.1.2 `IRecipeSlotsView` identity is unstable

Each call to `getRecipeSlotsView()` (or the decorator's slotsView parameter)
returns a **freshly-allocated wrapper** on every render frame, so identity
comparison (`==`) never matches between calls. If you need to pair state
keyed on a recipe (e.g. our shortage cache), key on the *recipe object*
itself (`layoutDrawable.getRecipe()`) — those instances ARE stable across
renders because JEI holds them from Minecraft's recipe manager.

### 5.2 AE2 storage accessor (`compat/ae2/Ae2ItemSource.java#storageOf`)

The line `menu.getHost().getInventory()` is the AE2-specific guess. Verify
against the AE2 version in `gradle.properties`. Recent AE2 sometimes uses
`getStorage()` instead. The rest of the AE2 integration (MEStorage,
AEItemKey, Actionable, IActionSource) has been stable for multiple major
versions and should not need changes.

### 5.3 Refined Storage compat (`compat/rs/RsItemSource.java`)

Wired against RS 2.0.0's actual API. Path: menu → `grid` field
(reflective, package-private) → `Grid#getItemStorage()` → `Storage` interface
→ `extract` / `insert` with `Action.EXECUTE` and a `PlayerActor`. Counting
uses `extract(resource, Long.MAX_VALUE, SIMULATE, actor)` because RS doesn't
expose a direct getAmount method.

The only fragile bit is reading the `grid` field reflectively. If RS ever
adds a public getter (e.g. `menu.getGrid()`) or renames the field, swap the
reflective `gridOf` for a direct call.

Coverage: all standard grid menus (regular, crafting, pattern, wireless,
portable) inherit from `AbstractGridContainerMenu` so the `instanceof` check
in `matches` covers them all.

### 5.4 Gradle is pinned to 8.10.2

`gradle/wrapper/gradle-wrapper.properties` pins Gradle 8.10.2 deliberately.
Gradle 9.x ships Groovy 4, and the NeoForge ModDevGradle plugin (1.x) was
compiled against Groovy 3 — incompatible. If IntelliJ regenerates the
wrapper with a 9.x version, change it back.

### 5.5 Version pins drift fast

`gradle.properties` holds pins for minecraft, neoforge, jei, ae2, rs.
Bump them together when needed; check the corresponding CurseMaven file ID
each time. Vendor links:

- NeoForge: https://projects.neoforged.net/neoforged/neoforge
- JEI / AE2 / RS / guideme: each mod's CurseForge file page has a
  "Curse Maven Snippet" panel that gives the exact dep string.

---

## 6. Build & test

```bash
# From the project root (D:\Projects\JackItToMe)
./gradlew build          # produces build/libs/JackItToMe-neoforge-1.21.1-0.2.0.jar
./gradlew runClient      # launches a dev MC client with the mod loaded
./gradlew runServer      # dedicated server
```

JEI, AE2, and RS are pulled at runtime via CurseMaven. Guideme is pulled as
an AE2 transitive (it's the AE2 in-game documentation library).

### Smoke test

1. New creative world.
2. Place a chest, put some items in it (oak planks work great).
3. Open the chest.
4. Hover an item — either in the chest, your inventory, or JEI's sidebar.
5. Press P.
6. Expected log line: `[JackItToMe] Jacking 1x X from open container.`
7. One of that item appears in your inventory; chest count drops by one.

If step 5 produces no log: keybind not detected. Check `KeyBindings.JACK_HOVERED`
was registered (the mod-bus event subscriber). The keycode for P is 80;
the log should show `Jack key pressed (keyCode=80)` if you re-add the
verbose log line.

If step 6 logs but no item moves: server-side issue. Check `PullHandler` log
output for "no ItemSource matched" or feedback messages.

---

## 7. Things to deliberately not do

- Do *