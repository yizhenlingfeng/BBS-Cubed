package gbeic.bbsplusplus.compat;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** 仅在安装 Iris 时加载以其内部类为目标的注入。 */
public class ShaderCurvesMixinPlugin implements IMixinConfigPlugin
{
    private static final Set<String> IRIS_ONLY_MIXINS = Set.of(
        "CustomUniformsSetupAccessor",
        "IrisRenderingPipelineMixin",
        "ShadowRendererMixin",
        "CenterDepthSamplerMixin",
        "IrisPbrTextureCleanupMixin121"
    );

    @Override
    public void onLoad(String mixinPackage)
    {}

    @Override
    public String getRefMapperConfig()
    {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName)
    {
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);

        return !IRIS_ONLY_MIXINS.contains(simpleName) || IrisCompat.isLoaded();
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
