package gbeic.bbsplusplus.ui.forms;

import mchorse.bbs_mod.forms.states.AnimationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnimationBakeProgressTest
{
    @Test
    void tracksQueuedRunningAndCompletedStates()
    {
        AnimationState state = new AnimationState("test");

        assertEquals(-1F, AnimationBakeProgress.get(state));
        AnimationBakeProgress.queued(state);
        assertEquals(0F, AnimationBakeProgress.get(state));
        AnimationBakeProgress.update(state, 0.5F);
        assertEquals(0.5F, AnimationBakeProgress.get(state));
        AnimationBakeProgress.update(state, 2F);
        assertEquals(1F, AnimationBakeProgress.get(state));
        AnimationBakeProgress.clear(state);
        assertEquals(-1F, AnimationBakeProgress.get(state));
    }
}
