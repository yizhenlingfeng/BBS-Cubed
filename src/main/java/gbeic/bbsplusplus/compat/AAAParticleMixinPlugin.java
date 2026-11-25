package gbeic.bbsplusplus.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Skips AAA Particles targets before Mixin resolves classes from the optional mod. */
public class AAAParticleMixinPlugin implements IMixinConfigPlugin
{
    private static final String RENDERER = "gbeic/bbsplusplus/client/renderer/AAAParticleFormRenderer.class";

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName)
    {
        if (!targetClassName.startsWith("mod.chloeprime.aaaparticles."))
        {
            return true;
        }

        return FabricLoader.getInstance().isModLoaded("aaa_particles")
            && AAAParticleMixinPlugin.class.getClassLoader().getResource(RENDERER) != null;
    }

    @Override
    public void onLoad(String mixinPackage)
    {}

    @Override
    public String getRefMapperConfig()
    {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets)
    {}

    @Override
    public List<String> getMixins()
    {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {}
}
