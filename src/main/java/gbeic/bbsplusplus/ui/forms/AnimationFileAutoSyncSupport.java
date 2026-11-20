package gbeic.bbsplusplus.ui.forms;

import gbeic.bbsplusplus.api.IBbsppAnimation;
import gbeic.bbsplusplus.settings.ModSettings;
import gbeic.bbsplusplus.util.AnimationBakeSnapshot;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.AnimationPart;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.states.AnimationState;
import mchorse.bbs_mod.forms.states.AnimationStates;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Pose;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * 「动画状态自动读取动画文件」：把模型里的 Bedrock 动画同步成动画状态，并烘焙成姿势关键帧。
 *
 * <p>所有状态都在独立后台线程中采样，渲染线程只负责创建任务和提交完成的关键帧。
 * 选中的状态优先排队，完成后刷新当前时间线；进度由 {@link AnimationBakeProgress} 提供给状态列表。</p>
 */
public class AnimationFileAutoSyncSupport
{
    private static final TrackId POSE_TRACK = TrackId.property("", "pose");
    private static final ExecutorService BAKE_EXECUTOR = Executors.newSingleThreadExecutor(runnable ->
    {
        Thread thread = new Thread(runnable, "bbspp-animation-bake");

        thread.setDaemon(true);

        return thread;
    });

    private final UIFormEditor editor;
    private final Supplier<AnimationState> currentState;
    private final ConcurrentLinkedQueue<BakeJob> finishedJobs = new ConcurrentLinkedQueue<>();
    private final Map<AnimationState, BakeJob> jobs = new IdentityHashMap<>();
    private final Set<AnimationState> skippedStates = Collections.newSetFromMap(new IdentityHashMap<>());

    private boolean pending;
    private ModelForm queueForm;
    private ModelInstance queueModel;
    private AnimationBakeSnapshot modelSnapshot;

    public AnimationFileAutoSyncSupport(UIFormEditor editor, Supplier<AnimationState> currentState)
    {
        this.editor = editor;
        this.currentState = currentState;
    }

    public void onSetState(AnimationState next)
    {
        this.pending = true;
        this.sync(next);
    }

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
            return;
        }

        this.pending = false;

        if (model.animations.animations.isEmpty())
        {
            return;
        }

        if (this.queueForm != modelForm || this.queueModel != model)
        {
            this.clearJobs();
            this.queueForm = modelForm;
            this.queueModel = model;
            this.modelSnapshot = AnimationBakeSnapshot.capture(model.model);
        }

        this.collectJobs(modelForm, model, selected);
    }

    private void collectJobs(ModelForm modelForm, ModelInstance model, AnimationState selected)
    {
        if (selected != null)
        {
            Animation animation = animationOf(model, selected);

            if (animation != null)
            {
                this.enqueue(modelForm, selected, animation);
            }
        }

        AnimationStates states = modelForm.states;

        for (Map.Entry<String, Animation> entry : new TreeMap<>(model.animations.animations).entrySet())
        {
            AnimationState state = states.getById(entry.getKey());

            if (state == null)
            {
                state = states.addState();
                state.customId.set(entry.getKey());
            }

            this.enqueue(modelForm, state, entry.getValue());
        }
    }

    private void enqueue(ModelForm modelForm, AnimationState state, Animation animation)
    {
        if (this.skippedStates.contains(state) || hasPoseKeyframes(state) || this.jobs.containsKey(state))
        {
            return;
        }

        BakeJob job = new BakeJob(modelForm, state, animation);

        if (!job.prepare())
        {
            this.skippedStates.add(state);

            return;
        }

        this.jobs.put(state, job);
        AnimationBakeProgress.queued(state);
        BAKE_EXECUTOR.execute(job);
    }

    private void pump()
    {
        BakeJob job;

        while ((job = this.finishedJobs.poll()) != null)
        {
            this.jobs.remove(job.state);
            AnimationBakeProgress.clear(job.state);

            if (job.result == null)
            {
                this.skippedStates.add(job.state);

                continue;
            }

            if (job.cancelled || this.editor.form != job.form || this.queueForm != job.form || hasPoseKeyframes(job.state))
            {
                continue;
            }

            this.commit(job);
        }
    }

    @SuppressWarnings("unchecked")
    private void commit(BakeJob job)
    {
        BaseValue.edit(job.state.properties, IValueListener.FLAG_BATCH, properties ->
        {
            KeyframeChannel<Pose> pose = (KeyframeChannel<Pose>) job.state.properties.getOrCreate(job.form, POSE_TRACK);

            if (pose == null)
            {
                return;
            }

            for (Keyframe<Pose> keyframe : job.result)
            {
                pose.add(keyframe);
            }

            pose.sort();
            job.state.duration.set(job.duration);
            job.state.looping.set(job.looping);
        });

        if (this.currentState.get() == job.state)
        {
            this.editor.statesKeyframes.setState(job.state);
        }
    }

    private void clearJobs()
    {
        for (BakeJob job : this.jobs.values())
        {
            job.cancelled = true;
            AnimationBakeProgress.clear(job.state);
        }

        for (BakeJob job : this.finishedJobs)
        {
            AnimationBakeProgress.clear(job.state);
        }

        this.jobs.clear();
        this.finishedJobs.clear();
        this.skippedStates.clear();
        this.queueForm = null;
        this.queueModel = null;
        this.modelSnapshot = null;
    }

    private static boolean hasPoseKeyframes(AnimationState state)
    {
        KeyframeChannel<?> pose = state.properties.get(POSE_TRACK);

        return pose != null && !pose.getKeyframes().isEmpty();
    }

    private static Animation animationOf(ModelInstance model, AnimationState state)
    {
        String name = state.customId.get();

        if (name == null || name.isEmpty())
        {
            name = state.id.get();
        }

        return name == null || name.isEmpty() ? null : model.animations.get(name);
    }

    private final class BakeJob implements Runnable
    {
        private final ModelForm form;
        private final AnimationState state;
        private final Animation animation;

        private float[] ticks;
        private IInterp[] interps;
        private AnimationBakeSnapshot snapshot;
        private IEntity entity;
        private int duration;
        private boolean looping;
        private volatile boolean cancelled;
        private List<Keyframe<Pose>> result;

        private BakeJob(ModelForm form, AnimationState state, Animation animation)
        {
            this.form = form;
            this.state = state;
            this.animation = animation;
        }

        private boolean prepare()
        {
            InterpIndex index = InterpIndex.of(this.animation);

            this.ticks = index.ticks;
            this.interps = new IInterp[this.ticks.length];

            for (int i = 0; i < this.ticks.length; i++)
            {
                this.interps[i] = index.at(i);
            }

            if (this.ticks.length == 0 || AnimationFileAutoSyncSupport.this.modelSnapshot == null)
            {
                return false;
            }

            this.snapshot = AnimationFileAutoSyncSupport.this.modelSnapshot;
            this.entity = this.resolveEntity();
            this.duration = Math.max(
                this.animation.getLengthInTicks(),
                (int) Math.ceil(this.ticks[this.ticks.length - 1])
            );
            this.looping = ((IBbsppAnimation) this.animation).bbspp$isLoop();

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
                return null;
            }
        }

        @Override
        public void run()
        {
            try
            {
                MolangParser parser = new MolangParser();
                IModel model = this.snapshot.createModel(parser);
                Animation animation = AnimationBakeSnapshot.copyAnimation(this.animation, parser);
                List<Keyframe<Pose>> frames = new ArrayList<>(this.ticks.length);

                for (int i = 0; i < this.ticks.length; i++)
                {
                    if (this.cancelled)
                    {
                        return;
                    }

                    model.resetPose();
                    model.apply(this.entity, animation, this.ticks[i], 1F, 0F, false);

                    Keyframe<Pose> keyframe = new Keyframe<>("", KeyframeFactories.POSE, this.ticks[i], model.createPose());

                    keyframe.getInterpolation().setInterp(this.interps[i]);
                    frames.add(keyframe);
                    AnimationBakeProgress.update(this.state, (i + 1F) / this.ticks.length);
                }

                this.result = frames;
            }
            catch (Exception ignored)
            {}
            finally
            {
                if (this.result == null)
                {
                    AnimationBakeProgress.clear(this.state);
                }

                AnimationFileAutoSyncSupport.this.finishedJobs.offer(this);
            }
        }
    }

    /**
     * 每个 tick 该用哪段插值，一次性算好。
     *
     * <p>方块模型动画的插值挂在段内后一帧，姿势通道取段内首帧，两者错开一段，所以要用该
     * tick <b>之后</b>那个关键帧的插值：步阶帧的 CONST 才会落到跳变前那一帧上。</p>
     */
    private static final class InterpIndex
    {
        private final float[] ticks;
        private final IInterp[] interps;
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
