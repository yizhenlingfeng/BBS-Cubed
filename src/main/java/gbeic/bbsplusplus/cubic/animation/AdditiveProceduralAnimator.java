package gbeic.bbsplusplus.cubic.animation;

import gbeic.bbsplusplus.api.ActionTimelineConfig;
import mchorse.bbs_mod.cubic.IModelInstance;
import mchorse.bbs_mod.cubic.animation.ActionPlayback;
import mchorse.bbs_mod.cubic.animation.ActionConfig;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.animation.ProceduralAnimator;
import mchorse.bbs_mod.forms.entities.IEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Adds only the procedural model's configured pre/post action animations. */
public class AdditiveProceduralAnimator extends ProceduralAnimator
{
    private final List<ActionPlayback> bbspp_cml$transitionActions = new ArrayList<>();

    @Override
    public void setup(IModelInstance model, ActionsConfig configs, boolean reset)
    {
        super.setup(model, configs, reset);
        this.bbspp_cml$transitionActions.clear();

        for (Map.Entry<String, ActionConfig> entry : configs.actions.entrySet())
        {
            if (!entry.getKey().startsWith(ActionTimelineConfig.bbspp_TRANSITION_PREFIX))
            {
                continue;
            }

            ActionPlayback playback = this.createAction(null, entry.getValue(), true);

            if (playback != null)
            {
                this.bbspp_cml$transitionActions.add(playback);
            }
        }
    }

    @Override
    public void applyActions(IEntity target, IModelInstance armature, float transition)
    {
        if (target == null)
        {
            return;
        }

        for (ActionPlayback action : this.bbspp_cml$transitionActions)
        {
            action.apply(target, armature.getModel(), transition, this.getWeight(action), true);
        }

        if (this.isActive(this.basePre))
        {
            this.basePre.apply(target, armature.getModel(), transition, this.getWeight(this.basePre), true);
        }

        if (this.isActive(this.basePost))
        {
            this.basePost.postApply(target, armature.getModel(), transition);
        }
    }

    private boolean isActive(ActionPlayback action)
    {
        if (action == null || !(action.config instanceof ActionTimelineConfig timeline))
        {
            return action != null;
        }

        if (!timeline.bbspp_cml$isTimelineDriven())
        {
            return true;
        }

        return true;
    }

    private float getWeight(ActionPlayback action)
    {
        if (action == null || !(action.config instanceof ActionTimelineConfig timeline)
            || !timeline.bbspp_cml$isTimelineDriven())
        {
            return 1F;
        }

        return Math.max(0F, Math.min(1F, timeline.bbspp_cml$getTimelineWeight()));
    }
}
