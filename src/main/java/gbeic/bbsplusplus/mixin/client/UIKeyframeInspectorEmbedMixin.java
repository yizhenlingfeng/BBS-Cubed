package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.ui.film.UIReplayKeyframeEmbedHelper;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 关键帧 inspector 重建后重新 embed 到 editArea 小窗(新 Bug2)。
 * pickKeyframe 是 private,@Inject 不受 private 限制(remap=false,描述符按方法名+签名)。
 */
@Mixin(value = UIKeyframeEditor.class, remap = false)
public abstract class UIKeyframeInspectorEmbedMixin
{
    @Inject(method = "pickKeyframe(Lmchorse/bbs_mod/utils/keyframes/Keyframe;)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$reembedAfterPickKeyframe(Keyframe keyframe, CallbackInfo ci)
    {
        UIReplayKeyframeEmbedHelper.reembedKeyframeInspector((UIKeyframeEditor) (Object) this);
    }
}
