package nl.ljack2k.jackittome.client;

import nl.ljack2k.jackittome.JackItToMe;
import nl.ljack2k.jackittome.jei.JackItToMeJeiPlugin;
import nl.ljack2k.jackittome.network.PullIngredientsPayload;
import nl.ljack2k.jackittome.network.PullMode;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;

import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

/**
 * Client-side keybind handler.
 * <p>
 * Single user-facing behavior: while a screen is open, hovering an item and
 * pressing the bound key ({@link KeyBindings#JACK_HOVERED}, default <kbd>P</kbd>)
 * pulls one of that item from whatever container is currently open into the
 * player's inventory.
 * <p>
 * The hover source is checked in this order:
 * <ol>
 *   <li>Slot under the mouse in any {@link AbstractContainerScreen}
 *       (vanilla inventory, chest, modded GUIs including AE2 terminals).</li>
 *   <li>JEI's ingredient-list overlay (the sidebar). Resolved reflectively
 *       because the exact return type of {@code getIngredientUnderMouse} has
 *       drifted across JEI versions.</li>
 * </ol>
 */
@EventBusSubscriber(modid = JackItToMe.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    /**
     * GUI-context keybindings don't fire via the normal tick loop — the screen
     * has first dibs on every keypress. We hook {@link ScreenEvent.KeyPressed.Pre}
     * to see every key the screen receives and compare it against our binding.
     */
    @SubscribeEvent
    public static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int pressedKey = event.getKeyCode();
        int boundKey   = KeyBindings.JACK_HOVERED.getKey().getValue();
        if (pressedKey != boundKey) return;

        handleJackHovered(mc);

        // Eat the event so the screen doesn't also process the key (e.g. typing
        // the letter into JEI's search box, or anything else screens bind P to).
        event.setCanceled(true);
    }

    /** Renders the falling-item animation on top of the screen each frame. */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        JackAnimations.render(event.getGuiGraphics(), event.getScreen());
    }

    private static void handleJackHovered(Minecraft mc) {
        // First check: are we inside a JEI recipe view? If so, the slot under the
        // mouse might accept many variants (the cycling "any plank" case). Get
        // ALL of them so the server's most-abundant resolver can pick whichever
        // variant is actually available.
        Ingredient ing = tryRecipeSlotIngredient(mc);

        if (ing == null) {
            // Not a recipe-view hover, or the slot only has one variant. Fall back
            // to the single hovered ItemStack.
            ItemStack hovered = hoveredItemStack(mc);
            if (hovered.isEmpty()) {
                JackItToMe.LOGGER.debug("[JackItToMe] Jack key pressed but no hovered item found.");
                return;
            }
            JackItToMe.LOGGER.info("[JackItToMe] Requesting 1x {} from open container.",
                    hovered.getHoverName().getString());
            ing = Ingredient.of(hovered);
        }

        // No optimistic animation — we wait for the server's JackFeedbackPayload
        // response and animate based on the actual outcome.
        PacketDistributor.sendToServer(new PullIngredientsPayload(List.of(ing), PullMode.SINGLE));
    }

    /**
     * If the cursor is over a JEI recipe slot with multiple acceptable variants,
     * build an Ingredient from all of them so the server can substitute. Returns
     * null if not in a recipe view, no slot under mouse, or slot has only one
     * variant (in which case the single-item fallback gives the same result).
     */
    private static Ingredient tryRecipeSlotIngredient(Minecraft mc) {
        Screen screen = mc.screen;
        if (screen == null) return null;

        // Cheap class-name filter so we don't probe non-recipe JEI screens.
        String simple = screen.getClass().getSimpleName().toLowerCase();
        String fqn = screen.getClass().getName().toLowerCase();
        if (!fqn.contains("jei") || !simple.contains("recipe")) return null;

        Window win = mc.getWindow();
        double mx = mc.mouseHandler.xpos() * win.getGuiScaledWidth()  / (double) win.getScreenWidth();
        double my = mc.mouseHandler.ypos() * win.getGuiScaledHeight() / (double) win.getScreenHeight();

        List<ItemStack> variants = JeiRecipeSlotProbe.getAllItemsUnderMouse(screen, mx, my);
        if (variants.size() < 2) return null; // single-variant slots use the regular path

        JackItToMe.LOGGER.info("[JackItToMe] Requesting recipe slot with {} acceptable variants.", variants.size());
        return Ingredient.of(variants.toArray(ItemStack[]::new));
    }

    /** Find the ItemStack the cursor is currently over. */
    private static ItemStack hoveredItemStack(Minecraft mc) {
        Screen screen = mc.screen;
        if (screen == null) return ItemStack.EMPTY;

        // 1) Container slot under mouse — vanilla path.
        if (screen instanceof AbstractContainerScreen<?> acs) {
            Slot slot = acs.getSlotUnderMouse();
            if (slot != null && !slot.getItem().isEmpty()) {
                return slot.getItem();
            }
        }

        // 2) JEI surfaces — four places, all sharing the same getIngredientUnderMouse API:
        //    a) right sidebar = ingredient list overlay
        //    b) left sidebar = bookmark overlay
        //    c) bottom-left recipe history = part of the bookmark overlay in modern JEI
        //    d) inside an open recipe screen = the RecipesGui itself
        IJeiRuntime rt = JackItToMeJeiPlugin.runtime();
        if (rt != null) {
            ItemStack hov = tryJeiOverlayHover(rt.getIngredientListOverlay());
            if (!hov.isEmpty()) return hov;

            hov = tryJeiOverlayHover(rt.getBookmarkOverlay());
            if (!hov.isEmpty()) return hov;

            hov = tryJeiOverlayHover(rt.getRecipesGui());
            if (!hov.isEmpty()) return hov;
        }

        return ItemStack.EMPTY;
    }

    /**
     * Call {@code getIngredientUnderMouse(IIngredientType)} reflectively on any
     * JEI overlay object and normalize the return shape to an ItemStack.
     * <p>
     * Both {@code IIngredientListOverlay} and {@code IBookmarkOverlay} expose
     * this method with the same signature, so one helper handles both. JEI has
     * flip-flopped between returning raw T, Optional&lt;T&gt;,
     * ITypedIngredient&lt;T&gt;, and Optional&lt;ITypedIngredient&lt;T&gt;&gt;
     * across versions — the reflective approach lets us not care.
     */
    private static ItemStack tryJeiOverlayHover(Object overlay) {
        if (overlay == null) return ItemStack.EMPTY;
        try {
            for (java.lang.reflect.Method m : overlay.getClass().getMethods()) {
                if (!"getIngredientUnderMouse".equals(m.getName())) continue;
                if (m.getParameterCount() != 1) continue;
                Object result = m.invoke(overlay, VanillaTypes.ITEM_STACK);
                ItemStack unwrapped = unwrapToItemStack(result);
                if (!unwrapped.isEmpty()) return unwrapped;
                break;
            }
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("[JackItToMe] JEI overlay hover lookup failed ({}): {}",
                    overlay.getClass().getSimpleName(), t.toString());
        }
        return ItemStack.EMPTY;
    }

    /** Recursively peel away Optional and ITypedIngredient wrappers until we find an ItemStack. */
    private static ItemStack unwrapToItemStack(Object o) {
        if (o == null) return ItemStack.EMPTY;
        if (o instanceof ItemStack stack) {
            return stack.isEmpty() ? ItemStack.EMPTY : stack;
        }
        if (o instanceof Optional<?> opt) {
            return opt.map(ClientEvents::unwrapToItemStack).orElse(ItemStack.EMPTY);
        }
        // Most likely an ITypedIngredient — call getIngredient() reflectively.
        try {
            java.lang.reflect.Method m = o.getClass().getMethod("getIngredient");
            return unwrapToItemStack(m.invoke(o));
        } catch (NoSuchMethodException ignored) {
            return ItemStack.EMPTY;
        } catch (Throwable t) {
            JackItToMe.LOGGER.debug("[JackItToMe] unwrap failed for {}: {}", o.getClass().getName(), t.toString());
            return ItemStack.EMPTY;
        }
    }
}
