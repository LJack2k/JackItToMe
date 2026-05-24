# JackItToMe

A small NeoForge 1.21.x mod that lets you grab one of any item into your
inventory just by hovering over it and pressing **P**.

## What it does

When a screen is open (a chest, your inventory, an AE2 ME terminal, an RS
grid, a JEI recipe view, anywhere) and your cursor is over an item, press
**P**. One of that item lands in your hotbar, pulled from whatever container
is open behind the cursor.

It removes the modpack player's daily grind of: bookmark item → close JEI →
drag item out of terminal → repeat.

### Where the hover works

- Any vanilla container slot (chest, barrel, shulker, etc.)
- The JEI right-side ingredient list
- The JEI left-side bookmarks
- The JEI bottom-left recipe history
- Inside a JEI recipe view — even on cycling "any planks" type slots, all
  variants are considered and the most-abundant one in your storage is pulled

### Where the items can come from

- Any vanilla `AbstractContainerMenu` open behind the cursor
- Applied Energistics 2 ME networks (any terminal-shaped menu)
- Refined Storage 2 grids (normal, crafting, pattern, wireless, portable)

## Install

Drop the built jar into your `mods/` folder alongside:

- NeoForge 1.21.1 (≥ 21.1.181)
- JEI (required, client-only)
- AE2 and/or Refined Storage (optional)

## Building from source

Requires JDK 21. From the project root:

```
./gradlew build
```

The output jar lands in `build/libs/`. To launch a dev client with the mod
loaded:

```
./gradlew runClient
```

This downloads Minecraft, NeoForge, JEI, AE2, and RS via CurseMaven on the
first run.

## Configuring

Rebind the key from `Options → Controls → JackItToMe → Jack hovered item`.
Default is **P**.

## License

MIT — see `LICENSE`.
