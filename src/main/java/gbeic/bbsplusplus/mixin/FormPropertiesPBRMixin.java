package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.PBRModelFormAccess;
import gbeic.bbsplusplus.pbr.BonePBRData;
import gbeic.bbsplusplus.pbr.BonePBRKeyframeFactory;
import gbeic.bbsplusplus.pbr.PBRChannel;
import gbeic.bbsplusplus.pbr.PBRData;
import gbeic.bbsplusplus.pbr.PBRKeyframeFactory;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

@Mixin(value = FormProperties.class, remap = false)
public abstract class FormPropertiesPBRMixin
{
    @Shadow
    public abstract KeyframeChannel registerChannel(String key, IKeyframeFactory factory);

    @Inject(method = "getOrCreate", at = @At("HEAD"), cancellable = true)
    private void bbspp_snow$getOrCreate(Form form, String key, CallbackInfoReturnable<KeyframeChannel> cir)
    {
        String leaf = bbspp_snow$leaf(key);

        if ("bone_pbr".equals(leaf))
        {
            IKeyframeFactory factory = KeyframeFactories.FACTORIES.get("bone_pbr");

            if (factory instanceof BonePBRKeyframeFactory)
            {
                cir.setReturnValue(this.registerChannel(key, factory));
            }
        }
        else if (leaf.startsWith("pbr.") && leaf.length() > 4)
        {
            IKeyframeFactory factory = KeyframeFactories.FACTORIES.get("pbr");

            if (factory instanceof PBRKeyframeFactory)
            {
                cir.setReturnValue(this.registerChannel(key, factory));
            }
        }
        else if (bbspp_snow$pbrComponent(leaf) != null)
        {
            cir.setReturnValue(this.registerChannel(key, KeyframeFactories.FLOAT));
        }
    }

    @Inject(method = "applyProperty", at = @At("HEAD"), cancellable = true)
    private void bbspp_snow$apply(float tick, Form form, KeyframeChannel value, float blend, CallbackInfo ci)
    {
        if (!(form instanceof ModelForm modelForm))
        {
            return;
        }

        String key = value.getId();
        String leaf = bbspp_snow$leaf(key);

        if ("bone_pbr".equals(leaf))
        {
            Map<String, Map<String, Integer>> overrides = ((PBRModelFormAccess) modelForm).bbspp_snow$getBonePbrOverrides();
            KeyframeSegment segment = value.find(tick);

            if (segment == null)
            {
                if (blend >= 1F)
                {
                    overrides.clear();
                }
            }
            else
            {
                BonePBRData data = ((BonePBRData) segment.createInterpolated()).copy();

                for (PBRChannel channel : data.getAll().values())
                {
                    channel.clamp();
                }

                overrides.clear();
                overrides.putAll(data.toIntMap());
            }

            ci.cancel();

            return;
        }

        if (leaf.startsWith("pbr.") && leaf.length() > 4)
        {
            Map<String, Map<String, Integer>> overrides = ((PBRModelFormAccess) modelForm).bbspp_snow$getPbrOverrides();
            String material = leaf.substring(4);
            KeyframeSegment segment = value.find(tick);

            if (segment == null)
            {
                if (blend >= 1F)
                {
                    overrides.remove(material);
                }
            }
            else
            {
                PBRChannel channel = ((PBRData) segment.createInterpolated()).get(material).copy();
                channel.clamp();
                overrides.put(material, channel.toIntMap());
            }

            ci.cancel();

            return;
        }

        String component = bbspp_snow$pbrComponent(leaf);

        if (component == null)
        {
            return;
        }

        Map<String, Map<String, Integer>> overrides = ((PBRModelFormAccess) modelForm).bbspp_snow$getPbrOverrides();
        int dot = leaf.length() - component.length() - 1;
        String material = dot > 0 ? leaf.substring(0, dot) : "";
        KeyframeSegment segment = value.find(tick);

        if (segment == null)
        {
            if (blend >= 1F)
            {
                Map<String, Integer> materialValues = overrides.get(material);

                if (materialValues != null)
                {
                    materialValues.remove(component);

                    if (materialValues.isEmpty())
                    {
                        overrides.remove(material);
                    }
                }
            }
        }
        else
        {
            int max = "pbr_s_a".equals(component) ? 254 : 255;
            int pbrValue = Math.max(0, Math.min(max, Math.round((Float) segment.createInterpolated())));
            overrides.computeIfAbsent(material, ignored -> new HashMap<>()).put(component, pbrValue);
        }

        ci.cancel();
    }

    @Unique
    private static String bbspp_snow$leaf(String key)
    {
        if (key == null)
        {
            return "";
        }

        int slash = key.lastIndexOf('/');

        return slash < 0 ? key : key.substring(slash + 1);
    }

    @Unique
    private static String bbspp_snow$pbrComponent(String leaf)
    {
        String[] components = {"pbr_s_r", "pbr_s_g", "pbr_s_b", "pbr_s_a", "pbr_n"};

        for (String component : components)
        {
            if (leaf.equals(component) || leaf.endsWith("." + component))
            {
                return component;
            }
        }

        return null;
    }
}
