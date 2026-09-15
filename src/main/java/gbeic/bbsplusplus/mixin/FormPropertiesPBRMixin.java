package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.PBRModelFormAccess;
import gbeic.bbsplusplus.pbr.BonePBRData;
import gbeic.bbsplusplus.pbr.BonePBRKeyframeFactory;
import gbeic.bbsplusplus.pbr.PBRChannel;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
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

import java.util.Map;

/**
 * 逐骨骼 PBR 轨道（bone_pbr）在 FormProperties 上的注册与应用。
 * <p>
 * 2.6 原生 FormMaterial 已提供逐材质 PBR，本插件原有的「逐材质 PBR」整段移除；
 * 但原生 FormBone 没有 PBR 字段，因此逐骨骼 PBR 作为本插件独有能力保留。
 * </p>
 * <p>2.6 适配：注册走 {@code register(TrackId, factory)}；应用不再有 {@code applyProperty}，
 * 统一由 {@code apply(TrackContext,TrackId,KeyframeChannel,float,float)} 按 TrackBehaviours 分发，
 * 这里在该静态分发方法的 HEAD 拦截 bone_pbr 通道自行求值并取消原生分发。</p>
 */
@Mixin(value = FormProperties.class, remap = false)
public abstract class FormPropertiesPBRMixin
{
    @Shadow
    public abstract KeyframeChannel register(TrackId track, IKeyframeFactory factory);

    @Inject(method = "getOrCreate", at = @At("HEAD"), cancellable = true)
    private void bbspp_snow$getOrCreate(Form form, String key, CallbackInfoReturnable<KeyframeChannel> cir)
    {
        String leaf = bbspp_snow$leaf(key);

        if ("bone_pbr".equals(leaf))
        {
            IKeyframeFactory factory = KeyframeFactories.FACTORIES.get("bone_pbr");

            if (factory instanceof BonePBRKeyframeFactory)
            {
                cir.setReturnValue(this.register(TrackId.parse(key), factory));
            }
        }
    }

    @Inject(
        method = "apply(Lmchorse/bbs_mod/film/replays/tracks/TrackContext;Lmchorse/bbs_mod/film/replays/tracks/TrackId;Lmchorse/bbs_mod/utils/keyframes/KeyframeChannel;FF)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void bbspp_snow$apply(TrackContext context, TrackId track, KeyframeChannel value,
                                         float tick, float blend, CallbackInfo ci)
    {
        if (!"bone_pbr".equals(track.property()) || !(context.root() instanceof ModelForm modelForm))
        {
            return;
        }

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
}
