# Contributing to JackItToMe

Technical notes for building, understanding, and extending the mod. For the
user-facing feature overview, see [README.md](README.md).

## Building from source

Requires **JDK 21**. The mod lives in the `neoforge/` subproject.

```sh
./gradlew build
```

Output: `neoforge/build/libs/JackItToMe-neoforge-1.21.1-<version>.jar`
(plus a `-sources` jar). The first build downloads Minecraft, NeoForge, and
the integration dependencies (JEI, EMI, REI, AE2, RS) via their mavens;
subsequent builds are cached.

### Running a dev client

```sh
./gradlew :neoforge:runClient
```

JEI, EMI, and REI are all compiled against, but only **one** recipe viewer
should be on the runtime classpath at a time (JEI and REI conflict, and the
viewer bridge picks the first one it finds). Select which one the dev client
loads with the `recipeViewer` property:

```sh
./gradlew :neoforge:runClient -PrecipeViewer=jei    # default
./gradlew :neoforge:runClient -PrecipeViewer=emi
./gradlew :neoforge:runClient -PrecipeViewer=rei
./gradlew :neoforge:runClient -PrecipeViewer=none   # no viewer (container slots only)
```

This flag only affects the dev runtime classpath — it has **no effect on the
published jar**, whose compatibility comes purely from the optional
dependencies declared in `neoforge.mods.toml`.

## Project layout

```
settings.gradle          root project; includes the 'neoforge' subproject
build.gradle             minimal root (nothing builds here)
neoforge/                the mod
  build.gradle           deps, run configs, icon rasterization
  src/main/java/...      mod code (see packages below)
  src/main/resources/    mods.toml, lang, icons, plugin service files
Documentation/           numbered deep-dive guides (bootstrap → distribution)
AGENTS.md                contributor brief: design decisions, fragile bits
```

The mod was structured as a subproject to leave room for additional loaders
(e.g. a `fabric/` subproject) without restructuring later.

### Key packages (`nl.ljack2k.jackittome`)

- `client/` — keybind handling, the recipe-viewer bridge, availability cache,
  tooltip/overlay helpers, animations
- `jei/`, `emi/`, `rei/` — one package per recipe viewer integration
- `network/` — the `CustomPacketPayload` records exchanged client↔server
- `server/` — pull/availability/autocraft request handlers
- `source/` — the `ItemSource` abstraction; `compat/ae2/` and `compat/rs/`
  provide the AE2 and Refined Storage sources

## How it works

### Recipe-viewer abstraction

`client/RecipeViewerBridge` isolates the keybind/event code from any
viewer-specific class, so a missing viewer never causes a
`ClassNotFoundException`. `ClientEvents.initBridge()` picks the first loaded
viewer (JEI > EMI > REI), or a no-op bridge if none is present.

The recipe-screen pull button is added through each viewer's **native** widget
API — never an injected vanilla button (custom recipe screens swallow those
clicks):

- **JEI** — `IRecipeButtonControllerFactory` (universal, all recipe types)
- **EMI** — `EmiRecipeDecorator` (runs per recipe layout)
- **REI** — `CategoryExtensionProvider` registered on every category

### The pull flow

The keybind and the button both send a `PullIngredientsPayload`
(`pullAvailable` + `triggerAutocraft` flags) to the server. The server
resolves the player's open menu against an ordered `ItemSource` registry (AE2
and RS register above the vanilla-container fallback), simulates the pull to
compute per-ingredient shortfalls against real stock, then — if
`pullAvailable` — extracts the in-stock portion through the source's official
API (so AE2 security, RS access modes, and vanilla `mayPickup` are all
respected).

For each ingredient still short but reported craftable, the server queues a
popup via `AutocraftChainPayload`; the client opens them one at a time,
advancing as each popup flow closes. A `JackFeedbackPayload` carries the items
actually moved (one staggered animation per type) plus an optional failure
item for the red-shake.

### Hover preview (button overlays + tooltip)

On button hover the client sends a `CheckAvailabilityPayload`; the server
answers with a per-slot `(shortage, craftable)` pair stored in the shared
`AvailabilityCache`. `PullTooltipBuilder` and `PullHoverPoller` (both
viewer-agnostic) turn that into the tooltip text and the red/green slot
overlays. JEI and REI draw the overlays on their input slots; EMI shows the
counts in the tooltip only, because its decorator API doesn't expose recipe
slot positions.

### Notable gotchas

- **REI plugin discovery** on NeoForge uses the `@REIPluginClient` annotation,
  not a ServiceLoader file.
- **REI hovered-slot** detection walks `screen.children()` for the hovered
  `Slot`; `ScreenRegistry.getFocusedStack` returns null for recipe-display
  slots in current REI.
- **RS's autocraft preview is menu-backed**, so handing it a recipe-viewer
  screen as its parent caused an infinite open/close loop. The fix returns it
  to the *host container screen* (`RecipeViewerBridge.getHostContainerScreen()`).

See `AGENTS.md` and the `Documentation/` guides for deeper detail.

## Adding another recipe viewer

1. Add the viewer as a `compileOnly` dependency in `neoforge/build.gradle` and
   gate a `runtimeOnly` entry behind the `recipeViewer` property.
2. Implement `RecipeViewerBridge` in a new package and register the pull button
   through that viewer's native widget API.
3. Add the viewer to `ClientEvents.initBridge()` and to the optional
   dependencies in `neoforge.mods.toml`.

## Releasing

Pushing a `v*` git tag triggers `release.yml` (builds the jar + creates a
GitHub Release), which in turn triggers the CurseForge and Modrinth publish
workflows. A normal commit or branch push publishes nothing. See
`Documentation/07-distribution.md` for the full release process.

## License

MIT — see [LICENSE](LICENSE).
