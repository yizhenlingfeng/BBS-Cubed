package gbeic.bbsplusplus.clips;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.clips.Clips;

/**
 * Carries one camera replay time through the film controller's update/render
 * pass. The bridge is deliberately thread-local because the BBS controller
 * evaluates a frame synchronously and Replay.getTick has no context argument.
 */
public final class ReplayTimeBridge
{
    private static final ThreadLocal<ReplayClip.PlaybackTime> TIME = new ThreadLocal<>();

    private ReplayTimeBridge()
    {}

    public static void begin(Clips clips, int timelineTick, float transition)
    {
        TIME.set(ReplayClip.resolvePlaybackTime(clips, timelineTick, transition));
    }

    public static void clear()
    {
        TIME.remove();
    }

    public static int mapTick(Replay replay, int tick)
    {
        ReplayClip.PlaybackTime time = TIME.get();

        if (time == null || !time.resampled())
        {
            return tick;
        }

        int mappedTick = time.tick();
        int looping = replay == null ? 0 : replay.looping.get();

        return applyLooping(mappedTick, looping);
    }

    public static int applyLooping(int mappedTick, int looping)
    {
        return ReplayTimeMath.applyLooping(mappedTick, looping);
    }

    public static float mapTransition(float transition)
    {
        ReplayClip.PlaybackTime time = TIME.get();

        return time == null || !time.resampled() ? transition : time.transition();
    }

    public static boolean isResampled()
    {
        ReplayClip.PlaybackTime time = TIME.get();

        return time != null && time.resampled();
    }
}
