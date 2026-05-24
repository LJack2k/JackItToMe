# Local mod jars

Drop mod jars here for mods that don't publish to a public Maven repo.

## Required for build

- `appliedenergistics2-19.2.17.jar` (AE2 — get from
  https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2
  or https://modrinth.com/mod/applied-energistics-2)

The exact filename matters — it must match what `build.gradle` references.
If you use a different AE2 version, update `ae2_version` in
`gradle.properties` to match.

## Optional

- `refinedstorage-neoforge-2.0.0.jar` — only needed if you re-enable the RS
  compileOnly dep in `build.gradle`. The reflective stubs in
  `RsItemSource.java` don't require it at compile time.

## Why this folder exists

AE2 19.2+ stopped publishing to modmaven.dev. CurseForge/Modrinth jars are
the only source, and Gradle can consume them through a flatDir repository
pointed at this directory.
