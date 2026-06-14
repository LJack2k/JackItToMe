package nl.ljack2k.jackittome.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.forge.REIPluginClient;

/**
 * REI plugin entry point. On NeoForge, REI discovers plugins via the
 * {@code @REIPluginClient} annotation (not a ServiceLoader file — that's the
 * Fabric mechanism).
 * <p>
 * Registers {@link JackReiButtonExtension} on every category so the pull button
 * appears on all recipe displays. We do this in {@link #registerDisplays} rather
 * than {@code registerCategories} because REI runs each plugin stage across all
 * plugins before the next, so by the displays stage every category — including
 * those from other mods — is registered.
 */
@REIPluginClient
public final class JackItToMeReiPlugin implements REIClientPlugin {

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        CategoryRegistry.getInstance().forEach(config -> addButtonExtension(config));
    }

    private static <T extends Display> void addButtonExtension(CategoryRegistry.CategoryConfiguration<T> config) {
        config.registerExtension(new JackReiButtonExtension<>());
    }
}
