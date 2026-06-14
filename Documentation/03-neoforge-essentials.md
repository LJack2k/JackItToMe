# NeoForge 1.21 essentials

The minimum NeoForge API surface you need to know to ship a non-trivial
mod: mod lifecycle, side-aware code, the packet/networking system, and
the `neoforge.mods.toml` declarations that bind them together.

This doc is opinionated about which APIs matter. Many things exist that
you can ignore until you need them. The bits below are what come up in
nearly every mod.

## Mod lifecycle and entry point

NeoForge 1.21 uses **constructor injection** on the `@Mod` class:

```java
@Mod(MyMod.MODID)
public final class MyMod {
    public static final String MODID = "mymod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public MyMod(IEventBus modBus, Dist dist) {
        modBus.addListener(ModPackets::register);  // register packets
        // ... other mod-bus event listeners

        if (dist.isClient()) {
            NeoForge.EVENT_BUS.register(ClientEvents.class);
        }
    }
}
```

- **`IEventBus modBus`** — the mod-bus. Use for **registration events**:
  `RegisterPayloadHandlersEvent`, `RegisterKeyMappingsEvent`,
  `EntityAttributeCreationEvent`, etc. Anything named `Register...`.
- **`Dist dist`** — `CLIENT` or `DEDICATED_SERVER`. Use `dist.isClient()`
  to guard client-only code. **Never** reference client-only Minecraft
  classes outside of `isClient()` branches or `@OnlyIn(Dist.CLIENT)`
  annotations.

`NeoForge.EVENT_BUS` is the **game-bus**, separate from the mod-bus.
Use for runtime events: `ServerTickEvent`, `PlayerEvent`,
`BlockEvent.BreakEvent`, etc. Mod-bus is for registration; game-bus is
for runtime gameplay events.

## Two event-bus subscription styles

**Static class with `@EventBusSubscriber`:**

```java
@EventBusSubscriber(modid = MyMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) { ... }
}
```

Default bus is the game-bus. For mod-bus events:

```java
@EventBusSubscriber(modid = MyMod.MODID, value = Dist.CLIENT,
                    bus = EventBusSubscriber.Bus.MOD)
public final class KeyBindings {
    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) { ... }
}
```

**Programmatic subscription via the constructor:**

```java
public MyMod(IEventBus modBus, Dist dist) {
    modBus.addListener(ModPackets::register);
}
```

Mix as needed — `@EventBusSubscriber` is cleaner for static handlers,
programmatic is necessary for handlers that need access to instance
state.

## Packets in NeoForge 1.21 (the big one)

NeoForge 1.21 ships with a new packet system based on
**`CustomPacketPayload`** records. Every packet is:

1. A **record** implementing `CustomPacketPayload`.
2. A **`StreamCodec`** declaring how to serialize fields.
3. A **handler method** called when the packet arrives.
4. **Registration** in a `RegisterPayloadHandlersEvent` listener.

### Anatomy of a packet

```java
public record PullIngredientsPayload(
        List<Ingredient> ingredients,
        PullMode mode,
        boolean respectShortageGate) implements CustomPacketPayload {

    public static final Type<PullIngredientsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    MyMod.MODID, "pull_ingredients"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PullIngredientsPayload> STREAM_CODEC =
            StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()),
                PullIngredientsPayload::ingredients,
                ByteBufCodecs.VAR_INT.map(PullMode::fromOrdinal, PullMode::ordinal),
                PullIngredientsPayload::mode,
                ByteBufCodecs.BOOL,
                PullIngredientsPayload::respectShortageGate,
                PullIngredientsPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                PullHandler.handle(sp, this);
            }
        });
    }
}
```

Important properties of this template:

- **`Type<T>` with a `ResourceLocation`** — the packet's globally unique
  identifier. Convention: `<modid>:<snake_case_name>`.
- **`StreamCodec.composite(codec1, getter1, codec2, getter2, ..., constructor)`**
  — paired (codec, getter) for each field, plus the record's
  constructor at the end. Order matters and must match the record's
  declaration order.
- **`RegistryFriendlyByteBuf`** — the buffer type for play-phase
  packets that may need registry access (items, enchantments, etc.).
- **`handle(IPayloadContext ctx)`** — runs on the network thread.
  Always `ctx.enqueueWork(...)` to bounce to the main thread before
  touching game state.

### Common stream codecs

| Field type | Codec |
| --- | --- |
| `int` | `ByteBufCodecs.VAR_INT` (recommended) or `ByteBufCodecs.INT` |
| `long` | `ByteBufCodecs.VAR_LONG` |
| `boolean` | `ByteBufCodecs.BOOL` |
| `String` | `ByteBufCodecs.STRING_UTF8` |
| `ItemStack` | `ItemStack.OPTIONAL_STREAM_CODEC` (handles empty stacks) |
| `Ingredient` | `Ingredient.CONTENTS_STREAM_CODEC` (added in 1.20.5) |
| `List<T>` | `<elementCodec>.apply(ByteBufCodecs.list())` |
| Enum (ordinal) | `ByteBufCodecs.VAR_INT.map(Enum::fromOrdinal, Enum::ordinal)` |
| `ResourceLocation` | `ResourceLocation.STREAM_CODEC` |

### Registration

```java
public final class ModPackets {
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(MyMod.MODID).versioned("1");

        registrar.playToServer(
                PullIngredientsPayload.TYPE,
                PullIngredientsPayload.STREAM_CODEC,
                PullIngredientsPayload::handle
        );

        registrar.playToClient(
                JackFeedbackPayload.TYPE,
                JackFeedbackPayload.STREAM_CODEC,
                JackFeedbackPayload::handle
        );
    }
}
```

- **`playToServer`** — client sends this; server receives.
- **`playToClient`** — server sends this; client receives.
- **`versioned("1")`** — handshake version. Bump when packet format
  changes incompatibly; clients/servers with different versions won't
  connect.

### `.optional()` for graceful degradation

By default, NeoForge requires registered packet channels on **both
sides** of any connection. A client with your mod can't connect to a
server without it — handshake fails with "channel missing".

If you want the mod to be installable client-side-only (and silently
no-op when the server doesn't have it), mark each registration as
optional:

```java
registrar.playToServer(...)
        .optional();
```

Trade-off: the mod's features don't work on incompatible servers, but
players can still connect. Useful for cosmetic/QoL mods. Not useful for
mods that fundamentally need server cooperation.

## Side-aware code patterns

The Java side of side-awareness:

```java
// Player object types tell you where you are:
//   - Player        - either side (abstract)
//   - LocalPlayer   - client only (the player you control)
//   - ServerPlayer  - server only (any player connected)

public void handle(IPayloadContext ctx) {
    ctx.enqueueWork(() -> {
        if (ctx.player() instanceof ServerPlayer sp) {
            // safe: we're on the server, sp is a real player
        }
    });
}
```

**The classloader trap:** if a class references a client-only type
(e.g., `Screen`, `Minecraft`), the JVM tries to load that type when the
class is loaded — even if the referencing code path never runs. On a
dedicated server, the client-only types don't exist, and the class
fails to load.

Fixes:

1. **Put client-only classes in client-only packages**, never referenced
   from main code. Server-side code never touches them = JVM never
   resolves them = no `NoClassDefFoundError`.

2. **`@OnlyIn(Dist.CLIENT)`** — strips the annotated class/method at
   build time on the dedicated server. Use sparingly; it can be tricky
   to get right and the error messages are bad.

3. **Lazy reference via runtime indirection** — for the rare case
   where a server-side packet needs to *dispatch to* a client method
   (like dispatching the feedback animation). Reference the client-only
   class only from inside the handler body, which runs only on the
   client:

   ```java
   public void handle(IPayloadContext ctx) {
       // This method's body references ClientFeedback (client-only).
       // The JVM only resolves ClientFeedback when handle() is invoked,
       // which only happens on the client (packet is playToClient).
       ctx.enqueueWork(() -> ClientFeedback.show(item));
   }
   ```

## `neoforge.mods.toml` essentials

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
logoFile = "icon.png"        # mod icon shown in Mods menu and on CurseForge
logoBlur = true               # false for pixel-art icons

# Required deps
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

# Required client-side only (e.g. JEI)
[[dependencies.${mod_id}]]
modId = "jei"
type = "required"
versionRange = "[19.0.0,)"
ordering = "AFTER"
side = "CLIENT"

# Optional integration
[[dependencies.${mod_id}]]
modId = "ae2"
type = "optional"
versionRange = "[19.0.0,)"
ordering = "AFTER"
side = "BOTH"
```

Field meanings:

- **`type`**: `required`, `optional`, `incompatible`, or `discouraged`.
  `incompatible` blocks the game from starting; `discouraged` shows a
  warning but loads.
- **`versionRange`**: Maven range syntax. `[1.0.0,2.0.0)` means
  "1.0 ≤ x < 2.0". `[1.0.0,)` means "≥ 1.0".
- **`ordering`**: `BEFORE`, `AFTER`, or `NONE`. Controls load order
  relative to the dependency.
- **`side`**: `CLIENT`, `SERVER`, or `BOTH`. `CLIENT` means "only check
  this dep on the client" — useful for client-side mods like JEI that
  shouldn't be required on dedicated servers.

## Resources structure

```
src/main/resources/
├── META-INF/
│   └── neoforge.mods.toml
├── pack.mcmeta                           # required, identifies as a Forge resource pack
├── icon.png                              # mod icon (if logoFile is set)
└── assets/<modid>/
    ├── lang/en_us.json                   # translations
    ├── textures/                         # textures (gui, items, blocks)
    ├── models/                           # block/item models
    └── ...
```

`pack.mcmeta`:

```json
{
    "pack": {
        "description": "<mod name> resources",
        "pack_format": 34
    }
}
```

`pack_format` for 1.21 is `34`. Different MC versions use different
values — check the wiki when updating.

## ResourceLocation

```java
ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(MyMod.MODID, "textures/gui/icon.png");
```

Resolves to `assets/mymod/textures/gui/icon.png` in the jar.

For built-in registries (items, blocks):

```java
ResourceLocation itemId = ResourceLocation.fromNamespaceAndPath(MyMod.MODID, "my_item");
// Resolves the registered item with that ID.
```

The deprecated single-arg constructor (`new ResourceLocation("mymod:my_item")`)
still works but warns; prefer `fromNamespaceAndPath` for new code.

## Quick reference: events you'll actually use

| Event | Bus | Use for |
| --- | --- | --- |
| `RegisterPayloadHandlersEvent` | Mod | Packet registration |
| `RegisterKeyMappingsEvent` | Mod | Keybind registration |
| `FMLClientSetupEvent` | Mod | Client-side init that needs all registries ready |
| `FMLCommonSetupEvent` | Mod | Both-sides init |
| `ScreenEvent.Init.Post` | Game | Modifying screens after they're built |
| `ScreenEvent.Render.Post` | Game | Drawing overlays on screens |
| `ScreenEvent.KeyPressed.Pre` | Game | Intercepting key presses in GUIs |
| `ScreenEvent.MouseButtonPressed.Pre` | Game | Intercepting clicks in GUIs |
| `ClientTickEvent.Post` | Game | Per-tick client logic (only when no GUI open) |
| `PlayerEvent.PlayerLoggedInEvent` | Game | Server-side: player joined |
