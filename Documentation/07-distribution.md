# Distribution: build, version, publish

How to get the mod from your dev machine to actual players. Covers git
setup, jar naming conventions, multi-version branching, GitHub Actions
for CI publishing, CurseForge / Modrinth uploading.

## Jar naming convention

The standard pattern, used by virtually every Minecraft mod on
CurseForge:

```
<ModName>-<loader>-<mcVersion>-<modVersion>.jar
```

Examples: `Create-neoforge-1.21.1-6.0.4.jar`,
`MyMod-fabric-1.20.1-0.3.0.jar`.

Configure in `build.gradle`:

```groovy
base {
    archivesName = "${mod_name}-neoforge-${minecraft_version}"
}

version = mod_version
```

That produces `<mod_name>-neoforge-<minecraft_version>-<mod_version>.jar`
automatically when `gradlew build` runs.

## Versioning

Stick to **SemVer** with the same caveats every mod author lives with:

- `0.x.y` — pre-stable, breaking changes allowed without bumping
  major. Use this until you're comfortable saying "the API is committed."
- `1.0.0` — first stable release. Bumps to `2.0.0` for breaking changes.
- Minor bumps (`0.3.0` → `0.4.0`) for new features.
- Patch bumps (`0.3.0` → `0.3.1`) for bug fixes only.

For a personal mod with no downstream API consumers, you can be loose
with SemVer — it's mostly a hint to users about "is this safe to
auto-update." The strict version of SemVer is for libraries.

## Git workflow

### Initial setup (one time)

```powershell
cd D:\path\to\project
git init -b master
git config user.name "YourGitHubUsername"
git config user.email "your@email.example"
git remote add origin https://YourGitHubUsername@github.com/YourGitHubUsername/Repo.git
```

Note the `YourGitHubUsername@` in the URL. This makes Git Credential
Manager store credentials per-username, allowing multiple GitHub
accounts to coexist on the same Windows machine without
clobbering each other.

### Fixing the executable bit on `gradlew`

When you initialize a project on Windows and push it to GitHub, the
`gradlew` script is committed without the executable bit. On
Linux runners (GitHub Actions), `./gradlew` fails with "Permission
denied."

One-time fix from any machine:

```powershell
git update-index --chmod=+x gradlew
git commit -m "Fix gradlew executable bit"
git push
```

`git update-index --chmod=+x` records the executable bit in git's
index without modifying anything on your Windows filesystem (Windows
ignores it anyway). Future Linux checkouts get the file as executable.

### Multi-account credentials

If you have multiple GitHub accounts (work, personal) and Git
Credential Manager keeps using the wrong one:

1. Put the username in the remote URL: `https://Personal@github.com/...`.
2. GCM will look for a credential matching that username. If none
   exists, it prompts — sign in with that account.
3. Both credentials coexist: `git:https://github.com` for the
   default, `git:https://Personal@github.com` for the username-tagged
   one.

Don't try to set `git config user.name` to fix this — that controls
the **author** label on commits, not authentication. They're separate.

### Tagging releases

```powershell
git tag -a v0.3.0 -m "v0.3.0: short description"
git push --tags
```

The tag is what GitHub Actions watches for (`on: push: tags: ['v*']`).
The annotated tag (`-a`) is preferred over a lightweight tag (`git tag v0.3.0`)
because it carries a message and an author.

If you need to **retag** (move a tag to a different commit):

```powershell
git tag -d v0.3.0                       # delete locally
git push origin :refs/tags/v0.3.0       # delete on remote
git tag -a v0.3.0 -m "v0.3.0"           # re-create at current HEAD
git push --tags
```

Note: re-running a workflow uses the tag's original commit SHA. If you
fixed a bug after pushing a tag, the re-run will check out the broken
commit. To actually run the fix, you must move the tag.

## GitHub Actions CI

Auto-build and auto-publish on tag push. The workflow file:

```yaml
# .github/workflows/release.yml
name: Release

on:
  push:
    tags:
      - 'v*'

permissions:
  contents: write   # for attaching jar to a GitHub Release

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - uses: gradle/actions/setup-gradle@v4

      - name: Build the mod
        run: |
          chmod +x ./gradlew
          ./gradlew build --no-daemon

      - name: Compute version from tag
        id: ver
        run: echo "value=${GITHUB_REF_NAME#v}" >> "$GITHUB_OUTPUT"

      - name: Publish to CurseForge + GitHub Release
        uses: Kir-Antipov/mc-publish@v3.3
        with:
          curseforge-id: REPLACE_WITH_PROJECT_ID
          curseforge-token: ${{ secrets.CURSEFORGE_TOKEN }}

          github-token: ${{ secrets.GITHUB_TOKEN }}

          files: |
            build/libs/!(*-sources).jar

          name: MyMod-neoforge-1.21.1-${{ steps.ver.outputs.value }}.jar
          version: ${{ steps.ver.outputs.value }}
          version-type: release

          loaders: neoforge
          game-versions: 1.21.1
          java: 21

          dependencies: |
            jei(required){curseforge:238222}
            applied-energistics-2(optional){curseforge:223794}

          changelog: |
            See the [release notes on GitHub](https://github.com/User/Repo/releases/tag/${{ github.ref_name }}).
```

### Prerequisites

Before the first run:

1. **CurseForge API token** — generate at
   <https://legacy.curseforge.com/account/api-tokens>. Shown once
   on screen. Copy immediately.

2. **CurseForge project ID** — visit your project page on
   CurseForge. The numeric ID is on the right sidebar under "About
   Project". Replace `REPLACE_WITH_PROJECT_ID` in the workflow with it.

3. **GitHub repo secret** — at
   `https://github.com/User/Repo/settings/secrets/actions`, click
   "New repository secret". Name `CURSEFORGE_TOKEN`, paste the token.

4. **The workflow file on master branch** — commit
   `.github/workflows/release.yml`, push, and GitHub picks it up
   automatically.

`GITHUB_TOKEN` is provided automatically by Actions — never add it
as a secret manually.

### Tag-version-jar synchronization

The convention: tag `v0.3.0` corresponds to `mod_version=0.3.0` in
`gradle.properties`. Three places to keep in sync per release:

1. Update `mod_version` in `gradle.properties`.
2. Commit.
3. Tag the commit with `v<same-version>`.

The workflow extracts the version from the tag name and uses it for
the CurseForge metadata. The jar's actual filename comes from
`gradle.properties` via `archivesName`. If you tag `v0.3.0` but
forget to update `gradle.properties`, the jar is built with the old
version number while CurseForge labels it as the new one — a confusing
mismatch.

A small Gradle task could enforce sync by reading the tag from
`GITHUB_REF_NAME` and writing it into the jar at CI time. Not done in
JackItToMe — just remembering to bump matches the convention well
enough.

## Free-tier CI limits

For **public repos** on GitHub:

- Standard runners (`ubuntu-latest`, `windows-latest`, `macos-latest`):
  unlimited free minutes
- Larger runners: paid
- Artifact storage: 500 MB included; pay per GB above

For **private repos**: 2,000 minutes/month included on the free plan.
A typical mod-release workflow uses 2-3 minutes, so even private
repos get ~600 releases/month before hitting the quota.

Your typical workflow consumes ~3 minutes per release; you'd never
hit any limit on a public repo even with daily releases.

## CurseForge upload mechanics

The Kir-Antipov/mc-publish action handles all the CurseForge API
plumbing. Key parameters:

| Parameter | What it does |
| --- | --- |
| `curseforge-id` | Numeric project ID |
| `curseforge-token` | API token (in repo secret) |
| `files` | Glob for the jar(s) to upload — exclude `*-sources.jar` with `!(*-sources).jar` |
| `name` | Display name on CurseForge (separate from filename) |
| `version` | Version string (drives CurseForge's release tag) |
| `version-type` | `release` / `beta` / `alpha` |
| `loaders` | `neoforge`, `forge`, `fabric`, `quilt` (or multiple) |
| `game-versions` | MC version(s) the file targets |
| `java` | Minimum Java version |
| `dependencies` | Mod deps to declare on CurseForge — see below |
| `changelog` | Plain text or markdown changelog for the release |

The `dependencies` field syntax:

```
jei(required){curseforge:238222}
applied-energistics-2(optional){curseforge:223794}
some-incompatible-mod(incompatible){curseforge:111111}
```

Format: `<slug>(<type>){curseforge:<projectId>}`. Multiple entries on
separate lines.

## Multi-platform: also publishing to Modrinth

Modrinth has the same mc-publish flow:

```yaml
modrinth-id: your-modrinth-slug
modrinth-token: ${{ secrets.MODRINTH_TOKEN }}
```

Project IDs on Modrinth are slugs (the URL bit), not numeric. Token
from <https://modrinth.com/settings/pats>.

Add both `curseforge-id` and `modrinth-id` to the same workflow step
and mc-publish uploads to both stores in one go. Generally a good
idea since some players prefer Modrinth.

## Multi-MC-version support (when you grow into it)

When your mod becomes popular enough that users on older MC versions
matter, the standard pattern is **branch-per-MC-version**:

```
master / main      → current MC version (e.g. 1.21.1)
1.20                → MC 1.20.x support (separate branch)
1.19                → MC 1.19.x support (separate branch, often archived)
```

Each branch has its own `gradle.properties`, its own dependencies, its
own NeoForge version pin. CI runs per-branch.

Workflow setup:

- `.github/workflows/release-1.21.yml` (only fires on tags from master)
- `.github/workflows/release-1.20.yml` (only fires on tags from the 1.20 branch)

Trigger refinement:

```yaml
on:
  push:
    tags:
      - 'v*'
    branches:
      - 'master'   # restrict by branch
```

Or use **tag prefixes**: tag `1.21-v0.4.0` from master and `1.20-v0.4.0`
from the 1.20 branch, filter in the workflow trigger.

CurseForge handles multi-version naturally — all jars upload to the
same project, each tagged with its own `game-versions`. Players see
one project page; the launcher picks the right file.

This is a heavy maintenance commitment — every fix needs cherry-picking
across branches. Worth it only when real users are stuck on older
versions. Drop oldest branches every ~6-12 months.

## Multi-loader support (Forge + Fabric + NeoForge)

If you want to support multiple mod loaders from one codebase,
**Architectury** is the standard solution. The structure becomes:

```
mod/
├── common/             shared business logic (≥90% of the code)
├── neoforge/           NeoForge-specific glue
├── fabric/             Fabric-specific glue
├── (forge/)            Forge if still needed
├── build.gradle        meta-build delegating to subprojects
```

Common code uses an "expect/actual" pattern — `common/` declares
interfaces, each loader subproject implements them. Build produces
one jar per loader from the same source.

Significant complexity tax. Don't reach for it unless you actually
have demand for Fabric support — most NeoForge mods don't get it
anyway and stay single-loader.
