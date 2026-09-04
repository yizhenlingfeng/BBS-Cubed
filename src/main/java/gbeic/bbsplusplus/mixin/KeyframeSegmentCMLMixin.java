package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.settings.CMLSettings;
import gbeic.bbsplusplus.timeline.ActionTimelineEvaluator;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = KeyframeSegment.class, remap = false)
public abstract class KeyframeSegmentCMLMixin<T>
{
    @Shadow
    public Keyframe<T> a;

    @Shadow
    public Keyframe<T> b;

    @Shadow
    public float offset;

    @Inject(method = "createInterpolated", at = @At("RETURN"), cancellable = true, remap = false)
    private void bbspp_cml$evaluateOverlayTimeline(CallbackInfoReturnable<T> cir)
    {
        if (!CMLSettings.isSnowActionsEnabled()
            || !(cir.getReturnValue() instanceof ActionsConfig actions))
        {
            return;
        }

        cir.setReturnValue((T) ActionTimelineEvaluator.evaluate(
            (KeyframeSegment<ActionsConfig>) (KeyframeSegment<?>) (Object) this,
            actions
        ));
    }
}
