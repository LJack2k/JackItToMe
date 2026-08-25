package nl.ljack2k.jackittome.emi.mixin;

import net.neoforged.fml.loading.LoadingModList;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Gate for {@code jackittome.mixins.json}. EMI is an OPTIONAL dependency, so the
 * EMI-targeting mixins must not be applied when it is absent — otherwise Mixin
 * fails to find {@code dev.emi.emi.screen.RecipeDisplay} and takes the game down
 * for JEI/REI (or no-viewer) users.
 * <p>
 * Mixin plugins run during class loading, long before mod construction, so
 * {@code ModList} is not populated yet. {@link LoadingModList} is the one that
 * is available this early.
 */
public final class EmiMixinPlugin implements IMixinConfigPlugin {

    private static final boolean EMI_PRESENT =
            LoadingModList.get().getModFileById("emi") != null;

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return EMI_PRESENT;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
