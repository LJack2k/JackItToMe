# Debugging strategies and inspection workflows

When something doesn't work and you don't know why. This is the
single most valuable doc in the set — the techniques here saved many
hours during JackItToMe development.

## The first rule: log, don't guess

When a feature silently doesn't work, **add `LOGGER.info(...)` calls at
every step of the chain** and run the test. The log tells you exactly
where the chain broke. Trying to reason about it from code alone is
slower and produces more wrong answers.

Pattern:

```java
public static void handle(SomePacket payload) {
    LOGGER.info("[diag] handle called: payload={}", payload);

    var something = computeSomething(payload);
    LOGGER.info("[diag] computed: {}", something);

    if (something.isEmpty()) {
        LOGGER.info("[diag] early return: something was empty");
        return;
    }

    // ... continue ...
    LOGGER.info("[diag] sending response");
    sendResponse(...);
}
```

Tag your diagnostic logs with a consistent prefix (`[diag]`,
`[mymod-probe]`, etc.) so you can grep them out easily and remove them
in one pass later. Demote to `LOGGER.debug` rather than delete if
they'd be useful to keep around for future debugging.

## Inspecting other mods' jars

When integrating with a mod whose API you don't fully know, **look at
the bytecode of its public API classes**. The Gradle cache has every
mod's jar:

```
~/.gradle/caches/modules-2/files-2.1/<group>/<artifact>/<version>/<hash>/<artifact>.jar
```

For CurseMaven mods:

```
~/.gradle/caches/modules-2/files-2.1/curse.maven/<slug>-<projectId>/<fileId>/<hash>/<slug>-<projectId>-<fileId>.jar
```

### Workflow

```bash
# Unzip a jar to a temp directory
mkdir /tmp/jarinspect && cd /tmp/jarinspect
unzip -q /path/to/mod.jar

# List the API classes
find com/mod -path "*api*" -name "*.class" | head -30

# Get class structure via strings (no JDK needed)
strings -n 4 com/mod/api/SomeInterface.class | head -50
```

`strings -n 4` extracts UTF-8 strings of ≥4 chars from the file.
Class files store names (methods, fields, types) as UTF-8 in the
constant pool, so `strings` reveals the entire public API without
needing `javap`.

What to look for:

- **Method names** (`getInventory`, `getStorage`, `addRecipe`...) —
  appear as plain identifiers
- **Method descriptors** — JVM bytecode descriptors like
  `(Lnet/minecraft/world/item/ItemStack;I)Lsomething/SomeReturn;`.
  Decode: `(...)R` = params and return type, prefixed with `L` for
  object types and ended with `;`, `I` for int, `Z` for boolean, `V`
  for void, `[X` for array of X.
- **Field types** appear next to field names
- **Implemented interfaces** appear at the top of the class

Example: from inspecting AE2's `MEStorageMenu.class`:

```
mezz/jei/api/recipe/category/extensions/IRecipeCategoryDecorator
java/lang/Object
draw
(Ljava/lang/Object;Lmezz/jei/api/recipe/category/IRecipeCategory;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/client/gui/GuiGraphics;DD)V
decorateTooltips
(Lmezz/jei/api/gui/builder/ITooltipBuilder;Ljava/lang/Object;Lmezz/jei/api/recipe/category/IRecipeCategory;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;DD)V
```

That tells me `IRecipeCategoryDecorator` has a `draw` method taking
`(Object, IRecipeCategory, IRecipeSlotsView, GuiGraphics, double, double)`
and returning `void`. Easy to translate to the matching Java signature.

### When `javap` is available

If your environment has the JDK (not just JRE), `javap` is cleaner:

```bash
javap -public com/mod/api/SomeInterface.class
```

shows the same data as parsed text. Most CI runners and dev machines
have it. Sandboxes often don't (only JRE) — the `strings` workflow is
the universal fallback.

## Finding a method's caller (working backwards)

When you have a mod's class and want to know how it's USED elsewhere:

```bash
# Find any class file in the same jar that references this method name
grep -r "methodName" /tmp/jarinspect | head -10

# Or with strings on multiple classes
for f in /tmp/jarinspect/com/mod/**/*.class; do
    if strings "$f" | grep -q methodName; then
        echo "$f"
    fi
done
```

Useful when you find a "showError" method and want to see how the
mod itself triggers it.

## When a workflow is silently doing nothing

Symptoms: no log lines, no errors, just nothing happens.

Most common causes, in order of likelihood:

1. **The event subscriber isn't registered.** Check the registration
   site — typo in modid, wrong event bus, `@EventBusSubscriber`
   pointed at the wrong dist.

2. **The condition you're filtering on is always false.** Add a log
   *before* the if-condition that prints the field values.

3. **The class isn't being loaded.** Add a `static { LOGGER.info("loaded"); }`
   block at the top of the class. If you don't see "loaded" in the
   log, NeoForge / JEI never instantiates the class.

4. **`@JeiPlugin` missing on a JEI plugin class.** Easy to forget,
   produces zero log lines.

5. **Class is loaded but method has wrong signature** — `@Override`
   not enforced means a typo lets it compile but never gets called.
   Always use `@Override`.

## When a packet seems to silently drop

For client → server → client roundtrips:

1. Log on the client at `sendToServer` time.
2. Log on the server at the handler's first line.
3. Log on the server at the response-send.
4. Log on the client at the response-handler's first line.

If you see 1 but not 2: packet not registered, channel mismatch,
or wrong direction (playToServer vs playToClient).

If you see 2 but not 3: server-side handler bug.

If you see 3 but not 4: response handler not registered, or
client-side state filtering it out.

The four-step trace nails 95% of packet-flow bugs in five minutes.

## When the build fails

### "Could not resolve all files for configuration..."

Dependency not found. The error includes:

```
Searched in the following locations:
  - https://....../artifact-version.pom
  - ...
```

Check whether you can browse to those URLs in a web browser. If the
.pom isn't there, either the version is wrong or the artifact lives
in a different repo. See [02-dependencies.md](./02-dependencies.md)
for how to find the right coordinates.

### "incompatible types: inference variable T has incompatible bounds"

Generic method overload mismatch. Common when implementing JEI
interfaces that declare `<T>` methods — you must repeat the `<T>`
in the implementer, not substitute `<?>`. See
[04-jei-integration.md](./04-jei-integration.md) §"Per-recipe buttons"
for the canonical example.

### "bad config line 1 in file .git/config"

Filesystem inconsistency, usually in sandbox / WSL environments.
Doesn't affect Gradle builds. If it appears in git commands too, kill
the Gradle daemon (`./gradlew --stop`) and retry.

### "Task ':sourcesJar' uses this output of task ':rasterizeIcons'..."

Gradle's task validator detected an implicit dependency. Fix:
explicitly declare `dependsOn 'rasterizeIcons'` on `sourcesJar`. See
[06-ui-and-rendering.md](./06-ui-and-rendering.md) for the full
pattern.

### "/home/runner/work/_temp/...sh: line 1: ./gradlew: Permission denied"

`gradlew` was committed without the executable bit. Two fixes — pick
one:

```yaml
# In the workflow, before ./gradlew calls:
run: |
  chmod +x ./gradlew
  ./gradlew ...
```

Or fix it permanently:

```powershell
git update-index --chmod=+x gradlew
git commit -m "..."
git push
```

## When things look weird, dump state

Frequently the bug is "this object isn't what I think it is." Dump
its full identity:

```java
LOGGER.info("[diag] obj={} class={} hash={}",
        obj,
        obj.getClass().getName(),
        System.identityHashCode(obj));
```

Then look at the log across multiple invocations. If `identityHashCode`
changes between calls but you expected the same object — JEI / some
other framework is creating fresh instances each call. Don't key
state on object identity in that case (see
[04-jei-integration.md](./04-jei-integration.md) §"Identity instability").

## Common UI rendering bugs

| Symptom | Likely cause |
| --- | --- |
| Button visible but no icon ("blank button") | Texture-size mismatch — use `drawableBuilder().setTextureSize(...)`, not `createDrawable(...)` |
| Overlay draws at wrong position | Forgot pose-stack translation before slot-relative draw |
| Overlay draws but is invisible | Other code is drawing on top — bump z-coordinate or check alpha |
| Hover detection always false | `mouseX`/`mouseY` are in different coordinate space than expected; log the values to compare |
| Animation never appears | `Render.Post` not firing on this screen, OR the animation was added to a list that gets cleared |

## How to escalate a hard-to-diagnose bug

If diagnostic logging hasn't found it after 10 minutes:

1. **Reduce.** Comment out other code until only the failing path
   remains. The bug often becomes obvious in a minimal repro.

2. **Reverse the assumption.** Whatever you're "sure" about is the
   thing to question. Add a log to verify it.

3. **Read the framework's own code.** Find a class in the framework
   (JEI, NeoForge, AE2) that does the thing you're trying to do.
   Read how it does it. Often a missing pattern (`setTextureSize`,
   pose translation, `enqueueWork`) is the difference.

4. **Search the framework's GitHub for similar issues.** Other devs
   have hit the same wall. Closed issues often have the answer.

5. **Take a 30-minute break.** Frustration narrows the search. Coming
   back fresh often reveals the obvious thing you missed.

## Common patterns to suspect when stuck

- **Caching stale state.** Reset before re-querying, especially after
  hover/click transitions.
- **Reference comparison on framework objects.** Many frameworks
  return fresh wrappers each call. Use a stable underlying object
  (the recipe, the player UUID, etc.) as a cache key.
- **Side mismatch.** Client-only code referenced from server-side
  paths. Server-only types referenced from client paths.
- **Class loading order.** `static` blocks run when the class is first
  used; if you reference an uninitialized constant, it might be null
  during a class init that touches another class with similar issues.
- **Async dispatch race.** Network handlers run on the network thread.
  `ctx.enqueueWork(...)` bounces to the main thread. Skipping the
  bounce produces "works most of the time but occasionally crashes."

## Final note

Diagnostic logging is the single most reusable skill in modding. The
bug you're hunting today might take an hour with logs; without them,
days. Add liberally, remove only after the bug is fixed and verified.
The discipline of "I will not guess; I will see the actual values"
pays off compounding interest across every project.
