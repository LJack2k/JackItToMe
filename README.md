# JackItToMe

A NeoForge 1.21.x mod that pulls items from whatever inventory you have open
into your own — either one item at a time via a keybind, or a whole recipe's
worth via a button on JEI's recipe screen.

## Two ways to grab items

### 1. Hover-and-press: pull one item

Hover the cursor over **any item** in any open screen and press **P**. One of
that item moves into your inventory, sourced from whatever container is open
behind the cursor.

Works on:

- Vanilla container slots (chest, barrel, shulker, etc.)
- Items in the JEI right-side ingredient list
- Items in the JEI left-side bookmarks
- Items in the JEI bottom-left recipe history
- Slots inside a JEI recipe view — including the cycling "any planks" tag
  slots, where all variants are considered and whichever you actually have
  is what gets pulled

### 2. Per-recipe button: pull all ingredients at once

While viewing any recipe in JEI, a chest-icon **J** button appears next to
JEI's own bookmark/+ buttons. Hover it to preview which slots can be
fulfilled; click to pull every ingredient in one go.

- **Hover** the button: each input slot the recipe needs is checked against
  your open storage in order (top-row first, left-to-right). Slots that
  can't be filled get a translucent red overlay. Refreshes every 750ms while
  you stay on the button, so the preview tracks live storage changes.
- **Click** the button: if every slot is fulfilled, all ingredients move
  into your inventory in one packet.
- **Click with a shortage**: blocked by default — you'd be wasting a click
  for a recipe you can't actually craft. **Hold Shift while clicking** to
  override and pull whatever's available.

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

The keybind and the button both send a `PullIngredientsPayload` to the
server. The server resolves the player's open menu against an ordered
`ItemSource` registry (AE2 and RS register at higher priority than the
vanilla container fallback). For each ingredient the most-abundant
acceptable variant is selected, items are extracted, and they land in the
player's inventory. A `JackFeedbackPayload` comes back with the actual
extracted item; the client renders either a falling-into-hotbar success
animation or a red-shake failure shake. For the button's hover preview the
client also sends a `CheckAvailabilityPayload` which the server answers with
a per-slot shortage boolean list; an `IRecipeCategoryDecorator` paints the
red overlays from that.

## License

MIT — see `LICENSE`.

## Architecture notes for contributors

See `AGENTS.md` for a brief on the layout, design decisions, fragile bits to
watch for, and the smoke-test flow.
