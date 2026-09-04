package gbeic.bbsplusplus.timeline.ui;

import gbeic.bbsplusplus.api.ActionTimelineConfig;
import gbeic.bbsplusplus.settings.CMLSettings;
import gbeic.bbsplusplus.timeline.ActionTimelineEvaluator;
import gbeic.bbsplusplus.ui.forms.SnowUIKeys;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.ActionConfig;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.pose.UIActionsConfigEditor;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * snow_actions 时间线在 {@code UIActionsConfigEditor} 上的整套业务支持类：
 * 循环控件构建、时间线上下文、成对端点同步与端点创建。宿主编辑器的私有
 * 成员（configs/config/pre/postCallback）经 Supplier 注入，mixin 只留薄壳。
 */
public class ActionsTimelineEditorSupport
{
    private static final float EPSILON = 0.0001F;

    private final UIActionsConfigEditor editor;
    private final Supplier<ActionsConfig> configs;
    private final Supplier<ActionConfig> config;
    private final Supplier<Runnable> preCallback;
    private final Supplier<Runnable> postCallback;

    private Keyframe<ActionsConfig> keyframe;
    private UIKeyframes timelineEditor;
    private ModelForm form;
    private boolean overlay;
    private int programmaticSelection;
    private UIToggle loopBeyond;
    private UITrackpad loopInterval;
    private UIElement loopIntervalRow;

    public ActionsTimelineEditorSupport(
        UIActionsConfigEditor editor,
        Supplier<ActionsConfig> configs,
        Supplier<ActionConfig> config,
        Supplier<Runnable> preCallback,
        Supplier<Runnable> postCallback
    )
    {
        this.editor = editor;
        this.configs = configs;
        this.config = config;
        this.preCallback = preCallback;
        this.postCallback = postCallback;
    }

    /** 构造器尾注入：创建循环控件并挂到编辑器上。 */
    public void initControls()
    {
        this.loopBeyond = new UIToggle(SnowUIKeys.ACTIONS_LOOP_BEYOND, (toggle) ->
        {
            if (!this.hasTimelineConfig())
            {
                return;
            }

            this.run(this.preCallback.get());
            ((ActionTimelineConfig) this.config.get()).bbspp_cml$setLoopBeyond(toggle.getValue());
            this.syncPairedOptions();
            this.run(this.postCallback.get());
        });
        this.loopInterval = new UITrackpad((value) ->
        {
            if (!this.hasTimelineConfig())
            {
                return;
            }

            this.run(this.preCallback.get());
            ((ActionTimelineConfig) this.config.get()).bbspp_cml$setLoopInterval(value.floatValue());
            this.syncPairedOptions();
            this.run(this.postCallback.get());
        }).limit(0D).integer();
        this.loopIntervalRow = UI.labelRow(SnowUIKeys.ACTIONS_LOOP_INTERVAL, this.loopInterval);

        this.editor.add(this.loopBeyond, this.loopIntervalRow);
        this.refreshLoopControls();
    }

    public UIElement getLoopBeyondControl()
    {
        return this.loopBeyond;
    }

    public UIElement getLoopIntervalRow()
    {
        return this.loopIntervalRow;
    }

    public void setTimelineContext(Keyframe<ActionsConfig> keyframe, UIKeyframes editor, ModelForm form, boolean overlay)
    {
        if (!CMLSettings.isSnowActionsEnabled())
        {
            this.keyframe = null;
            this.timelineEditor = null;
            this.form = null;
            this.overlay = false;

            return;
        }

        this.keyframe = keyframe;
        this.timelineEditor = editor;
        this.form = form;
        this.overlay = overlay;

        if (overlay)
        {
            KeyframeChannel<ActionsConfig> channel = this.getChannel();

            if (channel != null)
            {
                for (Keyframe<ActionsConfig> marker : channel.getKeyframes())
                {
                    for (ActionConfig action : marker.getValue().actions.values())
                    {
                        ActionTimelineConfig timeline = (ActionTimelineConfig) action;

                        if (timeline.bbspp_cml$isTimelineDriven())
                        {
                            timeline.bbspp_cml$setOverlayTimeline(true);
                        }
                    }
                }
            }
        }

        this.refreshLoopControls();
    }

    public void beginProgrammaticSelection()
    {
        this.programmaticSelection += 1;
    }

    public void endProgrammaticSelection()
    {
        this.programmaticSelection -= 1;
        this.refreshLoopControls();
    }

    /** 动作/动画列表选中变化时创建（或清理）成对的时间线端点关键帧。 */
    public void createAnimationEndpoint()
    {
        if (!CMLSettings.isSnowActionsEnabled()
            || this.programmaticSelection > 0
            || this.keyframe == null
            || this.timelineEditor == null
            || this.form == null)
        {
            return;
        }

        String actionKey = this.getCurrentActionKey();

        if (actionKey == null)
        {
            return;
        }

        KeyframeChannel<ActionsConfig> channel = this.getChannel();

        if (channel == null)
        {
            return;
        }

        ActionConfig config = this.config.get();
        ActionTimelineConfig timeline = (ActionTimelineConfig) config;
        String previousClip = timeline.bbspp_cml$getClipId();

        if (previousClip != null
            && !previousClip.isEmpty()
            && this.isPairedMarker(channel, actionKey, previousClip))
        {
            this.removePreviousSegment(channel, actionKey, previousClip);
        }

        if (config.name == null || config.name.isEmpty())
        {
            timeline.bbspp_cml$setClipId("");
            timeline.bbspp_cml$setTimelineFrame(0F);
            timeline.bbspp_cml$setOverlayTimeline(false);
            channel.sort();
            this.refreshLoopControls();

            return;
        }

        int duration = this.getAnimationDuration(config.name);
        String clipId = UUID.randomUUID().toString();

        timeline.bbspp_cml$setClipId(clipId);
        timeline.bbspp_cml$setTimelineFrame(0F);
        timeline.bbspp_cml$setOverlayTimeline(this.overlay);

        float endpointTick = this.keyframe.getTick() + duration;
        Keyframe<ActionsConfig> endpoint = this.findKeyframe(channel, endpointTick);
        ActionConfig endpointAction = config.copy();
        ActionTimelineConfig endpointTimeline = (ActionTimelineConfig) endpointAction;

        endpointTimeline.bbspp_cml$setClipId(clipId);
        endpointTimeline.bbspp_cml$setTimelineFrame(duration);
        endpointTimeline.bbspp_cml$setOverlayTimeline(this.overlay);

        if (endpoint == null)
        {
            ActionsConfig endpointValue = new ActionsConfig();

            endpointValue.copy(this.configs.get());
            endpointValue.actions.put(actionKey, endpointAction);
            channel.insert(endpointTick, endpointValue);
        }
        else
        {
            endpoint.getValue().actions.put(actionKey, endpointAction);
        }

        channel.sort();
        this.refreshLoopControls();
    }

    @SuppressWarnings("unchecked")
    private KeyframeChannel<ActionsConfig> getChannel()
    {
        return this.keyframe.getParent() instanceof KeyframeChannel<?>
            ? (KeyframeChannel<ActionsConfig>) this.keyframe.getParent()
            : null;
    }

    private String getCurrentActionKey()
    {
        ActionConfig config = this.config.get();

        for (Map.Entry<String, ActionConfig> entry : this.configs.get().actions.entrySet())
        {
            if (entry.getValue() == config)
            {
                return entry.getKey();
            }
        }

        return null;
    }

    /** 循环/速度/渐变/起始 tick 变化时同步到同一 clip 的成对端点。 */
    public void syncPairedOptions()
    {
        ActionConfig config = this.config.get();

        if (!CMLSettings.isSnowActionsEnabled()
            || this.keyframe == null
            || config == null)
        {
            return;
        }

        String actionKey = this.getCurrentActionKey();
        ActionTimelineConfig timeline = (ActionTimelineConfig) config;
        KeyframeChannel<ActionsConfig> channel = this.getChannel();

        if (actionKey == null || channel == null || !timeline.bbspp_cml$isTimelineDriven())
        {
            return;
        }

        for (Keyframe<ActionsConfig> member : this.getClipMembers(channel, actionKey, timeline.bbspp_cml$getClipId()))
        {
            ActionConfig paired = member.getValue().actions.get(actionKey);

            if (paired == null || paired == config)
            {
                continue;
            }

            paired.loop = config.loop;
            paired.speed = config.speed;
            paired.fade = config.fade;
            paired.tick = config.tick;

            ActionTimelineConfig pairedTimeline = (ActionTimelineConfig) paired;

            pairedTimeline.bbspp_cml$setLoopBeyond(timeline.bbspp_cml$isLoopBeyond());
            pairedTimeline.bbspp_cml$setLoopInterval(timeline.bbspp_cml$getLoopInterval());
        }
    }

    private boolean hasTimelineConfig()
    {
        ActionConfig config = this.config.get();

        return CMLSettings.isSnowActionsEnabled()
            && config != null
            && ((ActionTimelineConfig) config).bbspp_cml$isTimelineDriven();
    }

    public void refreshLoopControls()
    {
        if (this.loopBeyond == null || this.loopIntervalRow == null)
        {
            return;
        }

        boolean visible = this.keyframe != null;
        boolean enabled = visible && this.hasTimelineConfig();

        this.loopBeyond.setVisible(visible);
        this.loopIntervalRow.setVisible(visible);
        this.loopBeyond.setEnabled(enabled);
        this.loopIntervalRow.setEnabled(enabled);

        if (enabled)
        {
            ActionTimelineConfig timeline = (ActionTimelineConfig) this.config.get();

            this.loopBeyond.setValue(timeline.bbspp_cml$isLoopBeyond());
            this.loopInterval.setValue(timeline.bbspp_cml$getLoopInterval());
        }
        else
        {
            this.loopBeyond.setValue(true);
            this.loopInterval.setValue(0D);
        }

        /*
         * 这里绝不能对 actionsEditor 单独调用 resize()：它挂在工厂面板滚动视图的
         * ColumnResizer 列布局里，ChildResizer.apply 会复用父布局上一轮残留的
         * y 游标(= 内容总高)，导致每点击一次动作/动画列表整个面板下沉一大截，
         * 直到拖动窗口触发全量 resize 才复位。可见性/数值切换不影响列布局占位，
         * 因此无需任何重新布局。
         */
    }

    private void run(Runnable callback)
    {
        if (callback != null)
        {
            callback.run();
        }
    }

    private int getAnimationDuration(String animationName)
    {
        ModelInstance model = ModelFormRenderer.getModel(this.form);
        Animation animation = model == null ? null : model.getAnimations().get(animationName);

        return ActionTimelineEvaluator.getEffectiveAnimationLength(animation);
    }

    private Keyframe<ActionsConfig> findKeyframe(KeyframeChannel<ActionsConfig> channel, float tick)
    {
        for (Keyframe<ActionsConfig> candidate : channel.getKeyframes())
        {
            if (Math.abs(candidate.getTick() - tick) < EPSILON)
            {
                return candidate;
            }
        }

        return null;
    }

    private boolean isPairedMarker(KeyframeChannel<ActionsConfig> channel, String actionKey, String clipId)
    {
        List<Keyframe<ActionsConfig>> members = this.getClipMembers(channel, actionKey, clipId);

        if (!members.contains(this.keyframe) || members.size() < 2)
        {
            return false;
        }

        if (members.size() == 2)
        {
            return true;
        }

        Keyframe<ActionsConfig> first = null;
        Keyframe<ActionsConfig> second = null;
        float bestFrameSpan = 0F;
        float bestTickSpan = Float.MAX_VALUE;

        /*
         * A newly inserted Actions keyframe inherits the interpolated clip ID.
         * Keep the original start/end pair: it has the widest animation-frame
         * span, and duplicate inherited endpoints are resolved by proximity.
         */
        for (int i = 0; i < members.size() - 1; i++)
        {
            Keyframe<ActionsConfig> a = members.get(i);
            float aFrame = this.getTimelineFrame(a, actionKey);

            for (int j = i + 1; j < members.size(); j++)
            {
                Keyframe<ActionsConfig> b = members.get(j);
                float frameSpan = Math.abs(aFrame - this.getTimelineFrame(b, actionKey));
                float tickSpan = Math.abs(a.getTick() - b.getTick());

                if (frameSpan > bestFrameSpan + EPSILON
                    || (Math.abs(frameSpan - bestFrameSpan) < EPSILON && tickSpan < bestTickSpan))
                {
                    first = a;
                    second = b;
                    bestFrameSpan = frameSpan;
                    bestTickSpan = tickSpan;
                }
            }
        }

        return bestFrameSpan > EPSILON
            && (this.keyframe == first || this.keyframe == second);
    }

    private List<Keyframe<ActionsConfig>> getClipMembers(KeyframeChannel<ActionsConfig> channel, String actionKey, String clipId)
    {
        List<Keyframe<ActionsConfig>> members = new ArrayList<>();

        for (Keyframe<ActionsConfig> candidate : channel.getKeyframes())
        {
            ActionConfig action = candidate.getValue().actions.get(actionKey);

            if (action != null && clipId.equals(((ActionTimelineConfig) action).bbspp_cml$getClipId()))
            {
                members.add(candidate);
            }
        }

        return members;
    }

    private float getTimelineFrame(Keyframe<ActionsConfig> keyframe, String actionKey)
    {
        ActionConfig action = keyframe.getValue().actions.get(actionKey);

        return action == null ? 0F : ((ActionTimelineConfig) action).bbspp_cml$getTimelineFrame();
    }

    private void removePreviousSegment(KeyframeChannel<ActionsConfig> channel, String actionKey, String clipId)
    {
        List<Keyframe<ActionsConfig>> emptyMarkers = new ArrayList<>();

        for (Keyframe<ActionsConfig> candidate : this.getClipMembers(channel, actionKey, clipId))
        {
            if (candidate == this.keyframe)
            {
                continue;
            }

            ActionConfig action = candidate.getValue().actions.get(actionKey);

            if (action != null && clipId.equals(((ActionTimelineConfig) action).bbspp_cml$getClipId()))
            {
                if (this.overlay)
                {
                    candidate.getValue().actions.put(actionKey, new ActionConfig(""));
                }
                else
                {
                    candidate.getValue().actions.remove(actionKey);
                }

                if (this.isDisposableMarker(candidate.getValue()))
                {
                    emptyMarkers.add(candidate);
                }
            }
        }

        for (Keyframe<ActionsConfig> marker : emptyMarkers)
        {
            int index = channel.getKeyframes().indexOf(marker);

            if (index >= 0)
            {
                channel.remove(index);
            }
        }
    }

    private boolean isDisposableMarker(ActionsConfig configs)
    {
        for (Map.Entry<String, ActionConfig> entry : configs.actions.entrySet())
        {
            ActionConfig action = entry.getValue();

            if (((ActionTimelineConfig) action).bbspp_cml$isTimelineDriven())
            {
                return false;
            }

            boolean neutral = this.overlay
                ? (action.name == null || action.name.isEmpty()) && action.isDefault()
                : action.isDefault() && entry.getKey().equals(action.name);

            if (!neutral)
            {
                return false;
            }
        }

        return true;
    }
}
