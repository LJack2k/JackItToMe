package nl.ljack2k.jackittome.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/**
 * Keybinds for the mod.
 * <p>
 * Registration happens on the mod event bus during client init.
 * Press detection lives in {@link ClientEvents}.
 * <p>
 * The three pull quantities are separate keybinds (not one bind plus
 * hardcoded modifiers) so the Controls screen itself documents them and each
 * is independently rebindable. Defaults share the physical G key with NeoForge
 * {@link KeyModifier} defaults: G / Shift+G / Ctrl+G — G is unbound in vanilla
 * and one-handed with both modifiers (the original default P was a far-right
 * stretch and collides with vanilla's Social Interactions key).
 * <p>
 * All three use {@code GUI} conflict context — they only fire while a screen
 * is open, so they never collide with vanilla overworld bindings.
 */
@EventBusSubscriber(modid = nl.ljack2k.jackittome.JackItToMe.MODID,
                    value = Dist.CLIENT,
                    bus = EventBusSubscriber.Bus.MOD)
public final class KeyBindings {
    private KeyBindings() {}

    public static final String CATEGORY = "key.categories.jackittome";

    /** Hover an item, press this key, one of that item jumps into your inventory. */
    public static final KeyMapping JACK_HOVERED = new KeyMapping(
            "key.jackittome.jack_hovered",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );

    /** Same, but pulls one full stack (up to the item's max stack size). */
    public static final KeyMapping JACK_STACK = new KeyMapping(
            "key.jackittome.jack_stack",
            KeyConflictContext.GUI,
            KeyModifier.SHIFT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );

    /** Same, but pulls as much as fits in the player's inventory. */
    public static final KeyMapping JACK_MAX = new KeyMapping(
            "key.jackittome.jack_max",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(JACK_HOVERED);
        event.register(JACK_STACK);
        event.register(JACK_MAX);
    }
}
