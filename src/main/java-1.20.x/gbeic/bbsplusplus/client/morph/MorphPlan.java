package gbeic.bbsplusplus.client.morph;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.resources.Link;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个 (源模型, 目标模型, 映射配置) 的预计算形变方案。
 *
 * <p>方案在所有播放实例之间共享：它只保存不可变的端点数据与绑定姿态，
 * 采样缓冲区由每个表单实例自己的 {@link MorphGeometry} 持有，因此多个回放实例
 * 共用同一套模型资源也不会互相影响。</p>
 */
public final class MorphPlan
{
    /** 汇聚到同一目标时，末端重合的过渡体在超过这个进度后只渲染主过渡体，避免深度闪烁。 */
    public static final float CONVERGENCE_SUPPRESS_PROGRESS = 0.98F;

    /**
     * 克隆体错峰起始的跨度：越靠后的克隆体越晚开始分离。
     *
     * <p>早期取 0.35，克隆体要到进度 35% 才全部启动——前段几乎看不出分化，
     * 随后突然铺开，观感突兀。改为 0.12：分化从第一刻就连续展开。</p>
     */
    private static final float CLONE_STAGGER = 0.12F;

    private final Link sourceTexture;
    private final Link targetTexture;
    private final Map<ModelCube, List<Slot>> slots;
    private final List<MutationCube.UvRegion> uvRegions;
    private final List<String> warnings;

    private MorphPlan(Link sourceTexture, Link targetTexture, Map<ModelCube, List<Slot>> slots,
                      List<MutationCube.UvRegion> uvRegions, List<String> warnings)
    {
        this.uvRegions = Collections.unmodifiableList(new ArrayList<>(uvRegions));
        this.sourceTexture = sourceTexture;
        this.targetTexture = targetTexture;
        this.slots = slots;
        this.warnings = Collections.unmodifiableList(warnings);
    }

    public Link sourceTexture()
    {
        return this.sourceTexture;
    }

    public Link targetTexture()
    {
        return this.targetTexture;
    }

    public List<String> warnings()
    {
        return this.warnings;
    }

    /**
     * 各面的源/目标 UV 矩形。
     *
     * <p>贴图侧据此把源内容映射进目标区域后与目标淡化，使目标内容始终保持原生清晰度。</p>
     */
    public List<MutationCube.UvRegion> uvRegions()
    {
        return this.uvRegions;
    }

    public boolean isEmpty()
    {
        return this.slots.isEmpty();
    }

    public List<Slot> slots(ModelCube cube)
    {
        return this.slots.get(cube);
    }

    public Map<ModelCube, List<Slot>> slots()
    {
        return this.slots;
    }

    /**
     * 建立形变方案。任一映射组解析失败时跳过该组并记录警告，其余映射照常工作。
     */
    public static MorphPlan build(ModelInstance sourceInstance, ModelInstance targetInstance,
                                  ModelMorphData data, ModelMorphValidation.Result validation)
    {
        if (!(sourceInstance == null ? false : sourceInstance.model instanceof Model)
            || !(targetInstance == null ? false : targetInstance.model instanceof Model))
        {
            return null;
        }

        Model sourceModel = (Model) sourceInstance.model;
        Model targetModel = (Model) targetInstance.model;

        if (!ModelMorphAdapter.isCubeOnly(sourceModel) || !ModelMorphAdapter.isCubeOnly(targetModel))
        {
            return null;
        }

        ModelMorphSkeleton sourceSkeleton = ModelMorphAdapter.skeleton(sourceModel);
        ModelMorphSkeleton targetSkeleton = ModelMorphAdapter.skeleton(targetModel);
        Map<String, ModelCube> sourceCubes = ModelMorphAdapter.cubeIndex(sourceModel);
        Map<String, ModelCube> targetCubes = ModelMorphAdapter.cubeIndex(targetModel);
        Map<ModelCube, List<Slot>> slots = new IdentityHashMap<>();
        List<String> warnings = new ArrayList<>(validation.warnings());
        Map<String, Matrix4f> inverseSourceFrames = new LinkedHashMap<>();

        for (ModelMorphValidation.ResolvedGroup group : validation.groups())
        {
            if (!group.type().isSupported())
            {
                warnings.add("unsupported mapping type skipped: " + group.type().label());
                continue;
            }

            int count = group.type() == MappingGroup.Type.ONE_TO_MANY ? group.targets().size()
                : group.type() == MappingGroup.Type.MANY_TO_ONE ? group.sources().size() : 1;
            boolean clone = group.type() == MappingGroup.Type.ONE_TO_MANY;
            int index = 0;

            for (ModelMorphValidation.Transition transition : ModelMorphValidation.transitionsOf(group))
            {
                ModelCube sourceCube = sourceCubes.get(transition.source().key());
                ModelCube targetCube = targetCubes.get(transition.target().key());

                if (sourceCube == null || targetCube == null)
                {
                    warnings.add("cube pair skipped: " + transition.source().key() + " -> " + transition.target().key());
                    continue;
                }

                Matrix4f sourceFrame = MorphMath.cubePivotFrame(sourceCube.pivot, sourceCube.rotate);
                Matrix4f inverseSource = inverseSourceFrames.get(transition.source().groupPath());

                if (inverseSource == null)
                {
                    inverseSource = MorphMath.inverseOrNull(sourceSkeleton.byPath().get(transition.source().groupPath()).restFrame());

                    if (inverseSource == null)
                    {
                        warnings.add("source bone frame is singular: " + transition.source().groupPath());
                        continue;
                    }

                    inverseSourceFrames.put(transition.source().groupPath(), inverseSource);
                }

                ModelMorphSkeleton.GroupSpec targetGroup = targetSkeleton.byPath().get(transition.target().groupPath());

                if (targetGroup == null)
                {
                    warnings.add("target bone frame missing: " + transition.target().groupPath());
                    continue;
                }

                Matrix4f targetFrame = new Matrix4f(inverseSource)
                    .mul(targetGroup.restFrame())
                    .mul(MorphMath.cubePivotFrame(targetCube.pivot, targetCube.rotate));

                MutationCube mutation = MutationCube.build(sourceCube, targetCube, sourceFrame, targetFrame);

                if (mutation == null)
                {
                    warnings.add("cube pair has no renderable faces: " + transition.source().key());
                    continue;
                }

                /* index 只统计成功编译的过渡体：如果组内第一个目标无效，下一条有效克隆
                   仍必须成为可见的主过渡体，不能让源 Cube 在起点消失。 */
                if (clone && count > 1)
                {
                    mutation.setStagger(CLONE_STAGGER * index / count);
                }

                Slot slot = new Slot(mutation, clone, count, index);

                slots.computeIfAbsent(sourceCube, key -> new ArrayList<>()).add(slot);
                index++;
            }
        }

        if (slots.isEmpty())
        {
            warnings.add("no cube could be mapped; morph falls back to the vanilla model switch");

            return null;
        }

        Link sourceTexture = sourceInstance.getTexture();
        Link targetTexture = targetInstance.getTexture();

        if (sourceTexture == null)
        {
            sourceTexture = sourceInstance.baseTexture;
        }

        if (targetTexture == null)
        {
            targetTexture = targetInstance.baseTexture;
        }

        return new MorphPlan(sourceTexture, targetTexture, slots,
            collectRegions(slots), warnings);
    }

    /**
     * 汇总各过渡体的 UV 区域。
     */
    private static List<MutationCube.UvRegion> collectRegions(Map<ModelCube, List<Slot>> slots)
    {
        List<MutationCube.UvRegion> regions = new ArrayList<>();

        for (List<Slot> list : slots.values())
        {
            for (Slot slot : list)
            {
                regions.addAll(slot.mutation().regions());
            }
        }

        return regions;
    }

    /**
     * 一条过渡体的运行时描述。
     */
    public record Slot(MutationCube mutation, boolean cloning, int count, int index)
    {
        /**
         * 是否在该进度下渲染。
         *
         * <p>克隆分化在起点、完整汇聚在终点会出现多个几何完全重合的过渡体。重合的共面
         * 几何必然互相深度竞争（闪烁与碎裂），因此只在「确实已分离」时绘制。</p>
         *
         * <p><b>判定基于几何位移，而不是进度阈值。</b>早期用固定阈值
         * （localProgress ≥ 0.06）判定，克隆体会在某一个 tick 从「不画」直接跳到「画」，
         * 这就是首帧突兀的来源。位移由进度连续驱动、从 0 开始增长，越过极小阈值的时刻
         * 本身就是连续的；阈值取 1/4096 模型单位（远小于一个像素），重合阶段即便被绘制
         * 也几乎完全叠在源几何上，不会产生可见跳变。</p>
         */
        public boolean visibleAt(float progress)
        {
            if (this.count <= 1 || this.index == 0)
            {
                return true;
            }

            if (this.cloning)
            {
                return this.mutation.hasSeparated(this.mutation.localProgress(progress));
            }

            /* 汇聚体进入近乎重合的区域后，只保留主过渡体，避免多个共面面片深度闪烁。
               小幅运动可能更早收敛；较大运动则统一在 98% 处完成重叠抑制。 */
            return progress < CONVERGENCE_SUPPRESS_PROGRESS && !this.mutation.hasConverged(progress);
        }
    }
}
