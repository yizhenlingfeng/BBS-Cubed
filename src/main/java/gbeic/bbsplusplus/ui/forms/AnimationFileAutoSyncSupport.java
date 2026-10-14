package gbeic.bbsplusplus.ui.forms;

import gbeic.bbsplusplus.BBSPlusPlusMod;
import gbeic.bbsplusplus.api.IBbsppAnimation;
import gbeic.bbsplusplus.settings.CMLSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.AnimationPart;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.states.AnimationState;
import mchorse.bbs_mod.forms.states.AnimationStates;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.forms.editors.states.keyframes.UIAnimationStateEditor;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Pose;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * 动画状态自动读取模型动画文件。
 *
 * <p>BBS 原生 {@code GeoCubicModelLoader} 已经把模型目录下所有
 * {@code animations/*.animation.json}（Bedrock 格式）解析进了
 * {@link ModelInstance#animations}——动作页动画栏列出的就是这些动画。
 * 本支持类在打开动画状态编辑器时把它们同步为表单动画状态：</p>
 *
 * <ul>
 *   <li>按动画名（{@code customId}/{@code id}）匹配已有状态，缺失则新建；</li>
 *   <li>对空状态把动画的关键帧逐 tick 采样烘焙为姿势(Pose)轨道关键帧；</li>
 *   <li>写入动画时长（tick）与循环标记（loop 由 {@code AnimationMixin} 携带）。</li>
 * </ul>
 *
 * <p>保护：开关关闭不动作；pose 轨道已有任意关键帧时跳过，不覆盖手工编辑；
 * 模型尚未加载完成时挂起，由 {@link #tick()} 在渲染时重试。</p>
 */
public class AnimationFileAutoSyncSupport
{
    private final UIAnimationStateEditor stateEditor;
    private final UIFormEditor editor;
    private final Supplier<AnimationState> currentState;
    private boolean pending;

    public AnimationFileAutoSyncSupport(UIAnimationStateEditor stateEditor, UIFormEditor editor,
                                        Supplier<AnimationState> currentState)
    {
        this.stateEditor = stateEditor;
        this.editor = editor;
        this.currentState = currentState;
    }

    /** {@code UIAnimationStateEditor.setState} 入口调用。 */
    public void onSetState(AnimationState next)
    {
        this.pending = true;
        this.trySync(next);
    }

    /** {@code UIAnimationStateEditor.render} 头部调用：模型晚加载时补做一次。 */
    public void tick()
    {
        if (this.pending)
        {
            this.trySync(this.currentState.get());
        }
    }

    private void trySync(AnimationState selected)
    {
        if (!CMLSettings.isAutoLoadAnimationFileEnabled())
        {
            this.pending = false;
            return;
        }

        if (!(this.editor.form instanceof ModelForm modelForm))
        {
            this.pending = false;
            return;
        }

        ModelInstance model = ModelFormRenderer.getModel(modelForm);

        if (model == null || model.animations == null)
        {
            /* 渲染器还在加载模型，下一帧再试 */
            return;
        }

        if (model.animations.animations.isEmpty())
        {
            this.pending = false;
            return;
        }

        this.pending = false;

        if (selected != null)
        {
            this.bakeSelected(modelForm, model, selected);
        }

        this.syncAllStates(modelForm, model);
    }

    /** 把模型里的每个 Bedrock 动画同步为表单状态。 */
    private void syncAllStates(ModelForm modelForm, ModelInstance model)
    {
        AnimationStates states = modelForm.states;

        for (Map.Entry<String, Animation> entry : new TreeMap<>(model.animations.animations).entrySet())
        {
            String name = entry.getKey();
            Animation animation = entry.getValue();
            AnimationState state = states.getById(name);

            if (state == null)
            {
                state = states.addState();
                state.customId.set(name);
            }

            this.bakeIfEmpty(modelForm, model, state, animation);
        }
    }

    /** 选中状态按 customId/id 找同名动画后烘焙。 */
    private void bakeSelected(ModelForm modelForm, ModelInstance model, AnimationState state)
    {
        String name = state.customId.get();

        if (name.isEmpty())
        {
            name = state.id.get();
        }

        Animation animation = name.isEmpty() ? null : model.animations.get(name);

        if (animation != null)
        {
            this.bakeIfEmpty(modelForm, model, state, animation);
        }
    }

    @SuppressWarnings("unchecked")
    private void bakeIfEmpty(ModelForm modelForm, ModelInstance model, AnimationState state, Animation animation)
    {
        try
        {
            KeyframeChannel<Pose> pose =
                (KeyframeChannel<Pose>) state.properties.getOrCreate(modelForm, TrackId.property("", "pose"));

            if (pose == null || !pose.getKeyframes().isEmpty())
            {
                return;
            }

            List<Float> ticks = collectTicks(animation);

            if (ticks.isEmpty())
            {
                return;
            }

            IEntity entity = null;

            try
            {
                entity = this.editor.renderer.getTargetEntity();
            }
            catch (Exception ignored)
            {
                /* 常量动画不需要实体 */
            }

            final IEntity finalEntity = entity;

            BaseValue.edit(state.properties, (properties) ->
            {
                for (float tick : ticks)
                {
                    model.model.resetPose();
                    model.model.apply(finalEntity, animation, tick, 1F, 0F, false);

                    int index = pose.insert(tick, model.model.createPose());
                    Keyframe<Pose> keyframe = pose.get(index);

                    if (keyframe != null)
                    {
                        keyframe.getInterpolation().setInterp(interpAt(animation, tick));
                    }
                }

                model.model.resetPose();
            });

            float lastTick = ticks.get(ticks.size() - 1);
            state.duration.set(Math.max(animation.getLengthInTicks(), (int) Math.ceil(lastTick)));
            state.looping.set(((IBbsppAnimation) animation).bbspp$isLoop());
        }
        catch (Exception e)
        {
            BBSPlusPlusMod.LOGGER.warn("动画状态自动同步失败（{}）：{}", state.customId.get(), e.getMessage());
        }
    }

    /** 汇总动画所有骨骼通道的关键帧 tick，去重排序。 */
    private static List<Float> collectTicks(Animation animation)
    {
        TreeSet<Float> ticks = new TreeSet<>();

        for (AnimationPart part : animation.parts.values())
        {
            for (KeyframeChannel<?> channel : part.channels)
            {
                for (Keyframe<?> keyframe : channel.getKeyframes())
                {
                    ticks.add(keyframe.getTick());
                }
            }
        }

        return new ArrayList<>(ticks);
    }

    /** 取动画在该 tick 上第一个关键帧的插值类型，缺省线性。 */
    private static IInterp interpAt(Animation animation, float tick)
    {
        for (AnimationPart part : animation.parts.values())
        {
            for (KeyframeChannel<?> channel : part.channels)
            {
                for (Keyframe<?> keyframe : channel.getKeyframes())
                {
                    if (Math.abs(keyframe.getTick() - tick) < 0.001F)
                    {
                        IInterp interp = keyframe.getInterpolation().getInterp();

                        if (interp != null)
                        {
                            return interp;
                        }
                    }
                }
            }
        }

        return Interpolations.LINEAR;
    }
}
