package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.pbr.render.BonePBRContext;
import gbeic.bbsplusplus.pbr.render.PBRTextureContext;
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
        Map<String, Integer> values = new HashMap<>();

        if (bone != null && !bone.isEmpty())
        {
            bbspp_snow$copy(values, bone, normal);
        }
        else
        {
            Map<String, Map<String, Integer>> context = PBRTextureContext.get();

            if (context == null || context.isEmpty())
            {
                return;
            }

            for (Map<String, Integer> material : context.values())
            {
                bbspp_snow$maximum(values, material, "pbr_s_r", "s_r");
                bbspp_snow$maximum(values, material, "pbr_s_g", "s_g");
                bbspp_snow$maximum(values, material, "pbr_s_b", "s_b");
                bbspp_snow$maximum(values, material, "pbr_s_a", "s_a");
                bbspp_snow$maximum(values, material, "pbr_n", "n");
            }
        }

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

    private static void bbspp_snow$maximum(Map<String, Integer> target, Map<String, Integer> source, String sourceKey, String targetKey)
    {
        target.put(targetKey, Math.max(target.getOrDefault(targetKey, 0), source.getOrDefault(sourceKey, 0)));
    }
}
