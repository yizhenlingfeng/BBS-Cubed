package gbeic.bbsplusplus.util;

import gbeic.bbsplusplus.api.IBbsppStepChannel;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.AnimationInterpolation;
import mchorse.bbs_mod.cubic.data.animation.AnimationPart;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.fabricmc.loader.api.FabricLoader;

import java.util.HashMap;
import java.util.Map;

/**
 * "动画转姿势关键帧"的插值继承:从动画各通道在某 tick 的关键帧上收集插值
 * 类型,映射成 pose 关键帧的插值 —— Blockbench 步阶(step)对应常量 CONST,
 * 平滑(catmullrom,解析后为 HERMITE)按用户约定映射正弦淡出 SINE_OUT,
 * 贝塞尔(easing:"bezier",本插件扩展标记)在装了 BBS-PoseCurve-Addon
 * (让 Pose 支持贝塞尔编辑)时映射 BEZIER,否则退回线性。
 */
public class AnimationInterpInheritance
{
    public static final String POSE_CURVE_MOD_ID = "bbs-posecurve-addon";

    private static Boolean poseCurveLoaded;

    /**
     * 客户端初始化时调用:往 GeckoLib easing 名表注册 "bezier",让
     * {@code GeoAnimationParser} 能读回本插件导出器写出的贝塞尔标记
     * (原生 Blockbench 会忽略该字段,不影响其导入)。
     */
    public static void registerBezierEasing()
    {
        AnimationInterpolation.GECKO_LIB_NAMES.putIfAbsent("bezier", Interpolations.BEZIER);
    }

    public static boolean isPoseCurveLoaded()
    {
        if (poseCurveLoaded == null)
        {
            poseCurveLoaded = FabricLoader.getInstance().isModLoaded(POSE_CURVE_MOD_ID);
        }

        return poseCurveLoaded;
    }

    /**
     * 收集动画全部通道在该 tick 上关键帧的插值,取出现最多的非线性插值并
     * 映射为 pose 关键帧插值;全线性(或该 tick 无关键帧)返回 null 表示不动。
     *
     * <p>步阶到达帧例外:它自身的插值是 {@link Interpolations#CONST},表示"进入该帧的段保持"。
     * pose 通道的插值描述的是"从本帧出发的段",直接搬过去会把离开步阶帧的整段压成常量,
     * 动画看起来卡住一段再跳。此时改取<b>下一帧</b>的插值,那才是离开本帧那一段的真实类型。</p>
     */
    public static IInterp pickInterp(Animation animation, float tick)
    {
        float sample = isStepTickAt(animation, tick) ? nextKeyframeTick(animation, tick) : tick;
        Map<IInterp, Integer> counts = new HashMap<>();

        for (AnimationPart part : animation.parts.values())
        {
            for (KeyframeChannel<MolangExpression> channel : part.channels)
            {
                for (Keyframe<MolangExpression> keyframe : channel.getKeyframes())
                {
                    if (Math.abs(keyframe.getTick() - sample) < 0.001F)
                    {
                        IInterp interp = keyframe.getInterpolation().getInterp();

                        if (interp != null && interp != Interpolations.LINEAR)
                        {
                            counts.merge(interp, 1, Integer::sum);
                        }
                    }
                }
            }
        }

        IInterp best = null;
        int bestCount = 0;

        for (Map.Entry<IInterp, Integer> entry : counts.entrySet())
        {
            if (entry.getValue() > bestCount)
            {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }

        return best == null ? null : map(best);
    }

    /** 该 tick 是否为任一骨骼通道上的步阶到达帧。 */
    private static boolean isStepTickAt(Animation animation, float tick)
    {
        for (AnimationPart part : animation.parts.values())
        {
            for (KeyframeChannel<MolangExpression> channel : part.channels)
            {
                if (channel instanceof IBbsppStepChannel steps && steps.bbspp$isStepTick(tick))
                {
                    return true;
                }
            }
        }

        return false;
    }

    /** 严格大于该 tick 的最近关键帧 tick；没有下一帧时退回原 tick。 */
    private static float nextKeyframeTick(Animation animation, float tick)
    {
        float next = Float.MAX_VALUE;

        for (AnimationPart part : animation.parts.values())
        {
            for (KeyframeChannel<MolangExpression> channel : part.channels)
            {
                for (Keyframe<MolangExpression> keyframe : channel.getKeyframes())
                {
                    float keyframeTick = keyframe.getTick();

                    if (keyframeTick > tick + 0.001F && keyframeTick < next)
                    {
                        next = keyframeTick;
                    }
                }
            }
        }

        return next == Float.MAX_VALUE ? tick : next;
    }

    private static IInterp map(IInterp interp)
    {
        if (interp == Interpolations.HERMITE)
        {
            return Interpolations.SINE_OUT;
        }

        if (interp == Interpolations.BEZIER)
        {
            return isPoseCurveLoaded() ? Interpolations.BEZIER : Interpolations.LINEAR;
        }

        return interp;
    }
}
