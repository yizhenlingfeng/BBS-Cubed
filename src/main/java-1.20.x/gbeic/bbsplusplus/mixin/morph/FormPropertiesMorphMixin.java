package gbeic.bbsplusplus.mixin.morph;

import gbeic.bbsplusplus.client.morph.ModelMorphRuntime;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 每帧应用属性前清空形变状态，保证 Morph 严格跟随当前时间点。
 *
 * <p>没有这一层时，删除关键帧、把播放头移出形变区间或关闭 Morph 都可能残留上一帧的状态。</p>
 */
@Mixin(value = FormProperties.class, remap = false)
public class FormPropertiesMorphMixin
{
    @Inject(method = "apply(Lmchorse/bbs_mod/film/replays/tracks/TrackContext;FF)V", at = @At("HEAD"), require = 1)
    private void bbsppp$clearModelMorph(TrackContext context, float tick, float blend, CallbackInfo ci)
    {
        if (context != null)
        {
            ModelMorphRuntime.clearAll(context.root());
        }
    }
}
