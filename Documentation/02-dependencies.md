# Mod dependencies and version resolution

How to add a dependency on another mod (JEI, AE2, RS, Create, etc.) when
you don't know the right Maven coordinates. The Minecraft modding
ecosystem has fractured Maven publishing across many vendors, and most
mods don't publish to a single canonical location. This is the
workflow that works.

## Primary tool: CurseMaven

[CurseMaven](https://www.cursemaven.com) is a public proxy that serves
**any file on CurseForge** through a single Maven repository. It's the
correct first stop for almost any mod dependency.

Add the repo to `build.gradle`:

```groovy
repositories {
    maven {
        name = 'CurseMaven'
        url = 'https://www.cursemaven.com'
        content { includeGroup 'curse.maven' }  // important — see below
    }
}
```

The `content { includeGroup 'curse.maven' }` block restricts CurseMaven
lookups to artifacts in the `curse.maven` group. Without it, Gradle
hits CurseMaven for every dependency in your project, slowing builds
and being needlessly rude to their infrastructure.

## Finding the coordinates

Every CurseForge **file page** has a panel called **"Curse Maven Snippet"**.
It gives you the exact dependency string. Format:

```
curse.maven:<slug>-<projectId>:<fileId>
```

Where:
- `slug` is the mod's URL slug (`applied-energistics-2`, `jei`, etc.)
- `projectId` is a numeric ID stable across versions
- `fileId` is per-release; changes whenever a new file is uploaded

Example (real coordinates from JackItToMe's setup):

```groovy
compileOnly "curse.maven:jei-238222:7420587"
runtimeOnly "curse.maven:jei-238222:7420587"

compileOnly "curse.maven:applied-energistics-2-223794:7027323"
runtimeOnly "curse.maven:applied-energistics-2-223794:7027323"

compileOnly "curse.maven:refined-storage-243076:7039043"
runtimeOnly "curse.maven:refined-storage-243076:7039043"
```

## Convention: file IDs in `gradle.properties`

Don't hardcode file IDs in `build.gradle` — store them in
`gradle.properties` alongside human-readable version numbers:

```properties
jei_version=19.27.0.340
jei_curse_file_id=7420587

ae2_version=19.2.17
ae2_curse_file_id=7027323
guideme_curse_file_id=7127444    # AE2's required transitive

rs_version=2.0.0
rs_curse_file_id=7039043
```

Then reference them by name:

```groovy
compileOnly "curse.maven:jei-238222:${jei_curse_file_id}"
```

When bumping a mod version: update both the human-readable string and
the file ID. They must match — the file ID is what Gradle actually
resolves; the human-readable string is just for your benefit when
debugging.

## compileOnly vs runtimeOnly

For most mod deps you want **both**:

```groovy
compileOnly "curse.maven:jei-238222:${jei_curse_file_id}"
runtimeOnly "curse.maven:jei-238222:${jei_curse_file_id}"
```

- `compileOnly` puts the mod's classes on your compile classpath so
  your code can `import` them. The mod is *not* bundled into your jar.
- `runtimeOnly` makes the mod available at runtime in the dev client
  (when you run `./gradlew runClient`).

If you only set `compileOnly`, your code compiles but the mod isn't
loaded at runtime — `runClient` runs without it, and you can't
test integration. Both is the safe default.

`implementation` (which bundles the dep into your jar) is **wrong** for
mod deps. You'd end up with conflicting class versions when the player
also has the real mod installed.

## Transitive dependencies (the guideme trap)

Some mods declare dependencies on other mods, but Gradle's
CurseMaven integration **doesn't resolve transitives**. Adding AE2
doesn't automatically add `guideme` (AE2's documentation library).

Symptom: NeoForge boots and complains:

> Mod ae2 requires guideme 21.1.1 or above. Currently, guideme is not installed.

Fix: add the transitive explicitly.

```groovy
runtimeOnly "curse.maven:applied-energistics-2-223794:${ae2_curse_file_id}"
runtimeOnly "curse.maven:guideme-1173950:${guideme_curse_file_id}"
```

You learn about transitives by trying to launch and reading the error
message. There's no upfront list — each mod author decides what they
need.

Common transitives to expect for popular mods:

- **AE2** → guideme
- **Create** → Flywheel, Ponder, Reach-Entity-Attributes (on some MC versions)
- **Mekanism** → no major transitives (self-contained)
- **Patchouli-using mods** → patchouli
- **GeckoLib-using mods** → geckolib
- **Curios-using mods** → curios

## Backup: when CurseMaven doesn't have what you need

Some mod versions aren't on CurseForge (early dev builds, prerelease
testing, mods that withdrew from CurseForge). For those:

**Option 1: Modrinth Maven.** Modrinth also exposes a Maven:
`https://api.modrinth.com/maven/`. Coordinates are
`maven.modrinth:<slug>:<version>`. Add the repo:

```groovy
maven {
    name = 'Modrinth'
    url = 'https://api.modrinth.com/maven'
    content { includeGroup 'maven.modrinth' }
}
```

**Option 2: BlameJared Maven.** Hosts JEI, Crafttweaker, and some other
common mods.

```groovy
maven {
    name = 'BlameJared'
    url = 'https://maven.blamejared.com'
}
```

**Option 3: Local `libs/` folder.** Download the jar manually, drop it
into a `libs/` folder, reference via `flatDir`:

```groovy
repositories {
    flatDir { dirs 'libs' }
}

dependencies {
    compileOnly files('libs/whatever-mod-1.2.3.jar')
}
```

Used for mods that aren't on any public Maven (rare but happens for
early-development or private builds).

## Verifying you got the right version

After updating coordinates, run `./gradlew dependencies | head -50`.
You should see the resolved deps with file sizes. If something says
"Could not find", the file ID is wrong or the artifact doesn't exist
at that version.

Common Gradle error format:

```
> Could not find <group>:<artifact>:<version>.
  Searched in the following locations:
    - https://www.cursemaven.com/<group>/<artifact>/<version>/...
```

The URL it tried is the diagnostic — if you can't browse to that URL
in a web browser and see the file, the coordinates are wrong.

## Optional dependencies

If the mod compat is optional (e.g. your mod works without AE2 but adds
extra features when AE2 is installed), declare in `neoforge.mods.toml`:

```toml
[[dependencies.${mod_id}]]
modId = "ae2"
type = "optional"
versionRange = "[19.0.0,)"
ordering = "AFTER"
side = "BOTH"
```

Guard the actual compat code with `ModList.get().isLoaded("ae2")`:

```java
if (ModList.get().isLoaded("ae2")) {
    Ae2ItemSource.register();  // touches AE2 classes safely
}
```

Class loading is lazy: as long as you never *call* a method that
references AE2 classes when AE2 isn't loaded, the JVM won't try to
resolve them. The `ModList.isLoaded` check is the gate. See
[05-mod-compat.md](./05-mod-compat.md) for the full pattern.

## Quick reference: known mod coordinates

These are stable across releases — only the file ID changes per version.

| Mod | CurseMaven slug-projectID | Notes |
| --- | --- | --- |
| JEI | `jei-238222` | Required client-side for the recipe UI features |
| Applied Energistics 2 | `applied-energistics-2-223794` | Needs guideme transitive |
| guideme | `guideme-1173950` | AE2's docs library |
| Refined Storage | `refined-storage-243076` | RS 2.x for 1.21 |
| Create | `create-328085` | Has Flywheel transitive |
| Mekanism | `mekanism-268560` | Mostly self-contained |
| Curios | `curios-309927` | Common transitive |
| GeckoLib | `geckolib-388172` | Common transitive |
| Patchouli | `patchouli-306770` | Documentation framework |
| The One Probe | `the-one-probe-245211` | Tooltip-on-look mod |

Look up the file ID for the specific version you want via each mod's
CurseForge "Files" tab → click the file → "Curse Maven Snippet".
