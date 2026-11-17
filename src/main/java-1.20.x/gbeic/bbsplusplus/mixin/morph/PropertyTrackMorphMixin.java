package gbeic.bbsplusplus.mixin.morph;

import gbeic.bbsplusplus.client.morph.ModelMorphRuntime;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 BBS 原生 Model 属性轨道应用时读取当前关键帧段，判断是否开启 Morph。
 *
 * <p>注入目标是 {@code PropertyTrack#apply}：这里同时拿到轨道身份、关键帧通道与当前 tick，
 * 正好可以按「当前关键帧 + 下一关键帧 + 插值进度」直接求出形变状态，
 * 不依赖逐帧累计，因此拖动播放头、倒放、循环都自然正确。</p>
 */
@Mixin(value = mchorse.bbs_mod.film.replays.tracks.behaviours.PropertyTrack.class, remap = false)
public class PropertyTrackMorphMixin
{
    @Inject(method = "apply", at = @At("HEAD"), require = 1)
    private void bbsppp$captureModelMorph(TrackContext context, TrackId track, KeyframeChannel<?> channel,
                                          float tick, float blend, CallbackInfo ci)
    {
        if (context == null || track == null || channel == null || !bbsppp$isModelTrack(track.toKey()))
        {
            return;
        }

        Form form = FormUtils.getForm(context.root(), track.formPath());

        if (!(form instanceof ModelForm modelForm))
        {
            return;
        }

        ModelMorphRuntime.update(modelForm, channel.find(tick));
    }

    private static boolean bbsppp$isModelTrack(String key)
    {
        return key != null && ("model".equals(key) || key.endsWith("/model"));
    }
}
