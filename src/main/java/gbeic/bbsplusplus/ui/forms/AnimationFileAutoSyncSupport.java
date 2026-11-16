package gbeic.bbsplusplus.ui.forms;

import gbeic.bbsplusplus.BBSPlusPlusMod;
import gbeic.bbsplusplus.api.IBbsppAnimation;
import gbeic.bbsplusplus.settings.ModSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.AnimationPart;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.states.AnimationState;
import mchorse.bbs_mod.forms.states.AnimationStates;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Pose;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * 「动画状态自动读取动画文件」：把模型里的 Bedrock 动画同步成动画状态，并烘焙成姿势关键帧。
 *
 * <p>选中状态在 {@code setState} 里当场烘焙，编辑器随后的原生流程就能看到它的轨道；其余
 * 空状态排队，由 {@code render} 在每帧预算内推进，不在一次界面重建里把所有动画一次算完。</p>
 */
public class AnimationFileAutoSyncSupport
{
    /** 整条姿势通道。 */
    private static final TrackId POSE_TRACK = TrackId.property("", "pose");
    /** 每帧最多花在后台烘焙上的时间（纳秒）。 */
    private static final long FRAME_BUDGET_NANOS = 6L * 1_000_000L;
    /** 单个动画自动烘焙的姿势键上限（tick 数 × 骨骼数）。 */
    private static final long MAX_KEYS_PER_ANIMATION = 60_000L;
    /** 一次会话自动烘焙的姿势键总量上限。 */
    private static final long MAX_KEYS_PER_SESSION = 200_000L;

    private final UIFormEditor editor;
    private final Supplier<AnimationState> currentState;

    /** 渲染器尚未加载好模型，下一帧再同步。 */
    private boolean pending;
    /** 队列里的状态属于哪个模型表单，换表单即作废。 */
    private ModelForm queueForm;
    private final ArrayDeque<BakeJob> queue = new ArrayDeque<>();
    private BakeJob active;
    /** 超出上限而被跳过的状态，避免每次重建都重算一遍。 */
    private final Set<AnimationState> skippedStates = new HashSet<>();
    private long bakedKeys;

    public AnimationFileAutoSyncSupport(UIFormEditor editor, Supplier<AnimationState> currentState)
    {
        this.editor = editor;
        this.currentState = currentState;
    }

    /** {@code UIAnimationStateEditor.setState} 入口调用。 */
    public void onSetState(AnimationState next)
    {
        this.pending = true;
        this.sync(next);
    }

    /** {@code UIAnimationStateEditor.render} 头部调用：补做同步、推进后台烘焙。 */
    public void tick()
    {
        if (this.pending)
        {
            this.sync(this.currentState.get());
        }

        this.pump();
    }

    private void sync(AnimationState selected)
    {
        if (!ModSettings.isAutoLoadAnimationFileEnabled())
        {
            this.pending = false;
            this.clearJobs();

            return;
        }

        if (!(this.editor.form instanceof ModelForm modelForm))
        {
            this.pending = false;
            this.clearJobs();

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

        if (this.queueForm != modelForm)
        {
            this.clearJobs();
            this.queueForm = modelForm;
        }

        if (selected != null)
        {
            this.bakeNow(modelForm, model, selected);
        }

        this.collectJobs(modelForm, model);
    }

    /** 把模型里的每个 Bedrock 动画同步为表单状态，空状态的烘焙排进队列。 */
    private void collectJobs(ModelForm modelForm, ModelInstance model)
    {
        AnimationStates states = modelForm.states;

        for (Map.Entry<String, Animation> entry : new TreeMap<>(model.animations.animations).entrySet())
        {
            AnimationState state = states.getById(entry.getKey());

            if (state == null)
            {
                state = states.addState();
                state.customId.set(entry.getKey());
            }

            if (this.skippedStates.contains(state) || hasPoseKeyframes(state) || this.isBusy(state))
            {
                continue;
            }

            this.queue.add(new BakeJob(modelForm, state, entry.getValue()));
        }
    }

    /** 选中状态同步烘焙：编辑器在这一次 {@code setState} 里就要看到轨道。 */
    private void bakeNow(ModelForm modelForm, ModelInstance model, AnimationState state)
    {
        Animation animation = animationOf(model, state);

        if (animation == null)
        {
            return;
        }

        BakeJob job = this.active != null && this.active.state == state
            ? this.active
            : new BakeJob(modelForm, state, animation);

        try
        {
            if (!job.prepare(model))
            {
                return;
            }

            while (!job.finished)
            {
                job.step(Long.MAX_VALUE);
            }

            if (this.active == job)
            {
                this.active = null;
            }
        }
        catch (Exception e)
        {
            BBSPlusPlusMod.LOGGER.warn("动画状态自动同步失败（{}）：{}", state.customId.get(), e.getMessage());
        }
    }

    /** 在每帧渲染预算内推进后台烘焙。 */
    private void pump()
    {
        if (this.active == null && this.queue.isEmpty())
        {
            return;
        }

        if (!(this.editor.form instanceof ModelForm modelForm) || modelForm != this.queueForm)
        {
            this.clearJobs();

            return;
        }

        ModelInstance model = ModelFormRenderer.getModel(modelForm);

        if (model == null || model.animations == null)
        {
            /* 模型正在重载，队列留着，下一帧继续 */
            return;
        }

        long deadline = System.nanoTime() + FRAME_BUDGET_NANOS;

        try
        {
            while (true)
            {
                if (this.active == null)
                {
                    this.active = this.nextJob(model);

                    if (this.active == null)
                    {
                        return;
                    }
                }

                this.active.step(deadline);

                if (!this.active.finished)
                {
                    return;
                }

                this.active = null;

                if (System.nanoTime() >= deadline)
                {
                    return;
                }
            }
        }
        catch (Exception e)
        {
            AnimationState state = this.active == null ? null : this.active.state;

            BBSPlusPlusMod.LOGGER.warn("动画状态后台烘焙中断（{}）：{}",
                state == null ? "" : state.customId.get(), e.getMessage());

            this.active = null;
        }
    }

    private BakeJob nextJob(ModelInstance model)
    {
        while (!this.queue.isEmpty())
        {
            BakeJob job = this.queue.poll();

            if (job.form != this.queueForm || hasPoseKeyframes(job.state))
            {
                continue;
            }

            if (job.prepare(model))
            {
                return job;
            }
        }

        return null;
    }

    private void clearJobs()
    {
        this.queue.clear();
        this.active = null;
        this.skippedStates.clear();
        this.bakedKeys = 0L;
    }

    private boolean isBusy(AnimationState state)
    {
        if (this.active != null && this.active.state == state)
        {
            return true;
        }

        for (BakeJob job : this.queue)
        {
            if (job.state == state)
            {
                return true;
            }
        }

        return false;
    }

    private static boolean hasPoseKeyframes(AnimationState state)
    {
        KeyframeChannel<?> pose = state.properties.get(POSE_TRACK);

        return pose != null && !pose.getKeyframes().isEmpty();
    }

    /** 状态对应的动画：优先 customId，其次 id。 */
    private static Animation animationOf(ModelInstance model, AnimationState state)
    {
        String name = state.customId.get();

        if (name == null || name.isEmpty())
        {
            name = state.id.get();
        }

        return name == null || name.isEmpty() ? null : model.animations.get(name);
    }

    /**
     * 一个动画的烘焙活：自带游标，可以分片、跨帧继续。
     *
     * <p>每片包在 {@code FLAG_BATCH} 里，让 undo 把整段烘焙合并成一条历史，而不是每片一条
     * （每条 undo 都持有整条姿势轨道的序列化副本，重模型上比轨道本身还大）。</p>
     */
    private final class BakeJob
    {
        private final ModelForm form;
        private final AnimationState state;
        private final Animation animation;

        private ModelInstance model;
        private KeyframeChannel<Pose> pose;
        private float[] ticks;
        private IInterp[] interps;
        private IEntity entity;
        private int cursor;
        private int added;
        private boolean prepared;
        private boolean finished;

        private BakeJob(ModelForm form, AnimationState state, Animation animation)
        {
            this.form = form;
            this.state = state;
            this.animation = animation;
        }

        /**
         * 收集 tick、估算代价、准备通道。返回 {@code false} 表示不用烘焙
         * （已有键、没关键帧，或超出上限）。
         */
        @SuppressWarnings("unchecked")
        private boolean prepare(ModelInstance model)
        {
            if (this.prepared)
            {
                return true;
            }

            KeyframeChannel<?> existing = this.state.properties.get(POSE_TRACK);

            if (existing != null && !existing.getKeyframes().isEmpty())
            {
                return false;
            }

            if (this.ticks == null)
            {
                InterpIndex index = InterpIndex.of(this.animation);

                this.ticks = index.ticks;
                this.interps = new IInterp[this.ticks.length];

                for (int i = 0; i < this.ticks.length; i++)
                {
                    this.interps[i] = index.at(i);
                }
            }

            if (this.ticks.length == 0)
            {
                return false;
            }

            long cost = (long) this.ticks.length * Math.max(1, model.model.getAllGroupKeys().size());

            if (cost > MAX_KEYS_PER_ANIMATION || AnimationFileAutoSyncSupport.this.bakedKeys + cost > MAX_KEYS_PER_SESSION)
            {
                if (AnimationFileAutoSyncSupport.this.skippedStates.add(this.state))
                {
                    BBSPlusPlusMod.LOGGER.warn(
                        "动画「{}」自动烘焙已跳过：预计 {} 个姿势关键帧（单个动画上限 {}，本次已烘焙 {} / 上限 {}）",
                        this.state.customId.get(), cost, MAX_KEYS_PER_ANIMATION,
                        AnimationFileAutoSyncSupport.this.bakedKeys, MAX_KEYS_PER_SESSION
                    );
                }

                return false;
            }

            this.model = model;
            this.entity = this.resolveEntity();
            this.pose = (KeyframeChannel<Pose>) this.state.properties.getOrCreate(this.form, POSE_TRACK);

            if (this.pose == null)
            {
                return false;
            }

            this.state.duration.set(Math.max(
                this.animation.getLengthInTicks(),
                (int) Math.ceil(this.ticks[this.ticks.length - 1])
            ));
            this.state.looping.set(((IBbsppAnimation) this.animation).bbspp$isLoop());

            this.prepared = true;

            return true;
        }

        private IEntity resolveEntity()
        {
            try
            {
                return AnimationFileAutoSyncSupport.this.editor.renderer.getTargetEntity();
            }
            catch (Exception ignored)
            {
                /* 常量动画不需要实体 */
                return null;
            }
        }

        /** 在预算内算多少算多少；预算用完就留着游标，下一帧接着来。 */
        private void step(long deadline)
        {
            final int total = this.ticks.length;

            BaseValue.edit(this.state.properties, IValueListener.FLAG_BATCH, (properties) ->
            {
                while (this.cursor < total && System.nanoTime() < deadline)
                {
                    float tick = this.ticks[this.cursor];
                    IInterp interp = this.interps[this.cursor];

                    this.model.model.resetPose();
                    this.model.model.apply(this.entity, this.animation, tick, 1F, 0F, false);

                    Keyframe<Pose> keyframe = new Keyframe<>("", this.pose.getFactory(), tick, this.model.model.createPose());

                    keyframe.getInterpolation().setInterp(interp);
                    this.pose.add(keyframe);

                    this.cursor++;
                    this.added++;
                    AnimationFileAutoSyncSupport.this.bakedKeys++;
                }

                if (this.added > 0)
                {
                    /* tick 升序追加，列表本来就排好，一次 sort 就够：逐键 insert 每次都要全表
                     * 定位 + 整表 sort + sync，几万键就是平方级开销，主线程会卡死在这。 */
                    this.pose.sort();
                    this.added = 0;
                }
            });

            /* 别把烘焙用的姿势留给渲染 */
            this.model.model.resetPose();

            if (this.cursor >= total)
            {
                this.finished = true;
            }
        }
    }

    /**
     * 每个 tick 该用哪段插值，一次性算好。
     *
     * <p>方块模型动画的插值挂在段内后一帧，姿势通道取段内首帧，两者错开一段，所以要用该
     * tick <b>之后</b>那个关键帧的插值：步阶帧的 CONST 才会落到跳变前那一帧上。原先每个
     * tick 都扫一遍全部骨骼通道（平方级），这里按 tick 排序后记下"之后第一个带插值的关键帧"。</p>
     */
    private static final class InterpIndex
    {
        private final float[] ticks;
        private final IInterp[] interps;
        /** next[i]：i 之后第一个带插值的关键帧下标，没有则 -1。 */
        private final int[] next;

        private InterpIndex(float[] ticks, IInterp[] interps, int[] next)
        {
            this.ticks = ticks;
            this.interps = interps;
            this.next = next;
        }

        private static InterpIndex of(Animation animation)
        {
            TreeMap<Float, IInterp> byTick = new TreeMap<>();

            for (AnimationPart part : animation.parts.values())
            {
                for (KeyframeChannel<?> channel : part.channels)
                {
                    for (Keyframe<?> keyframe : channel.getKeyframes())
                    {
                        Float tick = keyframe.getTick();
                        IInterp interp = keyframe.getInterpolation().getInterp();

                        if (!byTick.containsKey(tick) || (byTick.get(tick) == null && interp != null))
                        {
                            byTick.put(tick, interp);
                        }
                    }
                }
            }

            int size = byTick.size();
            float[] ticks = new float[size];
            IInterp[] interps = new IInterp[size];
            int i = 0;

            for (Map.Entry<Float, IInterp> entry : byTick.entrySet())
            {
                ticks[i] = entry.getKey();
                interps[i] = entry.getValue();
                i++;
            }

            int[] next = new int[size];
            int cursor = -1;

            for (int j = size - 1; j >= 0; j--)
            {
                next[j] = cursor;

                if (interps[j] != null)
                {
                    cursor = j;
                }
            }

            return new InterpIndex(ticks, interps, next);
        }

        /** 下标 index 处那个 tick 该用的插值，缺省线性。 */
        private IInterp at(int index)
        {
            int next = this.next[index];

            if (next >= 0)
            {
                return this.interps[next];
            }

            return this.interps[index] == null ? Interpolations.LINEAR : this.interps[index];
        }
    }
}
