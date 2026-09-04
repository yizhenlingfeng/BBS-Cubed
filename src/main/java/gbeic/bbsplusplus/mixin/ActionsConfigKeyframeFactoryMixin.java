package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.timeline.ActionTimelineEvaluator;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.keyframes.factories.ActionsConfigKeyframeFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 时间线关键帧插值薄壳：插值业务在
 * {@link ActionTimelineEvaluator#interpolateTimelineFrames}，返回 null 表示不接管。
 */
@Mixin(value = ActionsConfigKeyframeFactory.class, remap = false)
public abstract class ActionsConfigKeyframeFactoryMixin
{
    @Inject(
        method = "interpolate(Lmchorse/bbs_mod/cubic/animation/ActionsConfig;Lmchorse/bbs_mod/cubic/animation/ActionsConfig;Lmchorse/bbs_mod/cubic/animation/ActionsConfig;Lmchorse/bbs_mod/cubic/animation/ActionsConfig;Lmchorse/bbs_mod/utils/interps/IInterp;F)Lmchorse/bbs_mod/cubic/animation/ActionsConfig;",
        at = @At("RETURN"),
        cancellable = true,
        remap = false
    )
    private void bbspp_cml$interpolateTimelineFrames(
        ActionsConfig preA,
        ActionsConfig a,
        ActionsConfig b,
        ActionsConfig postB,
        IInterp interpolation,
        float x,
        CallbackInfoReturnable<ActionsConfig> cir)
    {
        ActionsConfig result = ActionTimelineEvaluator.interpolateTimelineFrames(a, b, interpolation, x);

        if (result != null)
        {
            cir.setReturnValue(result);
        }
    }
}
