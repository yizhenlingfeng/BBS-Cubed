package gbeic.bbsplusplus.clips;

import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueDouble;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.clips.Clips;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Repeats the contiguous camera-clip range directly underneath this clip.
 */
public class ReplayClip extends CameraClip
{
    private static final double END_EPSILON = 0.0001D;

    public final ValueBoolean reverse = new ValueBoolean("reverse", false);
    public final ValueDouble speed = new ValueDouble("speed", 1D, 0.05D, 16D);
    public final ValueInt propagationRange = new ValueInt("propagation_range", 3, 0, Integer.MAX_VALUE);

    public ReplayClip()
    {
        this.add(this.reverse);
        this.add(this.speed);
        this.add(this.propagationRange);
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        int sourceDuration = this.findSourceDuration(context);

        if (sourceDuration <= 0)
        {
            return;
        }

        double localTime = context.relativeTick + context.transition;

        /* The source segment keeps the original playback. Only its overflow is
         * sampled again, so reverse/speed never reverses the first pass below. */
        if (localTime < sourceDuration)
        {
            return;
        }

        double sourceOffset = mapSourceOffset(
            localTime - sourceDuration,
            sourceDuration,
            this.speed.get(),
            this.reverse.get()
        );

        double sourceTime = this.tick.get() + sourceOffset;
        int sourceTick = (int) Math.floor(sourceTime);
        float sourceTransition = (float) (sourceTime - sourceTick);

        context.applyUnderneath(sourceTick, sourceTransition, position,
            (Predicate<Clip>) this::isReplayableClip);
    }

    /**
     * Resolve the camera replay which is currently resampling the timeline.
     * Replay editor data uses the same absolute film tick, so the returned
     * time can be shared with the film controller for keyframe evaluation.
     */
    public static PlaybackTime resolvePlaybackTime(Clips clips, int timelineTick, float transition)
    {
        if (clips == null)
        {
            return PlaybackTime.original(timelineTick, transition);
        }

        ReplayClip selected = null;
        int selectedDuration = 0;

        for (Clip clip : clips.get())
        {
            if (!(clip instanceof ReplayClip replay)
                || !replay.enabled.get()
                || !replay.isInside(timelineTick))
            {
                continue;
            }

            int sourceDuration = replay.findSourceDuration(clips);
            double localTime = timelineTick - replay.tick.get() + transition;

            if (sourceDuration <= 0 || localTime < sourceDuration)
            {
                continue;
            }

            if (selected == null || replay.layer.get() > selected.layer.get())
            {
                selected = replay;
                selectedDuration = sourceDuration;
            }
        }

        if (selected == null)
        {
            return PlaybackTime.original(timelineTick, transition);
        }

        double localTime = timelineTick - selected.tick.get() + transition;
        double sourceOffset = mapSourceOffset(
            localTime - selectedDuration,
            selectedDuration,
            selected.speed.get(),
            selected.reverse.get()
        );
        double sourceTime = selected.tick.get() + sourceOffset;
        int sourceTick = (int) Math.floor(sourceTime);
        float sourceTransition = (float) (sourceTime - sourceTick);

        return new PlaybackTime(sourceTick, sourceTransition, true, selected.reverse.get());
    }

    int findSourceDuration(ClipContext context)
    {
        return this.findSourceDuration(context == null ? null : context.clips);
    }

    int findSourceDuration(Clips clips)
    {
        if (clips == null)
        {
            return 0;
        }

        int sourceStart = this.tick.get();
        int replayEnd = sourceStart + this.duration.get();
        List<Range> ranges = new ArrayList<>();

        for (Clip clip : clips.get())
        {
            if (clip == this
                || clip instanceof ReplayClip
                || !AudioClip.NO_AUDIO.test(clip)
                || !clip.enabled.get()
                || !this.isReplayableClip(clip))
            {
                continue;
            }

            int start = Math.max(sourceStart, clip.tick.get());
            int end = Math.min(replayEnd, clip.tick.get() + clip.duration.get());

            if (start < end)
            {
                ranges.add(new Range(start, end));
            }
        }

        ranges.sort(Comparator.comparingInt(Range::start));

        int sourceEnd = sourceStart;

        for (Range range : ranges)
        {
            if (range.start() > sourceEnd)
            {
                break;
            }

            sourceEnd = Math.max(sourceEnd, range.end());
        }

        return sourceEnd - sourceStart;
    }

    private boolean isReplayableClip(Clip clip)
    {
        int layerDistance = this.layer.get() - clip.layer.get();

        return clip != this
            && !(clip instanceof ReplayClip)
            && AudioClip.NO_AUDIO.test(clip)
            && layerDistance > 0
            && layerDistance <= this.propagationRange.get();
    }

    static double positiveModulo(double value, double divisor)
    {
        return ReplayTimeMath.positiveModulo(value, divisor);
    }

    static double mapSourceOffset(double localTime, int sourceDuration, double speed, boolean reverse)
    {
        return ReplayTimeMath.mapSourceOffset(localTime, sourceDuration, speed, reverse, END_EPSILON);
    }

    @Override
    protected Clip create()
    {
        return new ReplayClip();
    }

    private record Range(int start, int end)
    {}

    public record PlaybackTime(int tick, float transition, boolean resampled, boolean reversed)
    {
        private static PlaybackTime original(int tick, float transition)
        {
            return new PlaybackTime(tick, transition, false, false);
        }
    }
}
