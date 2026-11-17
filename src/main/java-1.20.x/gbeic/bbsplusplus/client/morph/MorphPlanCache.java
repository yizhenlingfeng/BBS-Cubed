package gbeic.bbsplusplus.client.morph;

import gbeic.bbsplusplus.BBSPlusPlusMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 形变方案缓存：按「源模型 + 目标模型 + 映射配置哈希」缓存，避免每帧重复匹配 Cube。
 *
 * <p>缓存只保存不可变的端点数据，不保存任何实例状态；模型资源重载时必须整体失效。</p>
 */
public final class MorphPlanCache
{
    private static final int MAX_ENTRIES = 32;
    private static final Map<Key, MorphPlan> CACHE = new LinkedHashMap<>(8, 0.75F, true);

    /**
     * 解析失败的键。
     *
     * <p>形变状态每帧都会因为「属性应用前清空」而重新解析，若不记住失败结果，
     * 一个不完整或引用失效的映射会每帧重建骨架、跑一遍校验并刷一条日志。
     * 这里把失败也缓存起来，只在模型资源重载时失效。</p>
     */
    private static final Map<Key, Boolean> FAILED = new LinkedHashMap<>(8, 0.75F, true);

    private MorphPlanCache()
    {
    }

    public static MorphPlan get(String sourceId, String targetId, ModelMorphData data)
    {
        return get(sourceId, targetId, data, "");
    }

    public static MorphPlan get(String sourceId, String targetId, ModelMorphData data, String nextModelId)
    {
        if (sourceId == null || targetId == null || data == null || sourceId.isEmpty() || targetId.isEmpty())
        {
            return null;
        }

        Key key = new Key(sourceId, targetId, data.hashCode());

        if (FAILED.containsKey(key))
        {
            return null;
        }

        MorphPlan cached = CACHE.get(key);

        if (cached != null)
        {
            return cached;
        }

        MorphPlan plan = build(sourceId, targetId, data, nextModelId);

        if (plan != null)
        {
            CACHE.put(key, plan);

            /* 每个方案只在首次建立时报告一次：确认映射编译成功、涉及多少个过渡几何，
               比每帧刷日志有用，也让「装了但没生效」这种情况一眼可见。 */
            BBSPlusPlusMod.LOGGER.info("[Model Morph] {} -> {} ready: {} mapped source cube(s), {} transition(s){}",
                sourceId, targetId, plan.slots().size(), countSlots(plan),
                plan.warnings().isEmpty() ? "" : " (" + String.join("; ", plan.warnings()) + ")");
        }
        else
        {
            FAILED.put(key, Boolean.TRUE);
        }

        trim();

        return plan;
    }

    private static MorphPlan build(String sourceId, String targetId, ModelMorphData data, String nextModelId)
    {
        ModelInstance source = BBSModClient.getModels().getModel(sourceId);
        ModelInstance target = BBSModClient.getModels().getModel(targetId);

        if (source == null || target == null)
        {
            return null;
        }

        ModelMorphSkeleton sourceSkeleton = source.model instanceof mchorse.bbs_mod.cubic.data.model.Model model
            ? ModelMorphAdapter.skeleton(model) : null;

        ModelMorphSkeleton targetSkeleton = target.model instanceof mchorse.bbs_mod.cubic.data.model.Model model
            ? ModelMorphAdapter.skeleton(model) : null;

        if (sourceSkeleton == null || targetSkeleton == null)
        {
            return null;
        }

        ModelMorphValidation.Result validation = ModelMorphValidation.validate(
            data, sourceSkeleton, targetSkeleton, sourceId, targetId, nextModelId);

        if (!validation.playable())
        {
            /* 回退到原版硬切换是安全行为，但必须让用户能看懂原因。 */
            BBSPlusPlusMod.LOGGER.warn("[Model Morph] {} -> {} falls back to the vanilla model switch: {}",
                sourceId, targetId, validation.fallbackReason());

            return null;
        }

        MorphPlan plan = MorphPlan.build(source, target, data, validation);

        if (plan != null && !plan.warnings().isEmpty())
        {
            BBSPlusPlusMod.LOGGER.warn("[Model Morph] {} -> {}: {}", sourceId, targetId, String.join("; ", plan.warnings()));
        }

        return plan;
    }

    private static int countSlots(MorphPlan plan)
    {
        int count = 0;

        for (java.util.List<MorphPlan.Slot> slots : plan.slots().values())
        {
            count += slots.size();
        }

        return count;
    }

    private static void trim()
    {
        while (CACHE.size() > MAX_ENTRIES)
        {
            CACHE.remove(CACHE.keySet().iterator().next());
        }

        while (FAILED.size() > MAX_ENTRIES)
        {
            FAILED.remove(FAILED.keySet().iterator().next());
        }
    }

    public static void invalidateAll()
    {
        CACHE.clear();
        FAILED.clear();
        MorphTextures.invalidateAll();
    }

    private record Key(String sourceId, String targetId, int dataHash)
    {
    }
}
