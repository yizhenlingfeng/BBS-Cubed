package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.BBSPlusPlusMod;
import gbeic.bbsplusplus.settings.CMLSettings;
import gbeic.bbsplusplus.timeline.ActionTimelineEvaluator;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = KeyframeSegment.class, remap = false)
public abstract class KeyframeSegmentCMLMixin<T>
{
    private static boolean bbspp_cml$loggedEvaluationFailure;

    @Inject(method = "createInterpolated", at = @At("RETURN"), cancellable = true, remap = false)
    private void bbspp_cml$evaluateOverlayTimeline(CallbackInfoReturnable<T> cir)
    {
        if (!CMLSettings.isSnowActionsEnabled()
            || !(cir.getReturnValue() instanceof ActionsConfig actions))
        {
            return;
        }

        try
        {
            cir.setReturnValue((T) ActionTimelineEvaluator.evaluate(
                (KeyframeSegment<ActionsConfig>) (KeyframeSegment<?>) (Object) this,
                actions
            ));
        }
        catch (Exception e)
        {
            /* 求值链的任何异常都不得沿着 createInterpolated 传进 BBS 的动画/影片/物理
             * 采样管线——物理模组一次采样失败就会把该 tick 的骨骼通道记成 SILENT，
             * 渲染从此与刚体脱钩（模型悬空、碰撞盒散架）。这里回退 vanilla 插值结果，
             * 并只报告一次，避免 catch-up 期间刷屏。 */
            if (!bbspp_cml$loggedEvaluationFailure)
            {
                bbspp_cml$loggedEvaluationFailure = true;

                BBSPlusPlusMod.LOGGER.error(
                    "snow_actions: actions timeline evaluation failed; falling back to vanilla interpolation for this and any future failures", e);
            }
        }
    }
}
