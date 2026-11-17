package gbeic.bbsplusplus.client.morph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 映射有效性检查与展开。
 *
 * <p>校验规则：多对多不支持；同一个 Cube 不能属于多个互相冲突的映射组；
 * 引用必须能按稳定 key 解析，几何指纹变化时标记为失效；统计未映射 Cube。</p>
 *
 * <p>展开规则：1→1 一条变换；1→N 为每个目标 Cube 建立一条以同一源 Cube 为起点的变换；
 * N→1 为每个源 Cube 建立一条指向同一目标 Cube 的变换（Full Convergence）。</p>
 */
public final class ModelMorphValidation
{
    private ModelMorphValidation()
    {
    }

    /**
     * 校验映射，并额外检查「形变目标模型」与「终点实际模型」是否一致。
     *
     * @param nextModelId 该关键帧区间结束时实际切换到的模型 id；{@code null} 或空表示未知，跳过该项检查
     */
    public static Result validate(ModelMorphData data, ModelMorphSkeleton source, ModelMorphSkeleton target,
                                  String actualSourceId, String actualTargetId, String nextModelId)
    {
        Result result = validate(data, source, target, actualSourceId, actualTargetId);

        if (nextModelId == null || nextModelId.isEmpty() || data == null || !data.enabled())
        {
            return result;
        }

        String recorded = data.targetModel();

        /* 形变目标必须与终点实际切换到的模型一致，否则形变几何长成的是 A 的形状、
           纹理采样的是 A 的贴图，而区间结束却硬切到 B —— 目标端不是同一个模型，
           画面必然突兀（实测案例：target_model 指向只有 config.json 的空模型 cube，
           而下一帧实际是 model_morph_test/cube_test）。 */
        if (!recorded.isEmpty() && !recorded.equals(nextModelId) && !actualTargetId.equals(nextModelId))
        {
            result = result.withWarning("morph target (" + recorded
                + ") differs from the model the interval ends on (" + nextModelId
                + "); the transition will not match its endpoint");
        }

        return result;
    }

    public static Result validate(ModelMorphData data, ModelMorphSkeleton source, ModelMorphSkeleton target,
                                  String actualSourceId, String actualTargetId)
    {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<ResolvedGroup> resolved = new ArrayList<>();
        Set<String> usedSources = new LinkedHashSet<>();
        Set<String> usedTargets = new LinkedHashSet<>();
        Set<String> conflicts = new LinkedHashSet<>();

        if (data == null || !data.enabled())
        {
            return new Result(false, false, false, errors, warnings, resolved,
                usedSources, usedTargets, Collections.emptyList(), Collections.emptyList(),
                source == null ? 0 : source.cubeCount(), target == null ? 0 : target.cubeCount());
        }

        if (source == null || target == null || source.isEmpty() || target.isEmpty())
        {
            errors.add("source or target model has no cubes");

            return new Result(true, false, false, errors, warnings, resolved,
                usedSources, usedTargets, Collections.emptyList(), Collections.emptyList(), 0, 0);
        }

        /* 模型 id 漂移（换了模型、或 keyframe 被移动/复制）本身<b>不应阻塞播放</b>：
           映射记录的是 Cube 的稳定 key 与几何指纹，只要这些仍然解析得到，形变就能正常工作。
           真正会导致错误结果的是「Cube 引用解析不到」或「几何指纹变了」，那些检查在下面。
           这里只降级为警告，让用户知道配置里的模型名已经和当前不一致。 */
        if (!data.sourceModel().isEmpty() && !data.sourceModel().equals(actualSourceId))
        {
            warnings.add("source model changed (" + data.sourceModel() + " -> " + actualSourceId
                + "); mapping is still used, reopen the editor to refresh the stored name");
        }

        if (!data.targetModel().isEmpty() && !data.targetModel().equals(actualTargetId))
        {
            warnings.add("target model changed (" + data.targetModel() + " -> " + actualTargetId
                + "); mapping is still used, reopen the editor to refresh the stored name");
        }

        Map<String, ModelMorphSkeleton.CubeSpec> sourceCubes = source.cubesByKey();
        Map<String, ModelMorphSkeleton.CubeSpec> targetCubes = target.cubesByKey();

        for (MappingGroup group : data.groups())
        {
            int errorsBefore = errors.size();
            List<ModelMorphSkeleton.CubeSpec> sources = resolve(group.sources(), sourceCubes, "source", errors);
            List<ModelMorphSkeleton.CubeSpec> targets = resolve(group.targets(), targetCubes, "target", errors);

            /* 任一侧整组都解析不到时，逐条报错只会刷屏；折叠成一条可操作的提示。 */
            if (errors.size() > errorsBefore && (sources.isEmpty() || targets.isEmpty()))
            {
                String first = errors.get(errorsBefore);

                while (errors.size() > errorsBefore)
                {
                    errors.remove(errors.size() - 1);
                }

                errors.add("mapping group " + group.id() + " references cubes that no longer exist ("
                    + first + "); clear the mapping and re-create it");
            }

            MappingGroup.Type type = group.type();

            if (!type.isSupported())
            {
                errors.add("unsupported mapping type " + type.label() + " in group " + group.id());
                continue;
            }

            if (sources.isEmpty() || targets.isEmpty())
            {
                /* 已经就「引用已不存在」报过可操作提示时不再重复；
                   只有映射组本身为空才需要单独说明。 */
                if (errors.size() == errorsBefore)
                {
                    errors.add("empty or invalid mapping group " + group.id());
                }

                continue;
            }

            for (ModelMorphSkeleton.CubeSpec cube : sources)
            {
                if (!usedSources.add(cube.key()))
                {
                    conflicts.add(cube.key());
                }
            }

            for (ModelMorphSkeleton.CubeSpec cube : targets)
            {
                if (!usedTargets.add(cube.key()))
                {
                    conflicts.add(cube.key());
                }
            }

            resolved.add(new ResolvedGroup(group, type, sources, targets));
        }

        if (!conflicts.isEmpty())
        {
            errors.add(conflicts.size() + " cube(s) are used by conflicting mapping groups");
        }

        List<ModelMorphSkeleton.CubeSpec> unmappedSources = new ArrayList<>();
        List<ModelMorphSkeleton.CubeSpec> unmappedTargets = new ArrayList<>();

        for (ModelMorphSkeleton.CubeSpec cube : sourceCubes.values())
        {
            if (!usedSources.contains(cube.key()))
            {
                unmappedSources.add(cube);
            }
        }

        for (ModelMorphSkeleton.CubeSpec cube : targetCubes.values())
        {
            if (!usedTargets.contains(cube.key()))
            {
                unmappedTargets.add(cube);
            }
        }

        boolean complete = errors.isEmpty() && unmappedSources.isEmpty() && unmappedTargets.isEmpty();

        if (!unmappedSources.isEmpty())
        {
            warnings.add(unmappedSources.size() + " source cube(s) are unmapped");
        }

        if (!unmappedTargets.isEmpty())
        {
            warnings.add(unmappedTargets.size() + " target cube(s) are unmapped");
        }

        boolean playable = errors.isEmpty() && (complete || data.allowIncomplete());

        return new Result(true, playable, complete, errors, warnings, resolved,
            usedSources, usedTargets, unmappedSources, unmappedTargets,
            source.cubeCount(), target.cubeCount());
    }

    private static List<ModelMorphSkeleton.CubeSpec> resolve(
        List<MappingGroup.CubeRef> refs, Map<String, ModelMorphSkeleton.CubeSpec> cubes, String side, List<String> errors)
    {
        List<ModelMorphSkeleton.CubeSpec> result = new ArrayList<>();

        for (MappingGroup.CubeRef ref : refs)
        {
            ModelMorphSkeleton.CubeSpec cube = cubes.get(ref.key());

            if (cube == null)
            {
                /* 区分两种情形：整组都解析不到（多半是换了模型，旧映射已作废）
                   与个别 key 写错。前者给可操作的提示，而不是一串天书 key。 */
                boolean allMissing = refs.stream().noneMatch(other -> cubes.containsKey(other.key()));

                errors.add(allMissing
                    ? side + " model no longer has these cubes (" + ref.key()
                        + "); the model was probably replaced — clear the mapping and re-create it"
                    : side + " cube not found: " + ref.key());
                continue;
            }

            if (!ref.signature().isEmpty() && !ref.signature().equals(cube.signature()))
            {
                errors.add(side + " cube geometry changed: " + ref.key());
                continue;
            }

            result.add(cube);
        }

        return result;
    }

    /**
     * 把一条映射组展开成逐 Cube 的变换关系。
     *
     * <p>1:1 一条；1:N 为每个目标 Cube 建立一条以同一源 Cube 为起点的变换；
     * N:1 为每个源 Cube 建立一条指向同一目标 Cube 的变换（Full Convergence）。
     * 形变方案与自动检查共用这一份定义，避免两处各写一套。</p>
     */
    public static List<Transition> transitionsOf(ResolvedGroup group)
    {
        List<Transition> result = new ArrayList<>();

        switch (group.type())
        {
            case ONE_TO_ONE ->
                result.add(new Transition(group.sources().get(0), group.targets().get(0)));
            case ONE_TO_MANY ->
            {
                for (ModelMorphSkeleton.CubeSpec target : group.targets())
                {
                    result.add(new Transition(group.sources().get(0), target));
                }
            }
            case MANY_TO_ONE ->
            {
                for (ModelMorphSkeleton.CubeSpec source : group.sources())
                {
                    result.add(new Transition(source, group.targets().get(0)));
                }
            }
            default ->
            {
                /* 多对多不支持，交给校验层报错。 */
            }
        }

        return result;
    }

    /**
     * 展开成渲染用的逐 Cube 变换关系。键是源 Cube，值是该源 Cube 在形变期间要渲染的
     * 一个或多个过渡几何；未映射的源 Cube 以恒等变换保留原几何，不做静默丢弃。
     */
    public static Map<String, List<Transition>> expand(Result result)
    {
        Map<String, List<Transition>> transitions = new LinkedHashMap<>();

        for (ResolvedGroup group : result.groups())
        {
            for (Transition transition : transitionsOf(group))
            {
                addTransition(transitions, transition);
            }
        }

        for (ModelMorphSkeleton.CubeSpec cube : result.unmappedSources())
        {
            addTransition(transitions, new Transition(cube, cube));
        }

        return transitions;
    }

    private static void addTransition(Map<String, List<Transition>> transitions, Transition transition)
    {
        transitions.computeIfAbsent(transition.source().key(), key -> new ArrayList<>()).add(transition);
    }

    public record Transition(ModelMorphSkeleton.CubeSpec source, ModelMorphSkeleton.CubeSpec target)
    {
    }

    public record ResolvedGroup(MappingGroup group, MappingGroup.Type type,
                                List<ModelMorphSkeleton.CubeSpec> sources,
                                List<ModelMorphSkeleton.CubeSpec> targets)
    {
        public ResolvedGroup
        {
            sources = Collections.unmodifiableList(new ArrayList<>(sources));
            targets = Collections.unmodifiableList(new ArrayList<>(targets));
        }
    }

    public static final class Result
    {
        private final boolean enabled;
        private final boolean playable;
        private final boolean complete;
        private final List<String> errors;
        private final List<String> warnings;
        private final List<ResolvedGroup> groups;
        private final Set<String> usedSources;
        private final Set<String> usedTargets;
        private final List<ModelMorphSkeleton.CubeSpec> unmappedSources;
        private final List<ModelMorphSkeleton.CubeSpec> unmappedTargets;
        private final int sourceTotal;
        private final int targetTotal;

        private Result(boolean enabled, boolean playable, boolean complete,
                       List<String> errors, List<String> warnings, List<ResolvedGroup> groups,
                       Set<String> usedSources, Set<String> usedTargets,
                       List<ModelMorphSkeleton.CubeSpec> unmappedSources,
                       List<ModelMorphSkeleton.CubeSpec> unmappedTargets,
                       int sourceTotal, int targetTotal)
        {
            this.enabled = enabled;
            this.playable = playable;
            this.complete = complete;
            this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
            this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
            this.usedSources = Collections.unmodifiableSet(new LinkedHashSet<>(usedSources));
            this.usedTargets = Collections.unmodifiableSet(new LinkedHashSet<>(usedTargets));
            this.unmappedSources = Collections.unmodifiableList(new ArrayList<>(unmappedSources));
            this.unmappedTargets = Collections.unmodifiableList(new ArrayList<>(unmappedTargets));
            this.sourceTotal = sourceTotal;
            this.targetTotal = targetTotal;
        }

        public boolean enabled()
        {
            return this.enabled;
        }

        /** 是否可以进入形变渲染；false 时系统回退到原版模型切换。 */
        /**
         * 追加一条警告，其余状态保持不变。
         *
         * <p>用于在不改变「是否可播放」结论的前提下，补充与终点一致性相关的提示。</p>
         */
        public Result withWarning(String warning)
        {
            List<String> merged = new ArrayList<>(this.warnings);

            merged.add(warning);

            return new Result(this.enabled, this.playable, this.complete, this.errors, merged, this.groups,
                this.usedSources, this.usedTargets, this.unmappedSources, this.unmappedTargets,
                this.sourceTotal, this.targetTotal);
        }

        public boolean playable()
        {
            return this.playable;
        }

        public boolean complete()
        {
            return this.complete;
        }

        public List<String> errors()
        {
            return this.errors;
        }

        public List<String> warnings()
        {
            return this.warnings;
        }

        public List<ResolvedGroup> groups()
        {
            return this.groups;
        }

        public Set<String> usedSources()
        {
            return this.usedSources;
        }

        public Set<String> usedTargets()
        {
            return this.usedTargets;
        }

        public List<ModelMorphSkeleton.CubeSpec> unmappedSources()
        {
            return this.unmappedSources;
        }

        public List<ModelMorphSkeleton.CubeSpec> unmappedTargets()
        {
            return this.unmappedTargets;
        }

        public int sourceTotal()
        {
            return this.sourceTotal;
        }

        public int targetTotal()
        {
            return this.targetTotal;
        }

        public int mappedSourceCount()
        {
            return this.usedSources.size();
        }

        public int mappedTargetCount()
        {
            return this.usedTargets.size();
        }

        /**
         * 不可播放时的可读原因。
         *
         * <p>映射本身合法但被「允许不完整映射」开关拦下时，{@link #errors()} 是空的，
         * 直接打印会得到一句没有信息量的「不可播放：」。这里把这种情况补成明确说明。</p>
         */
        public String fallbackReason()
        {
            if (!this.errors.isEmpty())
            {
                return String.join("; ", this.errors);
            }

            if (this.groups.isEmpty())
            {
                return "no mapping groups";
            }

            return "mapping is incomplete ("
                + this.mappedSourceCount() + "/" + this.sourceTotal + " source, "
                + this.mappedTargetCount() + "/" + this.targetTotal + " target cubes) "
                + "and 'allow incomplete mapping' is off";
        }

    }
}
