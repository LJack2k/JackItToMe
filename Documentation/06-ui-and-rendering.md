# UI rendering, icons, and build-time asset generation

Patterns for drawing on Minecraft screens, packaging icon textures
correctly, and automating SVG → PNG conversion at build time so the
SVG is the source of truth.

## GuiGraphics: the modern drawing API

Minecraft 1.20.2+ replaced `PoseStack` + `Tesselator` direct calls
with `GuiGraphics`, a higher-level wrapper. Most rendering you'll do
goes through it.

```java
// Filled rectangle
gg.fill(x1, y1, x2, y2, 0xFF202020);  // ARGB int

// Outlined fill (background + border in one operation)
gg.fill(x, y, x + w, y + h, 0xFF000000);                    // border
gg.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF373737);    // background

// Text
gg.drawString(font, Component.literal("Hello"), x, y, 0xFFFFFFFF, false);
//                                                  ^color    ^drop-shadow

// Item rendering
gg.renderItem(itemStack, x, y);
gg.renderItemDecorations(font, itemStack, x, y);  // count badge + durability

// Texture
gg.blit(textureLocation, x, y, u, v, width, height);
```

### Pose stack translation

For nested coordinate systems (drawing relative to a sub-area), push,
translate, draw, pop:

```java
gg.pose().pushPose();
gg.pose().translate(originX, originY, 0);
// ... draw things in local coordinates ...
gg.pose().popPose();
```

The third argument to `translate` is the **z** coordinate. Use higher
z values to draw on top of other elements:

- z = 0: same layer as the screen background
- z = 100-200: above slot items but below tooltips
- z = 300-400: above tooltips (use for "always visible" overlays like
  the falling-item animation in JackItToMe)

### Alpha blending

```java
RenderSystem.enableBlend();
RenderSystem.setShaderColor(1f, 1f, 1f, 0.5f);  // 50% alpha
gg.renderItem(stack, x, y);
RenderSystem.setShaderColor(1f, 1f, 1f, 1f);    // always restore
```

For items specifically, alpha works but is fragile because item
rendering uses complex shaders. The pattern above is what JackItToMe's
animation uses; expect 80% of vanilla-style alpha-blend work to
look correct.

### Translucent overlays via slot.drawHighlight

For drawing a colored overlay on a JEI recipe slot:

```java
slot.drawHighlight(gg, 0x80FF4040);  // ARGB: 50% alpha red
```

`drawHighlight` expects the GuiGraphics to be in the slot's local
coordinate system. From a JEI button-controller's `drawExtras` (which
runs in screen coordinates), translate first:

```java
Rect2i recipeRect = layoutDrawable.getRect();
gg.pose().pushPose();
gg.pose().translate(recipeRect.getX(), recipeRect.getY(), 0);
slot.drawHighlight(gg, 0x80FF4040);
gg.pose().popPose();
```

## Animations (client-side, transient)

Pattern from JackItToMe's `JackAnimations` — a static singleton
holding a list of in-flight animations, drawn each frame in a
`ScreenEvent.Render.Post` hook, removed when their duration elapses.

```java
public final class MyAnimations {
    private static final List<Anim> ACTIVE = new ArrayList<>();
    private static final long DURATION_MS = 500;

    private record Anim(ItemStack stack, float startX, float startY, long startTime) {}

    public static void start(ItemStack stack, double mouseX, double mouseY) {
        if (stack == null || stack.isEmpty()) return;
        ACTIVE.add(new Anim(stack.copyWithCount(1),
                (float) mouseX, (float) mouseY, System.currentTimeMillis()));
    }

    public static void render(GuiGraphics gg, Screen screen) {
        long now = System.currentTimeMillis();
        Iterator<Anim> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Anim a = it.next();
            long elapsed = now - a.startTime;
            if (elapsed >= DURATION_MS) {
                it.remove();
                continue;
            }
            float t = elapsed / (float) DURATION_MS;  // 0.0 → 1.0
            // ... compute current position/scale/alpha from t, draw ...
        }
    }
}
```

Trigger from event handlers; render from `ScreenEvent.Render.Post`.

State lives in static fields because the animation is purely visual —
no save/load concern, no networking, no synchronization issues across
players.

## Icon textures

Icon resources live at `assets/<modid>/textures/gui/<name>.png` in the
jar. Reference via `ResourceLocation`:

```java
ResourceLocation iconPath = ResourceLocation.fromNamespaceAndPath(
        MyMod.MODID, "textures/gui/my_button.png");
```

For JEI's `IDrawable`:

```java
IDrawable icon = helpers.getGuiHelper()
        .drawableBuilder(iconPath, 0, 0, 16, 16)
        .setTextureSize(16, 16)         // ← see "the texture-size gotcha"
        .build();
```

### The texture-size gotcha

`createDrawable(loc, u, v, w, h)` defaults to assuming the texture file
is 256×256 (Minecraft's standard atlas size). If your file is actually
16×16, the UV math is wrong and you end up sampling only the top-left
1px — the button appears blank.

Always use `drawableBuilder(...)` + `setTextureSize(...)` with the
real file dimensions for standalone (non-atlas) textures.

For multi-icon sprite sheets, `setTextureSize(sheetWidth, sheetHeight)`
and use the u/v offsets to pick out each icon's region.

## Mod icon (in the Mods menu)

The mod icon shown in Minecraft's Mods menu, on CurseForge, and on
Modrinth is declared in `neoforge.mods.toml`:

```toml
[[mods]]
modId = "${mod_id}"
...
logoFile = "icon.png"   # path relative to resources root, NOT assets/
logoBlur = true         # false for pixel-art icons
```

The file goes at `src/main/resources/icon.png`. **It's not under
`assets/`** — `logoFile` looks at the resources root directly. This
trips up about half of first-time mod authors.

Recommended size: **256×256**. Bigger isn't useful (in-game display
is ~80×80); smaller looks blurry on CurseForge listings.

## SVG → PNG at build time

Maintaining PNG icons by hand is annoying because you have to
manually regenerate after every SVG edit. The clean pattern: SVGs are
source files, PNGs are derived build artifacts.

### Setup

Add Apache Batik as a buildscript-only dependency (not bundled in
the mod jar):

```groovy
buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath 'org.apache.xmlgraphics:batik-transcoder:1.17'
        classpath 'org.apache.xmlgraphics:batik-codec:1.17'
    }
}
```

### The rasterize task

```groovy
def iconMappings = [
    [src: 'src/main/resources/icon.svg',
     dst: 'src/main/resources/icon.png',
     w: 256, h: 256],
    [src: 'src/main/resources/button.svg',
     dst: 'src/main/resources/assets/mymod/textures/gui/button.png',
     w: 16, h: 16],
]

tasks.register('rasterizeIcons') {
    group = 'mymod'
    description = 'Rasterize all SVG icons in src/main/resources/ to PNGs.'

    inputs.files(iconMappings*.src)
    outputs.files(iconMappings*.dst)

    doLast {
        iconMappings.each { m ->
            def srcFile = file(m.src)
            def dstFile = file(m.dst)
            dstFile.parentFile.mkdirs()

            // For tiny targets (16×16), render at 8× first then crisp-
            // downsample via Java2D. Direct rendering at 16×16 produces
            // mushy edges because the rasterizer's antialiasing has too
            // few pixels to work with.
            def renderW = m.w < 64 ? m.w * 8 : m.w
            def renderH = m.h < 64 ? m.h * 8 : m.h

            def transcoder = new org.apache.batik.transcoder.image.PNGTranscoder()
            transcoder.addTranscodingHint(
                    org.apache.batik.transcoder.image.PNGTranscoder.KEY_WIDTH,
                    (float) renderW)
            transcoder.addTranscodingHint(
                    org.apache.batik.transcoder.image.PNGTranscoder.KEY_HEIGHT,
                    (float) renderH)

            def intermediate = new java.io.ByteArrayOutputStream()
            srcFile.withInputStream { input ->
                transcoder.transcode(
                        new org.apache.batik.transcoder.TranscoderInput(input),
                        new org.apache.batik.transcoder.TranscoderOutput(intermediate))
            }

            if (renderW != m.w || renderH != m.h) {
                def big = javax.imageio.ImageIO.read(
                        new java.io.ByteArrayInputStream(intermediate.toByteArray()))
                def small = new java.awt.image.BufferedImage(
                        m.w, m.h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                def g = small.createGraphics()
                g.setRenderingHint(
                        java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                g.setRenderingHint(
                        java.awt.RenderingHints.KEY_RENDERING,
                        java.awt.RenderingHints.VALUE_RENDER_QUALITY)
                g.drawImage(big, 0, 0, m.w, m.h, null)
                g.dispose()
                javax.imageio.ImageIO.write(small, 'PNG', dstFile)
            } else {
                dstFile.bytes = intermediate.toByteArray()
            }
            logger.lifecycle("Rasterized ${m.src} -> ${m.dst} (${m.w}x${m.h})")
        }
    }
}

tasks.named('sourcesJar').configure {
    dependsOn 'rasterizeIcons'
    exclude 'icon.png'
    exclude 'assets/mymod/textures/gui/button.png'  // derived, not source
}

tasks.named('processResources', ProcessResources).configure {
    dependsOn 'rasterizeIcons'
    exclude '**/*.svg'  // SVGs are source-only, don't ship in the jar
}
```

### Why the two-step rasterization

SVG rasterizers (Batik, ImageMagick, others) tend to produce mushy
results when rendering directly at tiny target sizes like 16×16.
Antialiasing has too few output pixels to work with.

Rendering at 8× (128×128) gives the rasterizer enough pixels for
clean antialiasing, then a separate downsample with Java2D's BICUBIC
filter produces crisp edges in the final 16×16.

For larger targets (≥64×64), direct rendering is fine.

### `.gitignore` the generated PNGs

```
# PNGs derived from SVGs at build time by rasterizeIcons.
src/main/resources/icon.png
src/main/resources/assets/mymod/textures/gui/button.png
```

The SVGs are the source of truth; PNGs regenerate on every build. If
the PNG was already committed before adding the task, untrack it:

```powershell
git rm --cached src/main/resources/icon.png
# then commit
```

### Build-task dependencies (avoid the validator error)

Without the right dependsOn lines, Gradle's task validator complains:

```
Task ':sourcesJar' uses this output of task ':rasterizeIcons' without
declaring an explicit or implicit dependency.
```

Add `dependsOn 'rasterizeIcons'` to **every task that reads from
`src/main/resources/`**. In practice that's `processResources` and
`sourcesJar`. Other tasks (`compileJava`, etc.) don't read resources
so don't need the dependency.

## Translations (lang files)

```json
// src/main/resources/assets/mymod/lang/en_us.json
{
    "key.categories.mymod": "MyMod",
    "key.mymod.jack_hovered": "Jack hovered item",
    "mymod.feedback.pulled": "Pulled %s items",
    "mymod.tooltip.something": "Hover to see..."
}
```

Reference in code:

```java
Component label = Component.translatable("key.jackittome.jack_hovered");
Component pulled = Component.translatable("mymod.feedback.pulled", count);  // %s = count
```

For other languages, add `de_de.json`, `fr_fr.json`, etc. Minecraft
auto-selects based on the player's language preference.

## Common UI element sizes

For visual consistency with vanilla:

| Element | Size |
| --- | --- |
| Slot | 16×16 (item) inside 18×18 (button area, 1px border) |
| Button (side panel) | 16×16 icon area + JEI's frame |
| Text | 9px line height (vanilla font) |
| Tooltip padding | 4px |
| Slot grid spacing | 18px (so slot+border tiles cleanly) |

## Useful constants

| Constant | Value | Notes |
| --- | --- | --- |
| Text color (white) | `0xFFFFFFFF` | A=FF means fully opaque |
| Text color (gray hint) | `0xFFAAAAAA` | Common for secondary text |
| Translucent red overlay | `0x80FF4040` | 50% alpha red — JEI uses this for missing-slot indicators |
| Tooltip z-coordinate | ~200 | Render above for "always-on-top" effects |
| Animation z-coordinate | ~400 | Above tooltips |
| Item slot inner color | `0xFF8B8B8B` | Vanilla slot background |
