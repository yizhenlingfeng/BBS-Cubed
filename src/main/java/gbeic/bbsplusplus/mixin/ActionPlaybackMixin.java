package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.ActionTimelineConfig;
import gbeic.bbsplusplus.settings.CMLSettings;
import gbeic.bbsplusplus.timeline.ActionTimelineEvaluator;
import mchorse.bbs_mod.cubic.animation.ActionConfig;
import mchorse.bbs_mod.cubic.animation.ActionPlayback;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 时间线驱动的动作以时间线帧接管播放进度，进度换算在
 * {@link ActionTimelineEvaluator#computeSeekFrame} 纯函数中。
 */
@Mixin(value = ActionPlayback.class, remap = false)
public abstract class ActionPlaybackMixin
{
    @Shadow
    public ActionConfig config;

    @Shadow
    public Animation action;

    @Inject(method = "getTick", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp_cml$seekFromTimeline(float transition, CallbackInfoReturnable<Float> cir)
    {
        if (!CMLSettings.isSnowActionsEnabled())
        {
            return;
        }

        ActionTimelineConfig timeline = (ActionTimelineConfig) this.config;

        if (timeline.bbspp_cml$isTimelineDriven())
        {
            cir.setReturnValue(ActionTimelineEvaluator.computeSeekFrame(
                ActionTimelineEvaluator.getEffectiveAnimationLength(this.action),
                timeline.bbspp_cml$getTimelineFrame(),
                this.config.speed,
                this.config.tick,
                this.config.loop,
                timeline.bbspp_cml$getLoopInterval()
            ));
        }
    }
}
