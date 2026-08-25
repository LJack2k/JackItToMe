package nl.ljack2k.jackittome;

import net.neoforged.fml.loading.LoadingModList;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Gate for {@code jackittome.mixins.json}. Lives outside the mixin package on
 * purpose: classes inside it are owned by Mixin and cannot be loaded normally.
 * <p>
 * Every recipe viewer is an OPTIONAL
 * dependency, so a viewer's mixins must not be applied when that viewer is
 * absent — otherwise Mixin cannot find the target class and takes the game down
 * for everyone using a different viewer, or none.
 * <p>
 * Mixin plugins run during class loading, long before mod construction, so
 * {@code ModList} is not populated yet. {@link LoadingModList} is the one that
 * is available this early.
 */
public final class ViewerMixinPlugin implements IMixinConfigPlugin {

    private static final boolean EMI_PRESENT =
            LoadingModList.get().getModFileById("emi") != null;
    private static final boolean REI_PRESENT =
            LoadingModList.get().getModFileById("roughlyenoughitems") != null;

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Mixins live in a package named after the viewer they target, so the
        // right dependency is derived from the class name rather than listed
        // separately (a list would drift the moment a mixin is added).
        if (mixinClassName.contains(".emi.")) return EMI_PRESENT;
        if (mixinClassName.contains(".rei.")) return REI_PRESENT;
        return false;
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
