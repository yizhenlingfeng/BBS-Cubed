package gbeic.bbsplusplus.timeline;

import gbeic.bbsplusplus.api.ActionTimelineConfig;
import gbeic.bbsplusplus.settings.CMLSettings;
import mchorse.bbs_mod.cubic.animation.ActionConfig;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.AnimationPart;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Stateless evaluation of linked Actions Overlay keyframe pairs. */
public final class ActionTimelineEvaluator
{
    private static final float EPSILON = 0.0001F;

    private ActionTimelineEvaluator()
    {}

    /**
     * Bedrock 动画允许省略 animation_length。此时 BBS 会得到 0 tick，
     * 但动作关键帧本身仍然包含真实时长，因此用最后一个动画关键帧补齐。
     */
    public static int getEffectiveAnimationLength(Animation animation)
    {
        if (animation == null)
        {
            return 1;
        }

        float lastKeyframe = 0F;

        for (AnimationPart part : animation.parts.values())
        {
            for (KeyframeChannel<?> channel : part.channels)
            {
                List<? extends Keyframe<?>> keyframes = channel.getKeyframes();

                if (!keyframes.isEmpty())
                {
                    lastKeyframe = Math.max(lastKeyframe, keyframes.get(keyframes.size() - 1).getTick());
                }
            }
        }

        return Math.max(1, Math.max(animation.getLengthInTicks(), (int) Math.ceil(lastKeyframe)));
    }

    /**
     * 时间线接管 {@code ActionPlayback.getTick} 时的播放进度求值（纯函数，来自
     * ActionPlaybackMixin 下沉）。返回值即动画帧位置。
     */
    public static float computeSeekFrame(float length, float timelineFrame, float speed, int tick, boolean loop, float loopInterval)
    {
        float frame = timelineFrame * speed + tick;

        if (speed < 0F)
        {
            frame += length;
        }

        if (loop && length > 0F)
        {
            float intervalTicks = Math.max(0F, loopInterval);
            float intervalFrames = intervalTicks * Math.max(0.0001F, Math.abs(speed));
            float cycle = length + intervalFrames;

            frame %= cycle;

            if (frame < 0F)
            {
                frame += cycle;
            }

            if (frame > length)
            {
                /* 正播停在末帧，倒播停在首帧。间隔按时间线 tick 计。 */
                frame = speed < 0F ? 0F : length;
            }
        }
        else
        {
            frame = Math.max(0F, Math.min(length, frame));
        }

        return frame;
    }

    /**
     * 时间线关键帧插值（来自 ActionsConfigKeyframeFactoryMixin 下沉）：对 clip
     * 匹配端点做动画帧插值。返回 null 表示没有时间线驱动的动作，不接管原插值结果。
     */
    public static ActionsConfig interpolateTimelineFrames(ActionsConfig a, ActionsConfig b, IInterp interpolation, float x)
    {
        if (!CMLSettings.isSnowActionsEnabled())
        {
            return null;
        }

        ActionsConfig result = null;

        for (Map.Entry<String, ActionConfig> entry : a.actions.entrySet())
        {
            ActionConfig left = entry.getValue();
            ActionConfig right = b.actions.get(entry.getKey());
            ActionTimelineConfig leftTimeline = (ActionTimelineConfig) left;

            if (!leftTimeline.bbspp_cml$isTimelineDriven())
            {
                continue;
            }

            ActionTimelineConfig rightTimeline = right == null ? null : (ActionTimelineConfig) right;
            boolean matchingEndpoint = rightTimeline != null
                && rightTimeline.bbspp_cml$isTimelineDriven()
                && Objects.equals(left.name, right.name)
                && Objects.equals(leftTimeline.bbspp_cml$getClipId(), rightTimeline.bbspp_cml$getClipId());

            if (result == null)
            {
                result = new ActionsConfig();
                result.copy(a);
            }

            if (!matchingEndpoint)
            {
                if (leftTimeline.bbspp_cml$isOverlayTimeline() && x > 0F)
                {
                    result.actions.put(entry.getKey(), new ActionConfig(""));
                }

                continue;
            }

            ActionTimelineConfig output = (ActionTimelineConfig) result.actions.get(entry.getKey());
            float frame = interpolation.interpolate(
                leftTimeline.bbspp_cml$getTimelineFrame(),
                rightTimeline.bbspp_cml$getTimelineFrame(),
                x
            );

            output.bbspp_cml$setTimelineFrame(frame);
        }

        return result;
    }

    public static ActionsConfig evaluate(KeyframeSegment<ActionsConfig> segment, ActionsConfig result)
    {
        if (!CMLSettings.isSnowActionsEnabled())
        {
            return result;
        }

        if (!(segment.a.getParent() instanceof KeyframeChannel<?> rawChannel))
        {
            return result;
        }

        KeyframeChannel<ActionsConfig> channel = (KeyframeChannel<ActionsConfig>) rawChannel;
        float tick = segment.a.getTick() + segment.offset;
        Map<String, List<Clip>> clips = collectClips(channel);

        for (Map.Entry<String, List<Clip>> entry : clips.entrySet())
        {
            evaluateAction(result, entry.getKey(), entry.getValue(), tick);
        }

        return result;
    }

    private static void evaluateAction(ActionsConfig result, String actionKey, List<Clip> clips, float tick)
    {
        clips.sort(Comparator.comparingDouble((clip) -> clip.startTick));
        Clip primary = null;

        for (Clip clip : clips)
        {
            if (tick + EPSILON < clip.startTick || tick + EPSILON >= clip.stopTick)
            {
                continue;
            }

            float fade = Math.max(0F, clip.config.fade);
            boolean active = tick <= clip.endTick + EPSILON
                || loopsBeyond(clip)
                || (fade > 0F && tick < clip.endTick + fade);

            if (active)
            {
                primary = clip;
            }
        }

        ActionConfig current = result.actions.get(actionKey);

        if (primary == null)
        {
            if (current != null)
            {
                ActionTimelineConfig timeline = (ActionTimelineConfig) current;

                if (timeline.bbspp_cml$isTimelineDriven()
                    && timeline.bbspp_cml$isOverlayTimeline())
                {
                    result.actions.put(actionKey, new ActionConfig(""));
                }
            }

            result.actions.remove(ActionTimelineConfig.bbspp_TRANSITION_PREFIX + actionKey);

            return;
        }

        ActionConfig primaryConfig = primary.config.copy();
        ActionTimelineConfig primaryTimeline = (ActionTimelineConfig) primaryConfig;
        float weight = weight(primary, tick);

        primaryTimeline.bbspp_cml$setTimelineFrame(frame(primary, tick));
        primaryTimeline.bbspp_cml$setTimelineWeight(weight);
        result.actions.put(actionKey, primaryConfig);

        String transitionKey = ActionTimelineConfig.bbspp_TRANSITION_PREFIX + actionKey;
        Clip previous = previous(clips, primary);
        float primaryFade = Math.max(0F, primary.config.fade);

        if (previous == null || primaryFade <= 0F || tick >= primary.startTick + primaryFade)
        {
            result.actions.remove(transitionKey);

            return;
        }

        if (previous.stopTick + EPSILON < primary.startTick)
        {
            result.actions.remove(transitionKey);

            return;
        }

        float previousWeight = loopsBeyond(previous) ? 1F : weight(previous, tick);

        if (previousWeight <= 0F)
        {
            result.actions.remove(transitionKey);

            return;
        }

        ActionConfig transition = previous.config.copy();
        ActionTimelineConfig transitionTimeline = (ActionTimelineConfig) transition;

        transitionTimeline.bbspp_cml$setTimelineFrame(frame(previous, tick));
        transitionTimeline.bbspp_cml$setTimelineWeight(previousWeight);
        result.actions.put(transitionKey, transition);
    }

    private static Clip previous(List<Clip> clips, Clip primary)
    {
        Clip previous = null;

        for (Clip clip : clips)
        {
            if (clip == primary)
            {
                break;
            }

            if (clip.startTick < primary.startTick)
            {
                previous = clip;
            }
        }

        return previous;
    }

    private static float weight(Clip clip, float tick)
    {
        float fade = Math.max(0F, clip.config.fade);

        if (fade <= 0F)
        {
            return !loopsBeyond(clip) && tick > clip.endTick + EPSILON ? 0F : 1F;
        }

        float fadeIn = clamp((tick - clip.startTick) / fade);

        if (loopsBeyond(clip) || tick <= clip.endTick)
        {
            return fadeIn;
        }

        return Math.min(fadeIn, clamp(1F - (tick - clip.endTick) / fade));
    }

    private static float frame(Clip clip, float tick)
    {
        float span = clip.endTick - clip.startTick;
        float x;

        if (tick <= clip.endTick + EPSILON)
        {
            x = clamp((tick - clip.startTick) / span);
        }
        else if (loopsBeyond(clip))
        {
            float rate = (clip.endFrame - clip.startFrame) / span;

            return clip.endFrame + (tick - clip.endTick) * rate;
        }
        else
        {
            x = 1F;
        }

        return clip.start.getInterpolation().interpolate(clip.startFrame, clip.endFrame, x);
    }

    private static Map<String, List<Clip>> collectClips(KeyframeChannel<ActionsConfig> channel)
    {
        Map<String, Map<String, List<Marker>>> grouped = new LinkedHashMap<>();

        for (Keyframe<ActionsConfig> keyframe : channel.getKeyframes())
        {
            for (Map.Entry<String, ActionConfig> entry : keyframe.getValue().actions.entrySet())
            {
                if (entry.getKey().startsWith(ActionTimelineConfig.bbspp_TRANSITION_PREFIX))
                {
                    continue;
                }

                ActionTimelineConfig timeline = (ActionTimelineConfig) entry.getValue();

                if (!timeline.bbspp_cml$isTimelineDriven())
                {
                    continue;
                }

                grouped.computeIfAbsent(entry.getKey(), (key) -> new LinkedHashMap<>())
                    .computeIfAbsent(timeline.bbspp_cml$getClipId(), (key) -> new ArrayList<>())
                    .add(new Marker(keyframe, entry.getValue()));
            }
        }

        Map<String, List<Clip>> output = new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, List<Marker>>> action : grouped.entrySet())
        {
            List<Clip> clips = new ArrayList<>();

            for (List<Marker> markers : action.getValue().values())
            {
                Clip clip = createClip(markers);

                if (clip != null)
                {
                    clip.stopTick = findStopTick(channel, action.getKey(), clip);
                    clips.add(clip);
                }
            }

            if (!clips.isEmpty())
            {
                output.put(action.getKey(), clips);
            }
        }

        return output;
    }

    private static Clip createClip(List<Marker> markers)
    {
        Marker first = null;
        Marker second = null;
        float bestFrameSpan = -1F;
        float bestTickSpan = Float.MAX_VALUE;

        for (int i = 0; i < markers.size() - 1; i++)
        {
            Marker a = markers.get(i);

            for (int j = i + 1; j < markers.size(); j++)
            {
                Marker b = markers.get(j);
                float tickSpan = Math.abs(a.keyframe.getTick() - b.keyframe.getTick());

                if (tickSpan <= EPSILON
                    || a.config.name == null
                    || a.config.name.isEmpty()
                    || !Objects.equals(a.config.name, b.config.name))
                {
                    continue;
                }

                float aFrame = ((ActionTimelineConfig) a.config).bbspp_cml$getTimelineFrame();
                float bFrame = ((ActionTimelineConfig) b.config).bbspp_cml$getTimelineFrame();
                float frameSpan = Math.abs(aFrame - bFrame);

                if (frameSpan > bestFrameSpan + EPSILON
                    || (Math.abs(frameSpan - bestFrameSpan) <= EPSILON && tickSpan < bestTickSpan))
                {
                    first = a;
                    second = b;
                    bestFrameSpan = frameSpan;
                    bestTickSpan = tickSpan;
                }
            }
        }

        if (first == null || second == null)
        {
            return null;
        }

        if (first.keyframe.getTick() > second.keyframe.getTick())
        {
            Marker swap = first;

            first = second;
            second = swap;
        }

        return new Clip(first, second);
    }

    private static float findStopTick(KeyframeChannel<ActionsConfig> channel, String actionKey, Clip clip)
    {
        for (Keyframe<ActionsConfig> keyframe : channel.getKeyframes())
        {
            if (keyframe.getTick() <= clip.endTick + EPSILON)
            {
                continue;
            }

            /* 结束点之后，同一关键帧轨道上的第一个点就是循环边界。
             * 插入或复制关键帧会继承 clip ID，不能因此跳过这个边界。 */
            return keyframe.getTick();
        }

        return Float.POSITIVE_INFINITY;
    }

    private static boolean loopsBeyond(Clip clip)
    {
        return clip.config.loop
            && ((ActionTimelineConfig) clip.config).bbspp_cml$isLoopBeyond();
    }

    private static float clamp(float value)
    {
        return Math.max(0F, Math.min(1F, value));
    }

    private record Marker(Keyframe<ActionsConfig> keyframe, ActionConfig config)
    {}

    private static class Clip
    {
        public final Keyframe<ActionsConfig> start;
        public final float startTick;
        public final float endTick;
        public final float startFrame;
        public final float endFrame;
        public final String clipId;
        public final ActionConfig config;
        public float stopTick;

        public Clip(Marker start, Marker end)
        {
            this.start = start.keyframe;
            this.startTick = start.keyframe.getTick();
            this.endTick = end.keyframe.getTick();
            this.startFrame = ((ActionTimelineConfig) start.config).bbspp_cml$getTimelineFrame();
            this.endFrame = ((ActionTimelineConfig) end.config).bbspp_cml$getTimelineFrame();
            this.clipId = ((ActionTimelineConfig) start.config).bbspp_cml$getClipId();
            this.config = start.config;
        }
    }
}
