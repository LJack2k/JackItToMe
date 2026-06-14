package nl.ljack2k.jackittome.jei;

import nl.ljack2k.jackittome.JackItToMe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI plugin. Two responsibilities:
 * <ol>
 *   <li>{@link #registerAdvanced(IAdvancedRegistration)} — registers our
 *       {@link JackPullRecipeButtonFactory}, which makes JEI render a chest-icon
 *       button on every recipe screen. JEI handles positioning, hover styling,
 *       tooltips, and click dispatch.</li>
 *   <li>{@link #onRuntimeAvailable(IJeiRuntime)} — captures the runtime so the
 *       client-side P keybind can ask JEI about sidebar/bookmark hover items.</li>
 * </ol>
 */
@JeiPlugin
public final class JackItToMeJeiPlugin implements IModPlugin {

    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(JackItToMe.MODID, "jei_plugin");
    }

    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        // The factory registers the button universally across every recipe type
        // — including modded ones — so the button controller's drawExtras
        // method also draws shortage overlays for everything. No per-type
        // decorator registration is needed.
        registration.addRecipeButtonFactory(
                new JackPullRecipeButtonFactory(registration.getJeiHelpers()));
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    /** Null if JEI hasn't finished loading yet (early in client init, or if JEI is missing). */
    public static IJeiRuntime runtime() {
        return runtime;
    }
}
