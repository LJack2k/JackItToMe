# Bootstrapping a new NeoForge mod

What to do when starting a new NeoForge 1.21.x mod from zero. Order
matters here — get these in place before writing any Java, because
missteps cost compounding time later.

## Java version

Minecraft 1.21 requires **JDK 21**. Earlier or later JDKs will silently
fail in subtle ways (compile errors that look like Gradle issues, or
runtime crashes during NeoForge bootstrap).

Verify with `java -version` showing `21.x.x`. Use Temurin or Microsoft's
build; both work. Set IntelliJ's project SDK to 21 (`File → Project
Structure → Project SDK`).

## Gradle version

**Pin to Gradle 8.10.2.** Do not let IntelliJ regenerate the wrapper
with 9.x — it ships Groovy 4, and the NeoForge ModDevGradle plugin
(1.0.x line) was compiled against Groovy 3. Symptom of the mismatch:
`'java.util.List org.codehaus.groovy.runtime.DefaultGroovyMethods.collect(java.util.Collection, groovy.lang.Closure)'`
appearing during build.

The wrapper config:

```properties
# gradle/wrapper/gradle-wrapper.properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.2-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

In IntelliJ, set **Settings → Build, Execution, Deployment → Build Tools
→ Gradle → Distribution: Wrapper**. Confirms IDE uses the pinned
version, not its bundled Gradle.

## Project structure

For a single-loader, single-Minecraft-version mod, the structure is:

```
<mod-name>/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradle/wrapper/
│   ├── gradle-wrapper.properties
│   └── gradle-wrapper.jar
├── gradlew
├── gradlew.bat
├── .gitignore
└── src/main/
    ├── java/<package>/...
    └── resources/
        ├── META-INF/neoforge.mods.toml
        ├── pack.mcmeta
        └── assets/<modid>/lang/en_us.json
```

If you want a multi-loader or multi-version setup later, see
[07-distribution.md](./07-distribution.md) — but **start single-target**.
The complexity tax of Architectury / multi-version subprojects is real
and isn't worth paying until you actually have a second target.

## Minimum `settings.gradle`

```groovy
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven { url = 'https://maven.neoforged.net/releases' }
    }
}

plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
}

rootProject.name = '<modid>'
```

## Minimum `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true

mod_id=<modid>
mod_name=<DisplayName>
mod_license=MIT
mod_version=0.1.0
mod_group_id=<reverse.dns.group.modid>
mod_authors=<Author>
mod_description=<one-line description>

minecraft_version=1.21.1
minecraft_version_range=[1.21.1,1.22)
neo_version=21.1.193
neo_version_range=[21.1.0,)
loader_version_range=[4,)
```

The dependency version pins (JEI, AE2, etc.) come from CurseMaven — see
[02-dependencies.md](./02-dependencies.md). Don't fill them in
speculatively; let the CurseMaven snippet panel give you exact values.

**ASCII only in `gradle.properties`.** Java's `Properties` class reads it
as ISO-8859-1. Any UTF-8 character (em-dashes, smart quotes, accented
letters) gets corrupted. Use `—` Unicode escapes if you absolutely
need them; in practice, just stick to ASCII hyphens and double-dashes.

## Minimum `build.gradle`

```groovy
plugins {
    id 'java-library'
    id 'maven-publish'
    id 'net.neoforged.moddev' version '1.0.20'
}

version = mod_version
group = mod_group_id

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
}

base {
    archivesName = "${mod_name}-neoforge-${minecraft_version}"
}

repositories {
    mavenCentral()
    maven {
        name = 'NeoForged'
        url = 'https://maven.neoforged.net/releases'
    }
    maven {
        name = 'CurseMaven'
        url = 'https://www.cursemaven.com'
        content { includeGroup 'curse.maven' }
    }
}

neoForge {
    version = neo_version

    runs {
        client { client() }
        server { server(); programArgument '--nogui' }
        data {
            data()
            programArguments.addAll '--mod', mod_id, '--all',
                '--output', file('src/generated/resources/').getAbsolutePath()
        }
    }

    mods {
        "${mod_id}" {
            sourceSet sourceSets.main
        }
    }
}

dependencies {
    // Mod deps go here. See 02-dependencies.md for CurseMaven coordinates.
}

tasks.named('processResources', ProcessResources).configure {
    var replaceProperties = [
        minecraft_version       : minecraft_version,
        minecraft_version_range : minecraft_version_range,
        neo_version             : neo_version,
        neo_version_range       : neo_version_range,
        loader_version_range    : loader_version_range,
        mod_id                  : mod_id,
        mod_name                : mod_name,
        mod_license             : mod_license,
        mod_version             : mod_version,
        mod_authors             : mod_authors,
        mod_description         : mod_description
    ]
    inputs.properties replaceProperties

    filesMatching(['META-INF/neoforge.mods.toml']) {
        expand replaceProperties
    }
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}
```

Note: `withSourcesJar()` generates a `*-sources.jar` alongside the main
jar. Useful for distribution; required for ModDevGradle to behave.

## Minimum `.gitignore`

```
# Gradle
.gradle/
build/
out/
src/generated/

# NeoForge dev environment
run/
runs/

# IDE
.idea/
*.iml
.vscode/
.project
.classpath
.settings/

# OS
.DS_Store
Thumbs.db
```

`.idea/` is gitignored deliberately — IDE-specific project state
shouldn't be in the repo. IntelliJ regenerates everything from
`build.gradle` on import. Anyone with stale state can delete `.idea/`
and reopen to fix.

## Minimum `neoforge.mods.toml`

```toml
modLoader = "javafml"
loaderVersion = "${loader_version_range}"
license = "${mod_license}"

[[mods]]
modId = "${mod_id}"
version = "${mod_version}"
displayName = "${mod_name}"
authors = "${mod_authors}"
description = '''${mod_description}'''

[[dependencies.${mod_id}]]
modId = "neoforge"
type = "required"
versionRange = "${neo_version_range}"
ordering = "NONE"
side = "BOTH"

[[dependencies.${mod_id}]]
modId = "minecraft"
type = "required"
versionRange = "${minecraft_version_range}"
ordering = "NONE"
side = "BOTH"
```

`${...}` placeholders are expanded by `processResources` from
`gradle.properties`. Don't hardcode versions here unless you want to
manually keep them in sync.

## Mod main class

```java
package <package>;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(<MainClass>.MODID)
public final class <MainClass> {
    public static final String MODID = "<modid>";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public <MainClass>(IEventBus modBus, Dist dist) {
        // Register event listeners, packets, etc. here.
        // Client-only initialization gates on dist.isClient().
    }
}
```

NeoForge 1.21 uses **constructor injection** for the mod main —
`@Mod` no longer wraps a no-arg constructor. The `IEventBus` is the
mod-bus (use for registration events); `Dist` tells you which side
you're loading on.

## First run

After all the above:

```bash
./gradlew runClient
```

This downloads Minecraft, NeoForge, generates run config, launches the
dev client. First run takes ~5 minutes (asset download). Subsequent
launches are ~30 seconds.

Common failures and what they mean:

- **`Could not resolve org.parchmentmc.data:parchment-...`** — Parchment
  mappings repo not configured, or version doesn't exist. Disable the
  `parchment { }` block in `neoForge { }` (it's optional — mappings get
  uglier but everything works).

- **`bad config line 1 in file .git/config`** — Filesystem race in
  certain WSL/sandbox setups. Doesn't matter for `runClient`. If git
  commands are also failing, this is FUSE inconsistency — restart Gradle.

- **`fatal: could not read Password for 'https://...github.com'`** — Git
  credential issue at push time. Doesn't affect builds.

## IDE setup quick reference

After all files are in place:

1. IntelliJ → **Open** → select project folder (the one with `build.gradle`).
2. Trust the project when prompted.
3. Wait for the Gradle sync (status bar bottom-right).
4. After sync, the **Gradle tool window** on the right shows tasks.
   Navigate Tasks → neoforge → `runClient`. Double-click to launch.
5. If the run config doesn't appear: `Gradle → Reload All Gradle Projects`.

For first commits: see [07-distribution.md](./07-distribution.md) for
git setup with multi-account credentials (work + personal accounts in
the same Credential Manager).
