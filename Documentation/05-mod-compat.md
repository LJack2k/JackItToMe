# Mod compatibility patterns

How to integrate with other mods *optionally* — your mod still loads
when they're not installed, and integration features activate when
they are. Examples: Applied Energistics 2, Refined Storage,
Sophisticated Storage, Curios, etc.

## The optional-dependency pattern

The core trick: **lazy class loading**. The JVM doesn't resolve a
class until something actually calls a method that references it.
You can have code that imports `appeng.api.storage.MEStorage` —
that class won't be loaded as long as no executed code path calls
into it.

So the integration pattern is:

1. Declare the dep `optional` in `neoforge.mods.toml`.
2. Add the dep as `compileOnly` in `build.gradle` (so your code
   compiles).
3. Gate every entry point into the integration with
   `ModList.get().isLoaded("their_modid")`.
4. Touch the integration's classes only from inside the gate.

### `neoforge.mods.toml`

```toml
[[dependencies.${mod_id}]]
modId = "ae2"
type = "optional"
versionRange = "[19.0.0,)"
ordering = "AFTER"
side = "BOTH"
```

### `build.gradle`

```groovy
compileOnly "curse.maven:applied-energistics-2-223794:${ae2_curse_file_id}"
runtimeOnly "curse.maven:applied-energistics-2-223794:${ae2_curse_file_id}"
runtimeOnly "curse.maven:guideme-1173950:${guideme_curse_file_id}"  // transitive
```

### Gate in your mod's entry point

```java
public MyMod(IEventBus modBus, Dist dist) {
    if (ModList.get().isLoaded("ae2")) {
        Ae2ItemSource.register();  // this method touches AE2 classes
    }
}
```

The class `Ae2ItemSource` is *referenced* by name in `MyMod`, but
referencing a class doesn't load it — only calling methods on it
does. As long as no other code path calls `Ae2ItemSource.register()`
when AE2 isn't installed, the JVM never resolves the AE2 imports,
and there's no `NoClassDefFoundError`.

## Abstraction: the source pattern

For a mod like JackItToMe that pulls items from multiple kinds of
storage (chests, AE2 ME networks, RS grids), the right abstraction is
a small interface implemented per-backend:

```java
public interface ItemSource {
    boolean matches(ServerPlayer player);
    long count(ItemStack template, ServerPlayer player);
    ItemStack extract(ItemStack template, int amount, ServerPlayer player);
    default void insertOrDrop(ItemStack stack, ServerPlayer player) {
        if (!stack.isEmpty()) player.drop(stack, false);
    }
}
```

And an ordered registry:

```java
public final class ItemSourceRegistry {
    private static final List<ItemSource> SOURCES = new ArrayList<>();

    public static void registerHighPriority(ItemSource src) { SOURCES.add(0, src); }
    public static void registerLowPriority(ItemSource src)  { SOURCES.add(src); }

    public static ItemSource findSource(ServerPlayer player) {
        for (ItemSource s : SOURCES) if (s.matches(player)) return s;
        return null;
    }
}
```

- The **default** source (e.g. vanilla container walker) registers
  low-priority. It matches *any* open menu and falls back when nothing
  more specific matches.
- **Specific** sources (AE2 terminal, RS grid) register high-priority.
  They match only their own menu types.

First-match-wins ordering. The default catch-all stays at the back.

Adding a new storage mod = write a new `ItemSource` implementation,
register it conditionally in your mod's entry point. No changes to
business logic.

## AE2 integration (working pattern)

```java
package mymod.compat.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.menu.me.common.MEStorageMenu;

public final class Ae2ItemSource implements ItemSource {

    public static void register() {
        try {
            ItemSourceRegistry.registerHighPriority(new Ae2ItemSource());
        } catch (Throwable t) {
            // AE2 API mismatch — log and skip without crashing the mod.
            MyMod.LOGGER.error("AE2 integration failed", t);
        }
    }

    @Override
    public boolean matches(ServerPlayer player) {
        return player.containerMenu instanceof MEStorageMenu;
    }

    @Override
    public long count(ItemStack template, ServerPlayer player) {
        MEStorage storage = storageOf(player);
        if (storage == null) return 0;
        AEItemKey key = AEItemKey.of(template);
        return key == null ? 0 : storage.getAvailableStacks().get(key);
    }

    @Override
    public ItemStack extract(ItemStack template, int amount, ServerPlayer player) {
        MEStorage storage = storageOf(player);
        if (storage == null || amount <= 0) return ItemStack.EMPTY;

        AEItemKey key = AEItemKey.of(template);
        if (key == null) return ItemStack.EMPTY;

        long extracted = storage.extract(key, amount, Actionable.MODULATE,
                IActionSource.ofPlayer(player));
        if (extracted <= 0) return ItemStack.EMPTY;

        ItemStack out = template.copy();
        out.setCount((int) Math.min(extracted, Integer.MAX_VALUE));
        return out;
    }

    private static MEStorage storageOf(ServerPlayer player) {
        if (!(player.containerMenu instanceof MEStorageMenu menu)) return null;
        try {
            return menu.getHost().getInventory();
        } catch (Throwable t) {
            return null;
        }
    }
}
```

Key API points for AE2 1.21:

- **`MEStorageMenu`** — all terminal menus extend this (ME Terminal,
  Crafting Terminal, Pattern Terminal, Wireless, etc.). One `instanceof`
  covers them all.
- **`AEItemKey.of(stack)`** — converts an ItemStack to AE2's resource
  key. Returns null for empty/invalid stacks.
- **`MEStorage.extract / insert`** — the canonical extraction API. Use
  `Actionable.MODULATE` to actually do it; `Actionable.SIMULATE` for
  count-without-extract.
- **`IActionSource.ofPlayer(player)`** — required for security checks
  (AE2's wireless permission system, etc.).

The `getHost().getInventory()` accessor is the one fragile point —
AE2 has shifted this between `getInventory()` and `getStorage()` in
different versions. If it breaks, swap one for the other.

## Refined Storage 2 integration (working pattern)

RS 2 is a major rewrite with a different API shape from RS 1.x.

```java
package mymod.compat.rs;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.Storage;
import com.refinedmods.refinedstorage.common.api.grid.Grid;
import com.refinedmods.refinedstorage.common.api.storage.PlayerActor;
import com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;

public final class RsItemSource implements ItemSource {

    @Override
    public boolean matches(ServerPlayer player) {
        return player.containerMenu instanceof AbstractGridContainerMenu;
    }

    @Override
    public long count(ItemStack template, ServerPlayer player) {
        Storage storage = storageOf(player);
        if (storage == null) return 0;
        ResourceKey resource = new ItemResource(template.getItem(),
                template.getComponentsPatch());
        // SIMULATE extract = "how much could I extract?" — RS has no
        // direct getAmount API; this is the canonical workaround.
        return storage.extract(resource, Long.MAX_VALUE,
                Action.SIMULATE, new PlayerActor(player));
    }

    @Override
    public ItemStack extract(ItemStack template, int amount, ServerPlayer player) {
        Storage storage = storageOf(player);
        if (storage == null || amount <= 0) return ItemStack.EMPTY;
        ResourceKey resource = new ItemResource(template.getItem(),
                template.getComponentsPatch());
        long extracted = storage.extract(resource, amount,
                Action.EXECUTE, new PlayerActor(player));
        return extracted <= 0 ? ItemStack.EMPTY : template.copyWithCount((int) extracted);
    }

    private static Storage storageOf(ServerPlayer player) {
        if (!(player.containerMenu instanceof AbstractGridContainerMenu menu)) return null;
        try {
            Grid grid = gridOf(menu);
            return (grid != null && grid.isGridActive()) ? grid.getItemStorage() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** The `grid` field on AbstractGridContainerMenu is package-private. */
    private static Grid gridOf(AbstractGridContainerMenu menu) {
        Class<?> c = menu.getClass();
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField("grid");
                f.setAccessible(true);
                Object value = f.get(menu);
                if (value instanceof Grid g) return g;
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) {
                return null;
            }
            c = c.getSuperclass();
        }
        return null;
    }
}
```

Key API points for RS 2.x:

- **`AbstractGridContainerMenu`** — base for all grids (normal,
  crafting, pattern, wireless, portable). One `instanceof` covers all
  variants.
- **`Grid.getItemStorage()`** — returns a `Storage` (which combines
  `StorageView + InsertableStorage + ExtractableStorage`).
- **`ItemResource(Item, DataComponentPatch)`** — RS 2's resource key
  for items. Constructed directly, not via a factory.
- **`Action.EXECUTE` / `Action.SIMULATE`** — RS's version of AE2's
  Actionable enum.
- **`PlayerActor(player)`** — RS 2 actor record for the security/log
  system. Pass as the `actor` argument.

The one reflective bit is reading the `grid` field — RS 2 keeps it
package-private with no public getter. If RS ever adds one (e.g.
`menu.getGrid()`), swap the reflection for a direct call.

## Verifying optional integration works without the mod

After implementing an optional integration, **run the dev client
without the mod loaded** to confirm the gate works:

```groovy
// In build.gradle, temporarily:
// compileOnly "curse.maven:applied-energistics-2-223794:${ae2_curse_file_id}"
// runtimeOnly "curse.maven:applied-energistics-2-223794:${ae2_curse_file_id}"
// runtimeOnly "curse.maven:guideme-1173950:${guideme_curse_file_id}"
```

Comment out the `runtimeOnly` lines, leave `compileOnly` active so the
code still compiles. Run `./gradlew runClient`. The mod should load,
the integration should be silently absent, and no
`NoClassDefFoundError` should appear in the log.

If `NoClassDefFoundError` appears: somewhere your code is calling an
AE2 method without going through the `ModList.isLoaded("ae2")` gate.
The stack trace shows where — usually a leaked reference from a
listener that runs at mod-init time, before the gate runs.

Re-enable the `runtimeOnly` lines once verified.

## Inspecting an unknown mod's API

When integrating with a new mod, you don't know what its API looks
like. The workflow:

1. Add the mod as `compileOnly` in `build.gradle`.
2. Open the mod's jar from your Gradle cache:
   `~/.gradle/caches/modules-2/files-2.1/curse.maven/<slug>-<id>/<fileId>/<hash>/<mod>.jar`
3. Run `unzip -l <jar>` to list classes.
4. For specific classes, `strings -n 4 <classfile>` reveals method
   names, field names, and method descriptors.

See [08-debugging.md](./08-debugging.md) for the full jar-inspection
workflow.

## Coexistence patterns

If two storage mods both want to be "the active source" for a player's
open menu, only one wins (first match in the registry). For
JackItToMe: AE2 and RS sources both register high-priority; if the
player has an AE2 terminal open, AE2 wins; if RS grid, RS wins. The
two mods don't really coexist at the same open-menu level anyway —
you can only have one menu open at a time.

For other compat scenarios (e.g., a mod that adds an *enhancement*
rather than a *primary backend*), use higher/lower priority to
control the chain.
