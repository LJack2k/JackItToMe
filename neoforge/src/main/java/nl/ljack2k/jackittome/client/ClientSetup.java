package nl.ljack2k.jackittome.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only wiring, kept out of {@code JackItToMe} so the entrypoint never
 * references client classes. {@link ConfigurationScreen} extends {@code Screen},
 * which does not exist on a dedicated server — same reason {@link ClientEvents}
 * lives behind its own class.
 */
public final class ClientSetup {
    private ClientSetup() {}

    public static void init(IEventBus modBus, ModContainer container) {
        NeoForge.EVENT_BUS.register(ClientEvents.class);

        container.registerConfig(ModConfig.Type.CLIENT, JackConfig.SPEC);

        // Keep the cached button offset in step with the file and the config screen.
        modBus.addListener((ModConfigEvent.Loading e) -> ButtonPlacement.refresh());
        modBus.addListener((ModConfigEvent.Reloading e) -> ButtonPlacement.refresh());

        // Puts a Config button next to us in the Mods list. NeoForge generates the
        // screen from the spec itself and uses each option's comment as its
        // tooltip, so there is no bespoke UI to maintain.
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new ConfigurationScreen(mod, parent));
    }
}
