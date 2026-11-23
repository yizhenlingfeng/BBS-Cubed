package gbeic.bbsplusplus.timeline;

import mchorse.bbs_mod.cubic.animation.ActionConfig;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionTimelineEvaluatorTest
{
    @Test
    void emptyOverlaysDoNotCountAsConfiguredActions()
    {
        ActionsConfig config = new ActionsConfig();

        assertFalse(ActionTimelineEvaluator.hasConfiguredActions(null));
        assertFalse(ActionTimelineEvaluator.hasConfiguredActions(config));

        config.actions.put("idle", new ActionConfig(""));

        assertFalse(ActionTimelineEvaluator.hasConfiguredActions(config));
    }

    @Test
    void explicitlyConfiguredActionsRemainEnabled()
    {
        ActionsConfig config = new ActionsConfig();

        config.actions.put("idle", new ActionConfig("idle"));

        assertTrue(ActionTimelineEvaluator.hasConfiguredActions(config));
    }
}
