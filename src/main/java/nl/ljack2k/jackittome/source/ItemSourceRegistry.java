package nl.ljack2k.jackittome.source;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiny ordered registry. First registered, first checked. Compat sources for
 * AE2 / RS register themselves <em>before</em> the vanilla source so they take
 * priority when their respective menus are open.
 */
public final class ItemSourceRegistry {
    private ItemSourceRegistry() {}

    private static final List<ItemSource> SOURCES = new ArrayList<>();

    public static void registerHighPriority(ItemSource src) {
        SOURCES.add(0, src);
    }

    public static void registerLowPriority(ItemSource src) {
        SOURCES.add(src);
    }

    /** Default vanilla source — registered from the main mod constructor. */
    public static void registerVanillaSource() {
        registerLowPriority(new ContainerItemSource());
    }

    /** Find the first source whose {@link ItemSource#matches} returns true. */
    public static ItemSource findSource(ServerPlayer player) {
        for (ItemSource s : SOURCES) {
            if (s.matches(player)) return s;
        }
        return null;
    }
}
