# JackItToMe

A NeoForge 1.21.x mod that pulls items from whatever inventory you have open
into your own — either one item at a time with a keybind, or a whole recipe's
worth with a button on your recipe viewer's recipe screen.

Works with **JEI**, **EMI**, or **REI** — install whichever you already use.

📦 **Download:** [CurseForge](https://www.curseforge.com/minecraft/mc-mods/jack-it-to-me) · [Modrinth](https://modrinth.com/mod/jackittome)

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

Works on:

- Vanilla container slots (chest, barrel, shulker, etc.)
- Items in your recipe viewer's item list and favorites/bookmarks
- Slots inside a recipe view — including the cycling "any planks" tag slots,
  where all variants are considered and whichever you actually have is what
  gets pulled

### 2. Per-recipe button: start crafting (and optionally pull what's ready)

While viewing any recipe, a chest-icon **J** button appears on the recipe.
Hover it to preview the state of each slot, then click to act on the recipe.

- **Hover** the button: each input slot is checked against your open storage,
  refreshing every ¾ second while you stay on the button.
  - **Red** = missing, and no AE2/RS pattern can produce it.
  - **Green** = missing, but your network can autocraft it.
  - **Clear** = in stock.
- **Click** the button:
  - If every ingredient is in stock: pulls them all into your inventory.
  - If any ingredient is missing: triggers autocraft popups (one after the
    next) for the missing-but-craftable ones. **Does not pull anything** —
    even in-stock items stay in storage until you Shift+click.
- **Shift+Click**: always pulls every in-stock ingredient **and** triggers
  autocraft popups for any missing-but-craftable ones.

The rule in one sentence: Shift is the "always pull" modifier; plain click
pulls only when there's nothing to autocraft, otherwise it leaves your
inventory alone so you can review the autocraft popups without committing.

## Recipe viewers

Install any **one** of these (or none). The mod adapts to whichever it finds:

| Viewer | Pull button | Hover tooltip | Red/green slot overlays |
| ------ | :---------: | :-----------: | :---------------------: |
| **JEI** | ✅ | ✅ | ✅ |
| **REI** | ✅ | ✅ | ✅ |
| **EMI** | ✅ | ✅ | — (counts shown in the tooltip instead) |

Without any viewer installed the mod still works for the **P** keybind on
vanilla container slots — you just won't get the recipe button or the ability
to pull from a viewer's item list.

## Where items come from

Both modes pull from the menu open behind the cursor:

- Any vanilla container (chest, barrel, shulker, your own inventory)
- **Applied Energistics 2** ME networks (any terminal-shaped menu)
- **Refined Storage 2** grids (normal, crafting, pattern, wireless, portable)

Without AE2 or RS the mod still works for vanilla containers; with either or
both installed, the corresponding source activates automatically — respecting
that storage system's own access/security rules.

## Install

Drop the jar into your `mods/` folder alongside:

- **NeoForge 1.21.1** (≥ 21.1.181) — required
- A recipe viewer — **JEI** (≥ 19), **EMI** (≥ 1.1), or **REI** (≥ 16) —
  recommended (needed for the recipe button and viewer-list pulling)
- **AE2** (≥ 19) — optional, enables the ME-network source
- **Refined Storage 2** (≥ 2.0) — optional, enables the RS-grid source

## Configuring

Rebind the keybind from **Options → Controls → JackItToMe → Jack hovered
item**. Default is **P**. No other configuration needed.

## Links

- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/jack-it-to-me)
- [Modrinth](https://modrinth.com/mod/jackittome)
- [Source & issue tracker on GitHub](https://github.com/LJack2k/JackItToMe)

## Contributing & technical docs

Building from source, how it works under the hood, and the project layout
are in [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT — see [LICENSE](LICENSE).
