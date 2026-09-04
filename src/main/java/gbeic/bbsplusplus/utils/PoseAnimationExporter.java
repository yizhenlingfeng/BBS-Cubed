package gbeic.bbsplusplus.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import mchorse.bbs_mod.cubic.data.animation.AnimationInterpolation;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
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
import java.util.function.Function;

/**
 * 把 pose 关键帧序列导出为 Bedrock 动画 json(Blockbench 可直接导入,BBS 的
 * Geo 模型加载器也会作为模型动画读回)。
 *
 * <p>换算依据是 bbs 内部两条互逆的应用链:{@code Model.applyPose} 把 pose 的
 * rotate(弧度)转成度数直接加到骨骼上;{@code CubicModelAnimator} 应用 bedrock
 * 动画时对 rotation 的 X/Y 轴乘 -1 再加到骨骼上。因此导出时 rotation 写
 * {@code [-deg(rx), -deg(ry), deg(rz)]},position/scale 两侧语义一致原样照抄,
 * 时间为 tick/20 秒。四元数模式(rotation_mode)的骨骼经 {@link #effectiveRotate}
 * 降维,插值路径由烘焙保真;fix/color/lighting 在 bedrock 格式没有对应物,忽略。</p>
 */
public class PoseAnimationExporter
{
    /** 混层旋转烘焙的采样步长(tick):1 tick = 0.05 秒,与游戏刻对齐 */
    private static final float BAKE_STEP = 1F;

    /** 相邻烘焙帧欧拉 L1 差超过该度数时二分细分(万向锁附近欧拉发散区) */
    private static final float BAKE_SUBDIVIDE_DEGREES = 30F;

    /** 细分的最小 tick 间隔 */
    private static final float BAKE_MIN_STEP = 0.2F;
    /**
     * IInterp -> GeckoLib easing 名的反查表(linear 是默认省略,catmullrom 走
     * lerp_mode 字段)。easing 是 GeckoLib 扩展字段,原生 Blockbench 会忽略它
     * 回退 linear,装了 GeckoLib 插件或导回 BBS 时则还原完整缓动。
     */
    private static final Map<IInterp, String> EASING_NAMES = new HashMap<>();

    static
    {
        for (Map.Entry<String, IInterp> entry : AnimationInterpolation.GECKO_LIB_NAMES.entrySet())
        {
            if (!entry.getKey().equals("linear") && !entry.getKey().equals("catmullrom"))
            {
                EASING_NAMES.put(entry.getValue(), entry.getKey());
            }
        }

        EASING_NAMES.putIfAbsent(Interpolations.STEP, "step");

        /* 本插件扩展标记:BEZIER pose 关键帧往返保真(GeoAnimationParser 经
         * AnimationInterpInheritance.registerBezierEasing 注册后能读回;
         * 原生 Blockbench 忽略 easing 字段,按线性显示,不影响导入) */
        EASING_NAMES.putIfAbsent(Interpolations.BEZIER, "bezier");
    }

    /**
     * 把选中的 pose 关键帧写入目标动画文件。文件已存在时解析合并(同名动画覆盖,
     * 其余动画保留),不存在时新建标准 bedrock 动画文件。
     *
     * @param boneOrder 模型的骨骼顺序(仅决定 json 中骨骼的排列,pose 里额外的
     *                  骨骼按字母序追加在后)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void export(File file, String animationName, boolean loop, List<Keyframe<Pose>> keyframes, Collection<String> boneOrder) throws IOException
    {
        List<Keyframe<Pose>> sorted = new ArrayList<>(keyframes);

        sorted.removeIf((kf) -> kf.getValue() == null);
        sorted.sort(Comparator.comparingDouble(Keyframe::getTick));

        if (sorted.isEmpty())
        {
            throw new IOException("No pose keyframes selected!");
        }

        float minTick = sorted.get(0).getTick();
        float maxTick = sorted.get(sorted.size() - 1).getTick();

        Set<String> present = new HashSet<>();

        for (Keyframe<Pose> keyframe : sorted)
        {
            present.addAll(keyframe.getValue().transforms.keySet());
        }

        LinkedHashSet<String> bones = new LinkedHashSet<>();

        if (boneOrder != null)
        {
            for (String bone : boneOrder)
            {
                if (present.contains(bone))
                {
                    bones.add(bone);
                }
            }
        }

        List<String> rest = new ArrayList<>(present);

        rest.removeAll(bones);
        Collections.sort(rest);
        bones.addAll(rest);

        JsonObject bonesJson = new JsonObject();

        for (String bone : bones)
        {
            final String key = bone;
            Function<Keyframe, Transform> getter = poseGetter(bone);
            Function<Float, Transform> sampler = null;

            if (isMixedLayers((List) sorted, getter))
            {
                /* factory.interpolate 返回内部复用对象,采样后必须即时快照 */
                sampler = (tick) ->
                {
                    Pose sampled = (Pose) sampleValue((List) sorted, tick);
                    Transform transform = sampled == null ? null : sampled.transforms.get(key);

                    return snapshotRotation(transform == null ? Transform.DEFAULT : transform);
                };
            }

            JsonObject boneJson = buildChannels((List) sorted, getter, minTick, sampler);

            if (boneJson.size() > 0)
            {
                bonesJson.add(bone, boneJson);
            }
        }

        writeAnimation(file, animationName, assemble(loop, minTick, maxTick, bonesJson));
    }

    private static Function<Keyframe, Transform> poseGetter(String bone)
    {
        return (kf) ->
        {
            Transform transform = ((Pose) kf.getValue()).transforms.get(bone);

            return transform == null ? Transform.DEFAULT : transform;
        };
    }

    /** 只拷贝旋转字段(烘焙仅用于 rotation 通道),脱离 factory 的复用对象 */
    private static Transform snapshotRotation(Transform source)
    {
        Transform copy = new Transform();

        copy.rotate.set(source.rotate);
        copy.quat.set(source.quat);
        copy.rotationMode = source.rotationMode;

        return copy;
    }

    /**
     * 肢体轨道导出:每条骨骼轨道的 {@code Keyframe<PoseTransform>} 独立成
     * bedrock 通道时间键,不同骨骼可以有各自的关键帧时间点(相比整体 pose
     * 导出更贴合 bedrock 的 per-bone 结构,无需补默认值帧)。
     *
     * @param tracks 骨骼名 -> 该骨骼轨道选中的关键帧(应按模型层级序传入)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void exportBoneTracks(File file, String animationName, boolean loop, Map<String, List<Keyframe<PoseTransform>>> tracks) throws IOException
    {
        float minTick = Float.MAX_VALUE;
        float maxTick = -Float.MAX_VALUE;

        Map<String, List<Keyframe<PoseTransform>>> sorted = new LinkedHashMap<>();

        for (Map.Entry<String, List<Keyframe<PoseTransform>>> entry : tracks.entrySet())
        {
            List<Keyframe<PoseTransform>> keyframes = new ArrayList<>(entry.getValue());

            keyframes.removeIf((kf) -> kf.getValue() == null);
            keyframes.sort(Comparator.comparingDouble(Keyframe::getTick));

            if (keyframes.isEmpty())
            {
                continue;
            }

            minTick = Math.min(minTick, keyframes.get(0).getTick());
            maxTick = Math.max(maxTick, keyframes.get(keyframes.size() - 1).getTick());
            sorted.put(entry.getKey(), keyframes);
        }

        if (sorted.isEmpty())
        {
            throw new IOException("No pose keyframes selected!");
        }

        JsonObject bonesJson = new JsonObject();

        for (Map.Entry<String, List<Keyframe<PoseTransform>>> entry : sorted.entrySet())
        {
            List<Keyframe<PoseTransform>> keyframes = entry.getValue();
            Function<Keyframe, Transform> getter = (kf) -> (Transform) kf.getValue();
            Function<Float, Transform> sampler = null;

            if (isMixedLayers((List) keyframes, getter))
            {
                sampler = (tick) ->
                {
                    Transform transform = (Transform) sampleValue((List) keyframes, tick);

                    return snapshotRotation(transform == null ? Transform.DEFAULT : transform);
                };
            }

            JsonObject boneJson = buildChannels((List) keyframes, getter, minTick, sampler);

            if (boneJson.size() > 0)
            {
                bonesJson.add(entry.getKey(), boneJson);
            }
        }

        writeAnimation(file, animationName, assemble(loop, minTick, maxTick, bonesJson));
    }

    static JsonObject assemble(boolean loop, float minTick, float maxTick, JsonObject bonesJson)
    {
        JsonObject animation = new JsonObject();

        if (loop)
        {
            animation.addProperty("loop", true);
        }

        animation.add("animation_length", num((maxTick - minTick) / 20F));
        animation.add("bones", bonesJson);

        return animation;
    }

    static void writeAnimation(File file, String animationName, JsonObject animation) throws IOException
    {
        JsonObject root = AnimationFileOperations.readRoot(file);

        root.getAsJsonObject("animations").add(animationName, animation);
        AnimationFileOperations.writeRoot(file, root);
    }

    /**
     * 通道启用规则:任一关键帧上非默认值才写该通道。整体 pose 导出时启用的
     * 通道在每个关键帧时间点都写值,骨骼在某帧缺席时由 getter 落回默认
     * (pose 缺席 = 回绑定姿势,漏写会被前后帧插值顶掉)。
     *
     * <p>{@code sampler} 非空时 rotation 通道走烘焙:双层旋转(R+R2)混用的
     * 骨骼没有等效的单层欧拉插值路径 —— 端点合成正确但 bedrock 逐轴插值的
     * 中间过程会绕远路,所以按 BBS 的真实插值采样成密集 linear 帧。
     * position/scale 不受影响。</p>
     */
    static JsonObject buildChannels(List<Keyframe> keyframes, Function<Keyframe, Transform> getter, float minTick, Function<Float, Transform> sampler)
    {
        boolean hasRotation = false;
        boolean hasPosition = false;
        boolean hasScale = false;

        for (Keyframe keyframe : keyframes)
        {
            Transform transform = getter.apply(keyframe);

            if (transform == Transform.DEFAULT)
            {
                continue;
            }

            hasRotation = hasRotation
                || transform.rotate.x != 0F || transform.rotate.y != 0F || transform.rotate.z != 0F
                || isQuaternionRotation(transform);
            hasPosition = hasPosition || transform.translate.x != 0F || transform.translate.y != 0F || transform.translate.z != 0F;
            hasScale = hasScale || transform.scale.x != 1F || transform.scale.y != 1F || transform.scale.z != 1F;
        }

        JsonObject rotation = hasRotation && sampler != null
            ? bakeRotation(keyframes, sampler, minTick)
            : new JsonObject();
        JsonObject position = new JsonObject();
        JsonObject scale = new JsonObject();

        for (Keyframe keyframe : keyframes)
        {
            Transform transform = getter.apply(keyframe);

            String time = time(keyframe.getTick(), minTick);
            IInterp interp = keyframe.getInterpolation().getInterp();

            if (hasRotation && sampler == null)
            {
                Vector3f rotate = effectiveRotate(transform);

                rotation.add(time, makeValue(
                    -(float) Math.toDegrees(rotate.x),
                    -(float) Math.toDegrees(rotate.y),
                    (float) Math.toDegrees(rotate.z),
                    interp
                ));
            }

            if (hasPosition)
            {
                position.add(time, makeValue(transform.translate.x, transform.translate.y, transform.translate.z, interp));
            }

            if (hasScale)
            {
                scale.add(time, makeValue(transform.scale.x, transform.scale.y, transform.scale.z, interp));
            }
        }

        JsonObject bone = new JsonObject();

        if (hasRotation) bone.add("rotation", rotation);
        if (hasPosition) bone.add("position", position);
        if (hasScale) bone.add("scale", scale);

        return bone;
    }

    /**
     * 混层旋转烘焙:基础步长 1 tick 采样,再对欧拉变化超过阈值的区间自适应
     * 二分细分(路径穿越万向锁附近时 ZYX 欧拉本质发散,单帧间直线插值会偏航,
     * 细分把直线段压短到视觉无差)。输出全部为 linear 纯数组帧。
     */
    private static JsonObject bakeRotation(List<Keyframe> keyframes, Function<Float, Transform> sampler, float minTick)
    {
        TreeMap<Float, Vector3f> samples = new TreeMap<>();

        for (float tick : collectBakeTicks(keyframes, BAKE_STEP))
        {
            samples.put(tick, rawEulerDegrees(sampler.apply(tick)));
        }

        for (int pass = 0; pass < 6; pass++)
        {
            List<Float> ticks = new ArrayList<>(samples.keySet());
            List<Vector3f> unwrapped = unwrapSequence(samples);
            List<Float> inserts = new ArrayList<>();

            for (int i = 0; i < ticks.size() - 1; i++)
            {
                float gap = ticks.get(i + 1) - ticks.get(i);
                Vector3f a = unwrapped.get(i);
                Vector3f b = unwrapped.get(i + 1);
                float distance = Math.abs(b.x - a.x) + Math.abs(b.y - a.y) + Math.abs(b.z - a.z);

                if (distance > BAKE_SUBDIVIDE_DEGREES && gap > BAKE_MIN_STEP)
                {
                    inserts.add((ticks.get(i) + ticks.get(i + 1)) / 2F);
                }
            }

            if (inserts.isEmpty())
            {
                break;
            }

            for (float tick : inserts)
            {
                samples.put(tick, rawEulerDegrees(sampler.apply(tick)));
            }
        }

        JsonObject rotation = new JsonObject();
        List<Vector3f> unwrapped = unwrapSequence(samples);
        int i = 0;

        for (Float tick : samples.keySet())
        {
            Vector3f degrees = unwrapped.get(i++);

            rotation.add(time(tick, minTick), makeValue(-degrees.x, -degrees.y, degrees.z, null));
        }

        return rotation;
    }

    /** effectiveRotate 的度数副本(未连续化) */
    private static Vector3f rawEulerDegrees(Transform transform)
    {
        Vector3f euler = effectiveRotate(transform);

        return new Vector3f(
            (float) Math.toDegrees(euler.x),
            (float) Math.toDegrees(euler.y),
            (float) Math.toDegrees(euler.z)
        );
    }

    /** 对采样序列做顺序欧拉连续化,返回连续化后的副本列表 */
    private static List<Vector3f> unwrapSequence(TreeMap<Float, Vector3f> samples)
    {
        List<Vector3f> result = new ArrayList<>(samples.size());
        Vector3f previous = null;

        for (Vector3f raw : samples.values())
        {
            Vector3f degrees = new Vector3f(raw);

            if (previous != null)
            {
                unwrapNear(degrees, previous);
            }

            previous = degrees;
            result.add(degrees);
        }

        return result;
    }

    /**
     * 烘焙判定:轨道里存在四元数模式的关键帧(BBS 2.5 的 rotation_mode,或与欧拉
     * 关键帧混排)时,逐轴欧拉插值无法表达 BBS 的 slerp/逐分量四元数插值路径,
     * 按 BBS 的真实插值采样成密集 linear 帧。纯欧拉轨道保持原有关键帧导出。
     */
    private static boolean isMixedLayers(List<Keyframe> keyframes, Function<Keyframe, Transform> getter)
    {
        for (Keyframe keyframe : keyframes)
        {
            Transform transform = getter.apply(keyframe);

            if (transform == Transform.DEFAULT)
            {
                continue;
            }

            if (transform.rotationMode == Transform.RotationMode.QUATERNION)
            {
                return true;
            }
        }

        return false;
    }

    /** 四元数模式下是否持有非恒等旋转(欧拉通道此时只是过期缓存) */
    private static boolean isQuaternionRotation(Transform transform)
    {
        return transform.rotationMode == Transform.RotationMode.QUATERNION
            && (transform.quat.x != 0F || transform.quat.y != 0F || transform.quat.z != 0F || transform.quat.w != 1F);
    }

    /** 烘焙采样时间点:所有原关键帧 tick + 相邻帧之间按 step 递进的中间点 */
    private static List<Float> collectBakeTicks(List<Keyframe> sorted, float step)
    {
        List<Float> ticks = new ArrayList<>();

        for (int i = 0; i < sorted.size() - 1; i++)
        {
            float a = sorted.get(i).getTick();
            float b = sorted.get(i + 1).getTick();

            ticks.add(a);

            for (float t = a + step; t < b - 0.0001F; t += step)
            {
                ticks.add(t);
            }
        }

        ticks.add(sorted.get(sorted.size() - 1).getTick());

        return ticks;
    }

    /** 按 BBS 播放语义对选中关键帧序列求 tick 处的插值。 */
    @SuppressWarnings("unchecked")
    static Object sampleValue(List<Keyframe> sorted, float tick)
    {
        int count = sorted.size();

        if (tick <= sorted.get(0).getTick())
        {
            Keyframe first = sorted.get(0);

            return first.getFactory().copy(first.getValue());
        }

        if (tick >= sorted.get(count - 1).getTick())
        {
            Keyframe last = sorted.get(count - 1);

            return last.getFactory().copy(last.getValue());
        }

        int index = 0;

        while (index + 2 < count && sorted.get(index + 1).getTick() <= tick)
        {
            index++;
        }

        Keyframe a = sorted.get(index);
        Keyframe b = sorted.get(index + 1);
        float duration = a.getDuration() > 0F ? a.getDuration() : b.getTick() - a.getTick();

        if (duration <= 0F)
        {
            return a.getFactory().copy(a.getValue());
        }

        Keyframe preA = sorted.get(Math.max(index - 1, 0));
        Keyframe postB = sorted.get(Math.min(index + 2, count - 1));
        float x = Math.max(0F, Math.min(1F, (tick - a.getTick()) / duration));

        Object interpolated = a.getFactory().interpolate(preA, a, b, postB, a.getInterpolation(), x);

        return a.getFactory().copy(interpolated);
    }

    /**
     * 欧拉连续化:ZYX 欧拉双重覆盖((x,y,z) 与 (x+180°,180°−y,z+180°) 同旋转)
     * 加每轴 ±360k,从等效表示中选与前一帧 L1 距离最小的,消除采样序列跳变。
     */
    static void unwrapNear(Vector3f degrees, Vector3f previous)
    {
        Vector3f flipped = new Vector3f(degrees.x + 180F, 180F - degrees.y, degrees.z + 180F);

        wrapAxes(degrees, previous);
        wrapAxes(flipped, previous);

        float direct = Math.abs(degrees.x - previous.x) + Math.abs(degrees.y - previous.y) + Math.abs(degrees.z - previous.z);
        float alternate = Math.abs(flipped.x - previous.x) + Math.abs(flipped.y - previous.y) + Math.abs(flipped.z - previous.z);

        if (alternate < direct)
        {
            degrees.set(flipped);
        }
    }

    /** 每轴加减 360k 移到 previous±180 范围内 */
    private static void wrapAxes(Vector3f degrees, Vector3f previous)
    {
        degrees.x += 360F * Math.round((previous.x - degrees.x) / 360F);
        degrees.y += 360F * Math.round((previous.y - degrees.y) / 360F);
        degrees.z += 360F * Math.round((previous.z - degrees.z) / 360F);
    }

    /**
     * 取骨骼的有效旋转(弧度 ZYX):BBS 2.5 起旋转有两种存储(rotation_mode),
     * {@link Transform#getEulerRotation} 是官方给"只能装欧拉的目标"的唯一读出口
     * —— 欧拉模式返回 rotate 原值(保留连续大角度),四元数模式分解 quat。
     * bedrock 格式只有单层 rotation,四元数骨骼经它降维(烘焙路径已覆盖插值
     * 保真,直接导出仅在未触发烘焙时用于端点值)。
     */
    private static Vector3f effectiveRotate(Transform transform)
    {
        return transform.getEulerRotation(new Vector3f());
    }

    static JsonElement makeValue(float x, float y, float z, IInterp interp)
    {
        JsonArray array = new JsonArray();

        array.add(num(x));
        array.add(num(y));
        array.add(num(z));

        if (interp == Interpolations.HERMITE)
        {
            JsonObject object = new JsonObject();

            object.add("post", array);
            object.addProperty("lerp_mode", "catmullrom");

            return object;
        }

        String easing = interp == null ? null : EASING_NAMES.get(interp);

        if (easing != null)
        {
            JsonObject object = new JsonObject();

            object.add("post", array);
            object.addProperty("easing", easing);

            return object;
        }

        return array;
    }

    static String time(float tick, float minTick)
    {
        return String.valueOf(round((tick - minTick) / 20F));
    }

    static JsonPrimitive num(float value)
    {
        float rounded = round(value);

        if (rounded == (long) rounded)
        {
            return new JsonPrimitive((long) rounded);
        }

        return new JsonPrimitive(rounded);
    }

    private static float round(float value)
    {
        return Math.round(value * 10000F) / 10000F;
    }
}
