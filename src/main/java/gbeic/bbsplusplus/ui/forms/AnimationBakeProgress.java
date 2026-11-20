package gbeic.bbsplusplus.ui.forms;

import mchorse.bbs_mod.forms.states.AnimationState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AnimationBakeProgress
{
    private static final Map<AnimationState, Progress> ENTRIES = new ConcurrentHashMap<>();

    private AnimationBakeProgress()
    {}

    public static void queued(AnimationState state)
    {
        ENTRIES.computeIfAbsent(state, key -> new Progress()).value = 0F;
    }

    public static void update(AnimationState state, float value)
    {
        ENTRIES.computeIfAbsent(state, key -> new Progress()).value = Math.max(0F, Math.min(1F, value));
    }

    public static float get(AnimationState state)
    {
        Progress progress = ENTRIES.get(state);

        return progress == null ? -1F : progress.value;
    }

    public static void clear(AnimationState state)
    {
        ENTRIES.remove(state);
    }

    private static final class Progress
    {
        private volatile float value;
    }
}
