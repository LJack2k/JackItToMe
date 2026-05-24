package nl.ljack2k.jackittome.jei;

import nl.ljack2k.jackittome.JackItToMe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/**
 * Minimal JEI plugin. We don't add categories or transfer handlers here — JEI's
 * own "+" button stays untouched. What we do is grab the {@link IJeiRuntime}
 * reference so the client-side button can ask JEI "what's the currently
 * displayed recipe?" when it needs to build the ingredient list for the packet.
 */
@JeiPlugin
public final class JackItToMeJeiPlugin implements IModPlugin {

    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(JackItToMe.MODID, "jei_plugin");
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
