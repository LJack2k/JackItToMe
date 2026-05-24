package nl.ljack2k.jackittome.jei;

import nl.ljack2k.jackittome.JackItToMe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
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
        registration.addRecipeButtonFactory(
                new JackPullRecipeButtonFactory(registration.getJeiHelpers()));

        // Shortage-overlay decorator. Registered per vanilla recipe type — JEI's
        // API doesn't have a "decorate everything" shortcut, so we enumerate.
        // Modded recipe types won't get the overlay until someone wires them up,
        // but the button itself still works on 