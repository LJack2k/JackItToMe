package nl.ljack2k.jackittome;

import nl.ljack2k.jackittome.client.ClientEvents;
import nl.ljack2k.jackittome.compat.ae2.Ae2ItemSource;
import nl.ljack2k.jackittome.compat.rs.RsItemSource;
import nl.ljack2k.jackittome.network.ModPackets;
import nl.ljack2k.jackittome.source.ItemSourceRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for JackItToMe.
 * <p>
 * Wires up packet registration, the default ItemSource for vanilla containers,
 * and the optional AE2 / Refined Storage sources when those mods are present.
 */
@Mod(JackItToMe.MODID)
public final class JackItToMe {
    public static final String MODID = "jackittome";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public JackItToMe(IEventBus modBus, Dist dist) {
        // Packet registration is a mod-bus event in NeoForge 1.21.
        modBus.addListener(ModPackets::register);

        // Register vanilla container source first (lowest priority — falls back to it).
        ItemSourceRegistry.registerVanillaSource();

        // Compat: only touch the integration classes if the mod is loaded. The class
        // itself is safe to *reference* (no static init of foreign types until we call
        // it) — but never call any method that touches AE2/RS types unless we've
        // verified the mod is loaded.
        if (ModList.get().isLoaded("ae2")) {
            LOGGER.info("Detected AE2 — registering MEStorage item source.");
            Ae2ItemSource.register();
        }
        if (ModList.get().isLoaded("refinedstorage")) {
            LOGGER.info("Detected Refined Storage — registering network item source.");
            RsItemSource.register();
        }

        // Client-only setup.
        if (dist.isClient()) {
            NeoForge.EVENT_BUS.register(ClientEvents.class);
        }
    }
}
