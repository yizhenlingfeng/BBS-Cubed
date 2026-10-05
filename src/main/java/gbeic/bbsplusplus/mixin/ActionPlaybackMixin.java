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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    @Shadow
    private int fade;

    @Shadow
    private ActionPlayback.Fade fading;

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
            float seek = ActionTimelineEvaluator.computeSeekFrame(
                ActionTimelineEvaluator.getEffectiveAnimationLength(this.action),
                timeline.bbspp_cml$getTimelineFrame(),
                this.config.speed,
                this.config.tick,
                this.config.loop,
                timeline.bbspp_cml$getLoopInterval()
            );

            /* NaN 帧会直接进入 armature.apply 的通道插值，再被物理模组采样成
             * 不可用目标——宁可靠边（0），不放坏帧出去。 */
            cir.setReturnValue(Float.isFinite(seek) ? seek : 0F);
        }
    }

    @Inject(method = "update", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp_cml$timelineSkipsPlaybackClock(CallbackInfo ci)
    {
        if (!CMLSettings.isSnowActionsEnabled()
            || !((ActionTimelineConfig) this.config).bbspp_cml$isTimelineDriven())
        {
            return;
        }

        /* 帧进度完全由轨道接管：vanilla 的 ticks 累计对 timeline 动作毫无意义
         * （getTick 不读它），却会在超过动画时长后触发非循环动作的 fadeOut——
         * blend 先被打到 0、淡出结束后又作为 active 回到 1，姿势在原地抖动。
         * 这里跳过整个时钟推进，只保留 fading 计数，让动作切换的 fadeIn/OUT
         * 仍能正常走完。 */
        if (this.fading != ActionPlayback.Fade.FINISHED && this.fade > 0)
        {
            this.fade--;
        }

        ci.cancel();
    }
}
