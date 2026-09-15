package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.texture.TextureTweenContext;
import gbeic.bbsplusplus.keyframes.LegacyTextureTweenTrackMigration;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把纹理补间接入模型原生纹理轨道，并在加载阶段触发旧轨道迁移。
 *
 * <p>原生 {@code texture} 轨道负责保存 Link，补间参数作为关键帧扩展字段挂在同一关键帧上。
 * 2.6 适配：属性应用不再有 {@code applyProperty}，逐通道应用统一走
 * {@code apply(TrackContext,TrackId,KeyframeChannel,float,float)}；在其 HEAD/RETURN 包裹模型上下文。</p>
 */
@Mixin(value = FormProperties.class, remap = false)
public abstract class FormPropertiesTextureTweenMixin
{
    private static final String APPLY = "apply(Lmchorse/bbs_mod/film/replays/tracks/TrackContext;Lmchorse/bbs_mod/film/replays/tracks/TrackId;Lmchorse/bbs_mod/utils/keyframes/KeyframeChannel;FF)V";

    @Inject(method = APPLY, at = @At("HEAD"))
    private static void bbsppp$beginTextureTweenContext(TrackContext context, TrackId track, KeyframeChannel<?> channel,
                                                        float tick, float blend, CallbackInfo ci)
    {
        if (!bbsppp$isModelTextureChannel(track.toKey()))
        {
            return;
        }

        BaseValueBasic<?> property = FormUtils.getProperty(context.root(), track.toKey());

        if (property != null && property.getParent() instanceof ModelForm modelForm)
        {
            TextureTweenContext.begin(modelForm);
        }
    }

    @Inject(method = APPLY, at = @At("RETURN"))
    private static void bbsppp$endTextureTweenContext(TrackContext context, TrackId track, KeyframeChannel<?> channel,
                                                     float tick, float blend, CallbackInfo ci)
    {
        if (bbsppp$isModelTextureChannel(track.toKey()))
        {
            TextureTweenContext.end();
        }
    }

    @Inject(method = "fromData", at = @At("TAIL"))
    private void bbsppp$migrateLegacyTextureTweenTrack(BaseType data, CallbackInfo ci)
    {
        LegacyTextureTweenTrackMigration.migrate((FormProperties) (Object) this);
    }

    private static boolean bbsppp$isModelTextureChannel(String id)
    {
        return "texture".equals(id) || id.endsWith("/texture");
    }
}
