package gbeic.bbsplusplus.cubic.animation;

import gbeic.bbsplusplus.api.ActionTimelineConfig;
import mchorse.bbs_mod.cubic.IModelInstance;
import mchorse.bbs_mod.cubic.animation.ActionPlayback;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.animation.Animator;
import mchorse.bbs_mod.forms.entities.IEntity;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies the normal action-state pipeline without resetting untouched bones. */
public class AdditiveAnimator extends Animator
{
    private final List<ActionPlayback> bbspp_cml$transitionActions = new ArrayList<>();

    @Override
    public void setup(IModelInstance model, ActionsConfig configs, boolean reset)
    {
        super.setup(model, configs, reset);
        this.bbspp_cml$transitionActions.clear();

        for (Map.Entry<String, mchorse.bbs_mod.cubic.animation.ActionConfig> entry : configs.actions.entrySet())
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
        Set<ActionPlayback> applied = Collections.newSetFromMap(new IdentityHashMap<>());

        for (ActionPlayback action : this.bbspp_cml$transitionActions)
        {
            this.apply(target, armature, transition, action, 1F, applied);
        }

        this.apply(target, armature, transition, this.basePre, 1F, applied);

        if (!this.isTimelineDriven(this.lastActive)
            && this.lastActive != null
            && this.active != null
            && this.active.isFading())
        {
            this.apply(target, armature, transition, this.lastActive, 1F, applied);
        }

        if (!this.isTimelineDriven(this.active) && this.active != null)
        {
            float fade = this.active.isFading() ? this.active.getFadeFactor(transition) : 1F;

            this.apply(target, armature, transition, this.active, fade, applied);
        }

        /* Timeline-driven overlay actions are clips, not entity state slots. */
        for (ActionPlayback action : new ActionPlayback[] {
            this.idle, this.running, this.sprinting, this.crouching, this.crouchingIdle,
            this.dying, this.falling, this.jump1, this.jump2, this.swipe, this.hurt,
            this.land, this.shoot, this.consume
        })
        {
            if (this.isTimelineActive(action))
            {
                this.apply(target, armature, transition, action, 1F, applied);
            }
        }

        this.apply(target, armature, transition, this.basePost, 1F, applied);

        for (ActionPlayback action : this.actions)
        {
            float fade = action.isFading() ? action.getFadeFactor(transition) : 1F;

            this.apply(target, armature, transition, action, fade, applied);
        }
    }

    private boolean isTimelineDriven(ActionPlayback action)
    {
        return action != null
            && action.config instanceof ActionTimelineConfig timeline
            && timeline.bbspp_cml$isTimelineDriven();
    }

    private boolean isTimelineActive(ActionPlayback action)
    {
        if (!this.isTimelineDriven(action))
        {
            return false;
        }

        return true;
    }

    private float getTimelineWeight(ActionPlayback action)
    {
        if (!this.isTimelineDriven(action))
        {
            return 1F;
        }

        float weight = ((ActionTimelineConfig) action.config).bbspp_cml$getTimelineWeight();

        return Math.max(0F, Math.min(1F, weight));
    }

    private void apply(
        IEntity target,
        IModelInstance armature,
        float transition,
        ActionPlayback action,
        float fade,
        Set<ActionPlayback> applied
    )
    {
        if (action != null
            && (!this.isTimelineDriven(action) || this.isTimelineActive(action))
            && applied.add(action))
        {
            action.apply(
                target,
                armature.getModel(),
                transition,
                fade * this.getTimelineWeight(action),
                true
            );
        }
    }
}
