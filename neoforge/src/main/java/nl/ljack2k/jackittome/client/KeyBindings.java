package nl.ljack2k.jackittome.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Keybinds for the mod.
 * <p>
 * Registration happens on the mod event bus during client init.
 * Press detection lives in {@link ClientEvents}.
 * <p>
 * The "jack hovered" keybind is bound to {@code GUI} conflict context — it only
 * fires while a screen is open, so it never collides with vanilla overworld
 * bindings. Default is G: unbound in vanilla, and close enough to Shift/Ctrl
 * that the quantity modifiers work one-handed (the original default P was a
 * far-right stretch and collides with vanilla's Social Interactions key).
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

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(JACK_HOVERED);
    }
}
