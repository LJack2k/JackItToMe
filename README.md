# JackItToMe

A NeoForge 1.21.x mod that pulls items from whatever inventory you have open
into your own — either one item at a time via a keybind, or a whole recipe's
worth via a button on JEI's recipe screen.

## Two ways to grab items

### 1. Hover-and-press: pull items

Hover the cursor over **any item** in any open screen and press **P**. One of
that item moves into your inventory, sourced from whatever container is open
behind the cursor.

Modifier keys change how much you pull:

| Modifier        | Effect                                  |
| --------------- | --------------------------------------- |
| (none)          | One item                                |
| **Shift+P**     | One full stack (up to 64)               |
| **Ctrl+P**      | As much as fits in your inventory       |

Ctrl beats Shift if you hold both.

**Autocraft escalation:** if the hovered item isn't in stock but your open
AE2/RS network has a pattern for it, pressing P opens the native autocraft
popup pre-filled with the amount your modifier asked for (1 / 64 / a lot).
This works seamlessly whether you're hovering JEI's sidebar, a bookmark, or
a slot inside a recipe view.

Works on:

- Vanilla container slots (chest, barrel, shulker, etc.)
- Items in the JEI right-side ingredient list
- Items in the JEI left-side bookmarks
- Items in the JEI bottom-left recipe history
- Slots inside a JEI recipe view — including the cycling "any planks" tag
  slots, where all variants are considered and whichever you actually have
  is what gets pulled

### 2. Per-recipe button: start crafting (and optionally pull what's ready)

While viewing any recipe in JEI, a chest-icon **J** button appears next to
JEI's own bookmark/+ buttons. Hover it to preview the state of each slot,
then click to act on the recipe.

- **Hover** the button: each input slot is checked against your open
  storage. Refreshes every 750ms while you stay on the button.
  - **Red overlay** = missing, and no AE2/RS pattern can produce it.
  - **Green overlay** = missing, but your network can autocraft it.
  - **No overlay** = in stock.
- **Click** the button:
  - If every ingredient is in stock (no shortage): pulls them all into
    your inventory. The straightforward case.
  - If any ingredient is missing: triggers autocraft popups (one after
    the next) for the missing-but-craftable ones. **Does not pull
    anything** — even items that are in stock stay in storage until
    you Shift+click separately.
- **Shift+Click** the button: always pulls every in-stock ingredient,
  **and** triggers autocraft popups for any missing-but-craftable ones.

The rule in one sentence: Shift is the "always pull" modifier. Plain click
pulls only when there's nothing to autocraft; otherwise it leaves your
inventory alone so you can review the autocraft popups without committing.

Where items come from for both modes:

- Any vanilla `AbstractContainerMenu` open behind the cursor (chest, barrel,
  shulker, the player's own inventory)
- Applied Energistics 2 ME networks (any terminal-shaped menu)
- Refined Storage 2 grids (normal, crafting, pattern, wireless, portable)

## Install

Drop the built jar into your `mods/` folder alongside:

- **NeoForge 1.21.1** (≥ 21.1.181) — required
- **JEI** ≥ 19.0.0 — required (client-side)
- **AE2** ≥ 19.0.0 — optional, enables the ME-network source
- **Refined Storage 2** ≥ 2.0.0 — optional, enables the RS-grid source

Without AE2 or RS the mod still works for vanilla containers; with either or
both installed, the corresponding source activates automatically.

## Configuring

Rebind the keybind from **Options → Controls → JackItToMe → Jack hovered
item**. Default is **P**.

No other configuration needed.

## Building from source

Requires JDK 21. From the project root:

```
./gradlew build
```

The output lands at `build/libs/JackItToMe-neoforge-1.21.1-VERSION.jar`. To
launch a dev client with the mod loaded:

```
./gradlew runClient
```

First run downloads Minecraft, NeoForge, JEI, AE2, and RS via CurseMaven.
Subsequent launches are cached and start in ~30 seconds.

## How it works (in a paragraph)

The keybind and the button both send a `PullIngredientsPayload` (with
`pullAvailable` + `triggerAutocraft` flags) to the server. The server
resolves the player's open menu against an ordered `ItemSource` registry
(AE2 and RS register at higher priority than the vanilla container
fallback). It simulates the pull first to compute per-ingredient shortfalls
against the source's actual stock; then, if `pullAvailable`, extracts the
in-stock portion via the source's official API (so AE2 security stations,
RS access modes, vanilla `mayPickup`, and modded slot restrictions are all
respected). For each ingredient still short and reported as craftable by
the source, the server queues a popup via `AutocraftChainPayload`; the
client opens them one at a time, advancing on the close of each popup
flow. A `JackFeedbackPayload` carries the list of items actually moved
(one staggered fan-out animation per unique pulled type), plus an optional
failure item for the red-shake. For the button's hover preview the client
sends a `CheckAvailabilityPayload` which the server answers with a
per-slot `(shortage, craftable)` pair; `JackPullButtonController.drawExtras`
paints red or green overlays from that via JEI's universal recipe-button
factory hook (no per-recipe-type decorator registration needed).

## License

MIT — see `LICENSE`.

## Architecture notes for contributors

See `AGENTS.md` for a brief on the layout, design decisions, fragile bits to
watch for, and the smoke-test flow.
