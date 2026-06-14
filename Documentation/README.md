# Modding guides for future projects

You're an AI agent helping build a NeoForge Minecraft mod. These documents
capture the patterns, gotchas, and workflows we learned while building
JackItToMe — the kind of stuff that would otherwise take a day to
rediscover via Stack Overflow, JEI bytecode, and trial and error.

Each document is self-contained. Pick the one matching the problem in
front of you. **Read the index here first** to know which doc to load.

## Document index

| Doc | When to read it |
| --- | --- |
| [01-project-bootstrap.md](./01-project-bootstrap.md) | Starting a new NeoForge mod from scratch. Covers Gradle wrapper version, ModDevGradle plugin setup, JDK version, IDE project import, and the `gradle.properties` / `settings.gradle` minimum viable layout. |
| [02-dependencies.md](./02-dependencies.md) | Adding a mod dependency (JEI, AE2, RS, etc.) when you don't know the right Maven coordinates. Covers CurseMaven, version sleuthing, transitive deps like guideme, and the `mods.toml` declaration. |
| [03-neoforge-essentials.md](./03-neoforge-essentials.md) | The core NeoForge 1.21 APIs: `neoforge.mods.toml`, packet payloads, stream codecs, registration events, side-aware code. The minimum you need to know to ship a mod. |
| [04-jei-integration.md](./04-jei-integration.md) | Anything JEI-related. The proper API patterns and the long list of undocumented quirks that bit us repeatedly (button factories, decorators, modifier keys, slot view identity, texture size defaults). |
| [05-mod-compat.md](./05-mod-compat.md) | Integrating with other mods: AE2, Refined Storage, the general "optional dependency that gracefully no-ops if missing" pattern. |
| [06-ui-and-rendering.md](./06-ui-and-rendering.md) | Drawing on screens — `GuiGraphics`, pose stack translation, animations, icon textures, and the SVG → PNG build automation pattern. |
| [07-distribution.md](./07-distribution.md) | Publishing the mod. CurseForge upload via GitHub Actions, version conventions, jar naming, free-tier limits, multi-version support patterns. |
| [08-debugging.md](./08-debugging.md) | When something doesn't work and you don't know why. Diagnostic logging patterns, jar inspection from a sandbox, the "look at the actual bytecode" workflow, common gotchas. |

## General philosophy

Things that aren't in any single document but apply across all of them:

**Inspect the actual jar, not your memory.** Mod APIs change between
versions and between mods. When in doubt, extract the jar with `unzip`
and read class names and method signatures via `strings -n 4`. See
[08-debugging.md](./08-debugging.md) for the exact workflow.

**Diagnostic logging is faster than guessing.** When something silently
fails, add `LOGGER.info(...)` statements at every step of the chain.
Remove (or demote to debug) once the issue is found. Pattern repeated
constantly during JackItToMe development.

**Test the failure path.** Build a feature, then break the input
deliberately (empty container, missing dep, network down). Surprises
during the happy-path test are normal; surprises during failure tests
are the cost of skipping them.

**Don't hardcode lists if a registry exists.** We almost shipped a
"works on 12 vanilla recipe types only" feature because the JEI API
seemed to require per-type registration. The right answer (use the
universal button factory) was hiding in JEI's own code. When tempted
to enumerate hardcoded types of anything, first check whether the
framework offers an "all types" hook.

**Read the user, not just the request.** When a user says "make
something work this way," check whether the request actually solves
their underlying problem. Often a simpler approach exists. Sometimes
their suggestion is the right one and your instinct to overcomplicate
is wrong.

## Project conventions used in these docs

- All path examples use forward slashes (`/`) even on Windows.
- Java code examples target Java 21 and NeoForge 1.21.x. Earlier
  versions may differ — APIs around `Ingredient`, `ItemStack`
  components, and `StreamCodec` are particularly version-sensitive.
- Gradle examples are Groovy DSL (`build.gradle`, not `build.gradle.kts`).
  Translation to Kotlin DSL is mostly mechanical.
- When a doc says "see X.java", it refers to a file in the JackItToMe
  source tree. The `neoforge/src/main/java/nl/ljack2k/jackittome/`
  prefix is implied.
