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

        if (this.isSlot(this.lastActive)
            && !this.isTimelineDriven(this.lastActive)
            && this.lastActive != null
            && this.active != null
            && this.active.isFading())
        {
            this.apply(target, armature, transition, this.lastActive, 1F, applied);
        }

        if (this.isSlot(this.active) && !this.isTimelineDriven(this.active) && this.active != null)
        {
            float fade = this.active.isFading() ? this.active.getFadeFactor(transition) : 1F;

            this.apply(target, armature, transition, this.active, fade, applied);
        }

        /* Timeline-driven overlay actions are clips, not entity state slots. */
        for (ActionPlayback action : new ActionPlayback[] {
            this.idle, this.running, this.sprinting, this.crouching, this.crouchingIdle,
            this.dying, this.falling, this.swimming, this.swimmingIdle, this.riding,
            this.ridingIdle, this.flying, this.flyingIdle,
            this.jump1, this.jump2, this.swipe, this.hurt,
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

    /**
     * active/lastActive 是否仍是本动画器某个槽位/列表里"在册"的 playback。
     * setup 每次都会重建各槽位字段（动画名变化时 createAction 换新实例），
     * 旧引用若还挂在 active 上，应用它就是在叠加一个已切走的动作——姿势抖动
     * 会顺着布娃娃的驱动目标传进物理。替换式 setup 曾靠"整体重建动画器"兜住
     * 这一点，改为复用式 setup 后由这里把关。
     */
    private boolean isSlot(ActionPlayback action)
    {
        if (action == null)
        {
            return false;
        }

        return action == this.idle || action == this.running || action == this.sprinting
            || action == this.crouching || action == this.crouchingIdle || action == this.dying
            || action == this.falling || action == this.swimming || action == this.swimmingIdle
            || action == this.riding || action == this.ridingIdle || action == this.flying
            || action == this.flyingIdle || action == this.jump1 || action == this.jump2
            || action == this.swipe || action == this.hurt || action == this.land
            || action == this.shoot || action == this.consume
            || action == this.basePre || action == this.basePost
            || this.bbspp_cml$transitionActions.contains(action)
            || this.actions.contains(action);
    }

    private boolean isTimelineActive(ActionPlayback action)
    {
        /* 只按权重判定：窗口内的活动 clip 权重 > 0，窗口外的动作求值器已输出空名
         * （playback 为 null）或权重归零，不再无条件应用。 */
        return this.isTimelineDriven(action) && this.getTimelineWeight(action) > 0F;
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
