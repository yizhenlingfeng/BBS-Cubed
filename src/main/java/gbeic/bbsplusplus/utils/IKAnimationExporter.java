package gbeic.bbsplusplus.utils;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.ik.IKControl;
import mchorse.bbs_mod.cubic.ik.IKControls;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Samples BBS's final visual pose and writes it as ordinary Bedrock FK channels.
 * The BBS IK solver writes a transient quaternion instead of changing the FK
 * Euler fields, so reading only Pose/Transform values cannot export IK motion.
 */
public final class IKAnimationExporter
{
    private static final float BAKE_STEP = 1F;
    private static final float SUBDIVIDE_DEGREES = 30F;
    private static final float MIN_STEP = 0.2F;
    private static final float EPSILON = 0.0001F;
    private static final Field CUBIC_ORIENTATION = orientationField(ModelGroup.class);
    private static final Field BOBJ_ORIENTATION = orientationField(BOBJBone.class);

    private IKAnimationExporter()
    {}

    public static boolean hasIK(ModelInstance instance, ModelForm form)
    {
        if (instance == null || !(instance.model instanceof Model) || form == null)
        {
            return false;
        }

        mchorse.bbs_mod.forms.forms.Form previous = instance.form;

        try
        {
            instance.form = form;

            return !ModelIKRuntime.getControllers(instance).isEmpty();
        }
        finally
        {
            instance.form = previous;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void exportPose(File file, String animationName, boolean loop,
        ModelInstance instance, ModelForm form, List<Keyframe<Pose>> keyframes,
        Collection<String> boneOrder, List<Keyframe<IKControls>> ikControls) throws IOException
    {
        List<Keyframe<Pose>> sorted = sortPoseKeyframes(keyframes);

        if (sorted.isEmpty())
        {
            throw new IOException("No pose keyframes selected!");
        }

        Set<String> source = new LinkedHashSet<>();

        for (Keyframe<Pose> keyframe : sorted)
        {
            source.addAll(keyframe.getValue().transforms.keySet());
        }

        Map<String, List<Keyframe>> tracks = new LinkedHashMap<>();
        Map<String, Function<Keyframe, Transform>> getters = new LinkedHashMap<>();

        for (String bone : source)
        {
            tracks.put(bone, (List) sorted);
            getters.put(bone, (keyframe) ->
            {
                Transform transform = ((Pose) keyframe.getValue()).transforms.get(bone);

                return transform == null ? Transform.DEFAULT : transform;
            });
        }

        exportBaked(file, animationName, loop, instance, form, sorted.get(0).getTick(),
            sorted.get(sorted.size() - 1).getTick(), (tick) -> (Pose) PoseAnimationExporter.sampleValue((List) sorted, tick),
            tracks, getters, source, ikControls, (List) sorted, boneOrder);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void exportBoneTracks(File file, String animationName, boolean loop,
        ModelInstance instance, ModelForm form, Map<String, List<Keyframe<PoseTransform>>> tracks,
        Collection<String> boneOrder, List<Keyframe<IKControls>> ikControls) throws IOException
    {
        Map<String, List<Keyframe<PoseTransform>>> sorted = new LinkedHashMap<>();
        float minTick = Float.MAX_VALUE;
        float maxTick = -Float.MAX_VALUE;
        Set<String> source = new LinkedHashSet<>();

        for (Map.Entry<String, List<Keyframe<PoseTransform>>> entry : tracks.entrySet())
        {
            List<Keyframe<PoseTransform>> values = new ArrayList<>(entry.getValue());
            values.removeIf((keyframe) -> keyframe.getValue() == null);
            values.sort(Comparator.comparingDouble(Keyframe::getTick));

            if (values.isEmpty())
            {
                continue;
            }

            sorted.put(entry.getKey(), values);
            source.add(entry.getKey());
            minTick = Math.min(minTick, values.get(0).getTick());
            maxTick = Math.max(maxTick, values.get(values.size() - 1).getTick());
        }

        if (sorted.isEmpty())
        {
            throw new IOException("No pose keyframes selected!");
        }

        Map<String, List<Keyframe>> rawTracks = new LinkedHashMap<>();
        Map<String, Function<Keyframe, Transform>> getters = new LinkedHashMap<>();

        for (Map.Entry<String, List<Keyframe<PoseTransform>>> entry : sorted.entrySet())
        {
            rawTracks.put(entry.getKey(), (List) entry.getValue());
            getters.put(entry.getKey(), (keyframe) -> (Transform) keyframe.getValue());
        }

        exportBaked(file, animationName, loop, instance, form, minTick, maxTick, (tick) ->
        {
            Pose pose = new Pose();

            for (Map.Entry<String, List<Keyframe>> entry : rawTracks.entrySet())
            {
                Transform value = (Transform) PoseAnimationExporter.sampleValue(entry.getValue(), tick);

                if (value != null)
                {
                    pose.get(entry.getKey()).copy(value);
                }
            }

            return pose;
        }, rawTracks, getters, source, ikControls,
            rawTracks.values().stream().flatMap(Collection::stream).toList(), boneOrder);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void exportBaked(File file, String animationName, boolean loop,
        ModelInstance instance, ModelForm form, float minTick, float maxTick,
        Function<Float, Pose> poseSampler, Map<String, List<Keyframe>> directTracks,
        Map<String, Function<Keyframe, Transform>> directGetters, Set<String> sourceBones,
        List<Keyframe<IKControls>> ikControls,
        List<Keyframe> timingKeyframes, Collection<String> boneOrder) throws IOException
    {
        if (instance == null || !(instance.model instanceof Model))
        {
            throw new IOException("IK baking supports Cubic/Geo models only");
        }

        List<String> bones = orderedBones(instance.model, boneOrder, sourceBones);
        List<Keyframe<IKControls>> sortedControls = new ArrayList<>(
            ikControls == null ? Collections.emptyList() : ikControls);
        sortedControls.removeIf((keyframe) -> keyframe == null || keyframe.getValue() == null);
        sortedControls.sort(Comparator.comparingDouble(Keyframe::getTick));
        Map<String, JsonObject> direct = new LinkedHashMap<>();

        for (Map.Entry<String, List<Keyframe>> entry : directTracks.entrySet())
        {
            Function<Keyframe, Transform> getter = directGetters.get(entry.getKey());
            JsonObject channels = PoseAnimationExporter.buildChannels(entry.getValue(), getter, minTick, null);

            if (instance.model.getAllGroupKeys().contains(entry.getKey()))
            {
                channels.remove("rotation");
            }

            direct.put(entry.getKey(), channels);
        }

        try (BakeSession session = new BakeSession(instance, form, bones))
        {
            Map<String, TreeMap<Float, Vector3f>> samples = new LinkedHashMap<>();

            for (String bone : bones)
            {
                samples.put(bone, new TreeMap<>());
            }

            List<Keyframe> allTimingKeyframes = new ArrayList<>(timingKeyframes);

            allTimingKeyframes.addAll((List) sortedControls);

            List<Float> ticks = collectTicks(allTimingKeyframes, minTick, maxTick);

            for (float tick : ticks)
            {
                sample(session, poseSampler.apply(tick), sortedControls, tick, samples);
            }

            for (int pass = 0; pass < 6; pass++)
            {
                Set<Float> inserts = new TreeSet<>();

                for (Map.Entry<String, TreeMap<Float, Vector3f>> entry : samples.entrySet())
                {
                    TreeMap<Float, Vector3f> sequence = entry.getValue();
                    List<Float> sequenceTicks = new ArrayList<>(sequence.keySet());
                    List<Vector3f> unwrapped = unwrap(sequence, session.bindEuler(entry.getKey()));

                    for (int i = 0; i + 1 < sequenceTicks.size(); i++)
                    {
                        Vector3f a = unwrapped.get(i);
                        Vector3f b = unwrapped.get(i + 1);
                        float gap = sequenceTicks.get(i + 1) - sequenceTicks.get(i);
                        float distance = Math.abs(b.x - a.x) + Math.abs(b.y - a.y) + Math.abs(b.z - a.z);

                        if (distance > SUBDIVIDE_DEGREES && gap > MIN_STEP)
                        {
                            inserts.add((sequenceTicks.get(i) + sequenceTicks.get(i + 1)) / 2F);
                        }
                    }
                }

                if (inserts.isEmpty())
                {
                    break;
                }

                for (float tick : inserts)
                {
                    sample(session, poseSampler.apply(tick), sortedControls, tick, samples);
                }
            }

            JsonObject bonesJson = new JsonObject();

            for (String bone : bones)
            {
                JsonObject channels = direct.getOrDefault(bone, new JsonObject());
                TreeMap<Float, Vector3f> sequence = samples.get(bone);

                Vector3f bindEuler = session.bindEuler(bone);

                if (hasMotion(sequence, bindEuler))
                {
                    JsonObject rotation = new JsonObject();
                    List<Vector3f> unwrapped = unwrap(sequence, bindEuler);
                    int index = 0;

                    for (Float tick : sequence.keySet())
                    {
                        Vector3f degrees = unwrapped.get(index++).sub(bindEuler, new Vector3f());
                        rotation.add(PoseAnimationExporter.time(tick, minTick),
                            PoseAnimationExporter.makeValue(-degrees.x, -degrees.y, degrees.z, null));
                    }

                    channels.add("rotation", rotation);
                }

                if (channels.size() > 0)
                {
                    bonesJson.add(bone, channels);
                }
            }

            PoseAnimationExporter.writeAnimation(file, animationName,
                PoseAnimationExporter.assemble(loop, minTick, maxTick, bonesJson));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void sample(BakeSession session, Pose pose, List<Keyframe<IKControls>> controls,
        float tick, Map<String, TreeMap<Float, Vector3f>> samples)
    {
        session.model.resetPose();
        session.model.applyPose(pose == null ? new Pose() : pose);
        session.form.ikControlOverrides.clear();

        if (controls != null && !controls.isEmpty())
        {
            IKControls value = (IKControls) PoseAnimationExporter.sampleValue((List) controls, tick);

            if (value != null)
            {
                for (Map.Entry<String, IKControl> entry : value.controls.entrySet())
                {
                    session.form.ikControlOverrides.put(entry.getKey(), entry.getValue().copy());
                }
            }
        }

        ModelIKRuntime.apply(session.instance, null, null);

        Map<String, PivotFrame> frames = new HashMap<>();
        ModelPivotFrames.collect(session.model, session.wanted, frames);

        for (String bone : session.bones)
        {
            PivotFrame frame = frames.get(bone);
            Quaternionf local = session.localRotation(bone, frame);

            samples.get(bone).put(tick, local == null ? new Vector3f() : eulerZYXDegrees(local));
        }
    }

    private static Vector3f eulerZYXDegrees(Quaternionf quaternion)
    {
        Matrix3f matrix = new Matrix3f().set(quaternion);
        Vector3f euler = new Vector3f();
        float sinY = Math.max(-1F, Math.min(1F, matrix.m02));

        euler.y = (float) Math.asin(-sinY);

        if (Math.abs(sinY) < 0.9999F)
        {
            euler.x = (float) Math.atan2(matrix.m12, matrix.m22);
            euler.z = (float) Math.atan2(matrix.m01, matrix.m00);
        }
        else
        {
            euler.x = (float) Math.atan2(-matrix.m10, matrix.m11);
            euler.z = 0F;
        }

        return euler.mul((float) (180D / Math.PI));
    }

    private static Quaternionf localRotation(PivotFrame frame)
    {
        return new Quaternionf(frame.parentRotation()).conjugate().mul(frame.worldRotation()).normalize();
    }

    private static boolean hasMotion(TreeMap<Float, Vector3f> sequence, Vector3f bindEuler)
    {
        for (Vector3f value : unwrap(sequence, bindEuler))
        {
            if (Math.abs(value.x - bindEuler.x) > EPSILON
                || Math.abs(value.y - bindEuler.y) > EPSILON
                || Math.abs(value.z - bindEuler.z) > EPSILON)
            {
                return true;
            }
        }

        return false;
    }

    private static List<Vector3f> unwrap(TreeMap<Float, Vector3f> samples, Vector3f reference)
    {
        List<Vector3f> result = new ArrayList<>();
        Vector3f previous = new Vector3f(reference);

        for (Vector3f raw : samples.values())
        {
            Vector3f value = new Vector3f(raw);

            PoseAnimationExporter.unwrapNear(value, previous);

            result.add(value);
            previous = value;
        }

        return result;
    }

    private static List<Float> collectTicks(List<Keyframe> keyframes, float minTick, float maxTick)
    {
        Set<Float> ticks = new TreeSet<>();

        for (float tick = minTick; tick < maxTick - 0.0001F; tick += BAKE_STEP)
        {
            ticks.add(tick);
        }

        for (Keyframe keyframe : keyframes)
        {
            if (keyframe.getTick() >= minTick && keyframe.getTick() <= maxTick)
            {
                ticks.add(keyframe.getTick());
            }
        }

        ticks.add(maxTick);

        return new ArrayList<>(ticks);
    }

    private static List<Keyframe<Pose>> sortPoseKeyframes(List<Keyframe<Pose>> keyframes)
    {
        List<Keyframe<Pose>> sorted = new ArrayList<>(keyframes == null ? Collections.emptyList() : keyframes);
        sorted.removeIf((keyframe) -> keyframe == null || keyframe.getValue() == null);
        sorted.sort(Comparator.comparingDouble(Keyframe::getTick));

        return sorted;
    }

    private static List<String> orderedBones(IModel model, Collection<String> preferred, Set<String> source)
    {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();

        if (model != null)
        {
            ordered.addAll(model.getGroupKeysInHierarchyOrder());
        }

        if (preferred != null)
        {
            List<String> extras = new ArrayList<>(preferred);
            extras.removeAll(ordered);
            extras.sort(String::compareTo);
            ordered.addAll(extras);
        }

        if (source != null)
        {
            List<String> extras = new ArrayList<>(source);
            extras.removeAll(ordered);
            extras.sort(String::compareTo);
            ordered.addAll(extras);
        }

        return new ArrayList<>(ordered);
    }

    private static final class BakeSession implements AutoCloseable
    {
        private final ModelInstance instance;
        private final ModelForm form;
        private final IModel model;
        private final List<String> bones;
        private final Set<String> wanted;
        private final Map<String, BoneState> states = new LinkedHashMap<>();
        private final Map<String, Vector3f> bindEulers = new LinkedHashMap<>();
        private final Map<String, IKControl> oldControls = new HashMap<>();
        private final mchorse.bbs_mod.forms.forms.Form oldForm;

        private BakeSession(ModelInstance instance, ModelForm form, Collection<String> bones)
        {
            if (instance == null || instance.model == null || form == null)
            {
                throw new IllegalArgumentException("Model is not loaded");
            }

            this.instance = instance;
            this.form = form;
            this.model = instance.model;
            this.bones = new ArrayList<>(bones);
            this.wanted = new HashSet<>(this.bones);
            this.oldForm = instance.form;

            for (Map.Entry<String, IKControl> entry : form.ikControlOverrides.entrySet())
            {
                this.oldControls.put(entry.getKey(), entry.getValue().copy());
            }

            captureStates();
            instance.form = form;
            model.resetPose();

            for (String bone : this.bones)
            {
                this.bindEulers.put(bone, this.readBindEuler(bone));
            }
        }

        private void captureStates()
        {
            if (this.model instanceof Model cubic)
            {
                for (ModelGroup group : cubic.getAllGroups())
                {
                    this.states.put(group.id, new BoneState(group.current.copy(),
                        readOrientation(group, CUBIC_ORIENTATION), group.lighting, group.color.copy()));
                }
            }
            else if (this.model instanceof BOBJModel bobj)
            {
                for (BOBJBone bone : bobj.getArmature().orderedBones)
                {
                    this.states.put(bone.name, new BoneState(bone.transform.copy(),
                        readOrientation(bone, BOBJ_ORIENTATION), 0F, null));
                }
            }
        }

        private Vector3f readBindEuler(String bone)
        {
            if (this.model instanceof Model cubic)
            {
                ModelGroup group = cubic.getGroup(bone);

                return group == null ? new Vector3f() : new Vector3f(group.initial.rotate);
            }

            return new Vector3f();
        }

        private Vector3f bindEuler(String bone)
        {
            return this.bindEulers.getOrDefault(bone, new Vector3f());
        }

        private Quaternionf localRotation(String bone, PivotFrame fallback)
        {
            Quaternionf orientation = null;

            if (this.model instanceof Model cubic)
            {
                ModelGroup group = cubic.getGroup(bone);

                if (group != null)
                {
                    if (CUBIC_ORIENTATION == null && fallback != null)
                    {
                        orientation = IKAnimationExporter.localRotation(fallback);
                    }
                    else
                    {
                        orientation = readOrientation(group, CUBIC_ORIENTATION);

                        if (orientation == null)
                        {
                            /* createRotation 是 2.5 的唯一旋转读出口:弧度 ZYX 欧拉
                             * 或四元数模式都正确(旧代码把弧度喂给 Degrees 换算是错的) */
                            orientation = group.current.createRotation();
                        }
                    }
                }
            }
            else if (this.model instanceof BOBJModel bobj)
            {
                BOBJBone bobjBone = bobj.getArmature().bones.get(bone);

                if (bobjBone != null)
                {
                    orientation = readOrientation(bobjBone, BOBJ_ORIENTATION);

                    if (orientation == null)
                    {
                        orientation = bobjBone.transform.createRotation();
                    }
                }
            }

            if (orientation != null)
            {
                return orientation.normalize();
            }

            return fallback == null ? null : IKAnimationExporter.localRotation(fallback);
        }

        @Override
        public void close()
        {
            this.form.ikControlOverrides.clear();

            for (Map.Entry<String, IKControl> entry : this.oldControls.entrySet())
            {
                this.form.ikControlOverrides.put(entry.getKey(), entry.getValue().copy());
            }

            if (this.model instanceof Model cubic)
            {
                for (ModelGroup group : cubic.getAllGroups())
                {
                    BoneState state = this.states.get(group.id);

                    if (state != null)
                    {
                        group.current.copy(state.transform());
                        group.lighting = state.lighting();

                        if (state.color() != null)
                        {
                            group.color.copy(state.color());
                        }

                        writeOrientation(group, CUBIC_ORIENTATION, state.orientation());
                    }
                }
            }
            else if (this.model instanceof BOBJModel bobj)
            {
                for (BOBJBone bone : bobj.getArmature().orderedBones)
                {
                    BoneState state = this.states.get(bone.name);

                    if (state != null)
                    {
                        bone.transform.copy(state.transform());
                        writeOrientation(bone, BOBJ_ORIENTATION, state.orientation());
                    }
                }

                bobj.getArmature().setupMatrices();
            }

            this.instance.form = this.oldForm;
        }
    }

    private static Field orientationField(Class<?> type)
    {
        for (String name : new String[] {"orient", "ikOrient"})
        {
            try
            {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);

                return field;
            }
            catch (ReflectiveOperationException ignored)
            {
                /* The field was renamed between BBS builds. */
            }
        }

        return null;
    }

    private static Quaternionf readOrientation(Object object, Field field)
    {
        if (field == null)
        {
            return null;
        }

        try
        {
            Object value = field.get(object);

            return value instanceof Quaternionf quaternion ? new Quaternionf(quaternion) : null;
        }
        catch (IllegalAccessException ignored)
        {
            return null;
        }
    }

    private static void writeOrientation(Object object, Field field, Quaternionf orientation)
    {
        if (field == null)
        {
            return;
        }

        try
        {
            field.set(object, orientation == null ? null : new Quaternionf(orientation));
        }
        catch (IllegalAccessException ignored)
        {
            /* The runtime field is optional for older BBS builds. */
        }
    }

    private record BoneState(Transform transform, Quaternionf orientation, float lighting, Color color)
    {}
}
