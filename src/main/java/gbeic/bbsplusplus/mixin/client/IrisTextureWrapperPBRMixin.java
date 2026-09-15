package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.pbr.render.BonePBRContext;
import gbeic.bbsplusplus.pbr.render.PBRTextureModifier;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.iris.IrisTextureWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/**
 * 逐骨骼 PBR 对 Iris 贴图包装的修改：当前骨骼存在 PBR 覆盖时，
 * 把对应通道值烘焙进镜面/法线 GL 纹理。逐材质 PBR 已由 2.6 原生 FormMaterial 接管，相关分支移除。
 */
@Mixin(IrisTextureWrapper.class)
public class IrisTextureWrapperPBRMixin
{
    @Shadow(remap = false)
    public Link texture;

    @Inject(method = "getGlId", at = @At("RETURN"), cancellable = true)
    private void bbspp_snow$modifyPbrTexture(CallbackInfoReturnable<Integer> cir)
    {
        if (this.texture == null || this.texture.path == null)
        {
            return;
        }

        String path = this.texture.path;
        boolean specular = path.endsWith("_s.png") || path.contains("_s.png?");
        boolean normal = path.endsWith("_n.png") || path.contains("_n.png?");

        if (!specular && !normal)
        {
            return;
        }

        Map<String, Integer> bone = BonePBRContext.getCurrent();

        if (bone == null || bone.isEmpty())
        {
            return;
        }

        Map<String, Integer> values = new HashMap<>();

        bbspp_snow$copy(values, bone, normal);

        cir.setReturnValue(PBRTextureModifier.getModifiedTextureId(cir.getReturnValue(), normal, values));
    }

    private static void bbspp_snow$copy(Map<String, Integer> target, Map<String, Integer> source, boolean normal)
    {
        if (normal)
        {
            target.put("n", source.getOrDefault("pbr_n", 0));
        }
        else
        {
            target.put("s_r", source.getOrDefault("pbr_s_r", 0));
            target.put("s_g", source.getOrDefault("pbr_s_g", 0));
            target.put("s_b", source.getOrDefault("pbr_s_b", 0));
            target.put("s_a", source.getOrDefault("pbr_s_a", 0));
            target.put("emission_multiplier", source.getOrDefault("emission_multiplier", 100));
        }
    }
}
