package gbeic.bbsplusplus.client.grade;

import gbeic.bbsplusplus.BBSPlusPlusMod;

/**
 * 逐骨骼提交的计数器 —— 施工表 B2-08 的对拍工具。
 *
 * <p>B2-01 把「一次整模型提交」拆成「每个组一次提交」，draw call 因此随骨骼数线性增长。
 * 施工表要求「退化 ≤10%，超限有降级」，所以在 {@link ModelGroupSubmit121#canSplit} 的
 * 组数上限之外，还需要一个能<b>实测</b>的手段：默认不输出任何东西，只有启动时加了
 * {@code -Dbbspp.debug_per_bone_stats} 才会每秒打一行统计，用来对比开关前后的 draw call
 * 与模型数。</p>
 *
 * <p>用它做对拍的方式：同一个场景（建议一个 >100 骨骼的模型 + 若干普通模型）分别以
 * <ul>
 *   <li>默认（拆分开）</li>
 *   <li>{@code -Dbbspp.disable_per_bone_split}（整体关闭）</li>
 * </ul>
 * 启动，各停留 30 秒，比较日志里 {@code models/s} 与 {@code groups/s}，
 * 再用 F3 的帧时间对比。统计本身不参与渲染决策，关掉开关时它也不会计数。</p>
 */
public final class ModelGroupStats121
{
    private static final String DEBUG_PROPERTY = "bbspp.debug_per_bone_stats";
    private static final long WINDOW_NANOS = 1_000_000_000L;

    private static boolean enabled;
    private static boolean checked;

    private static long windowStart;
    private static long models;
    private static long groups;
    private static long splitModels;
    private static long fallbackModels;
    private static long peakGroups;

    private ModelGroupStats121()
    {
    }

    private static boolean enabled()
    {
        if (!checked)
        {
            checked = true;
            enabled = System.getProperty(DEBUG_PROPERTY) != null;
        }

        return enabled;
    }

    /** 记一次拆分成功的模型。 */
    public static void recordSplit(int groupCount)
    {
        if (!enabled())
        {
            return;
        }

        splitModels ++;
        groups += groupCount;

        if (groupCount > peakGroups)
        {
            peakGroups = groupCount;
        }

        tick();
    }

    /** 记一次回退到整模型提交的模型（含组数超限、拾取、预览、已 broken）。 */
    public static void recordFallback()
    {
        if (!enabled())
        {
            return;
        }

        fallbackModels ++;

        tick();
    }

    private static void tick()
    {
        long now = System.nanoTime();

        if (windowStart == 0L)
        {
            windowStart = now;

            return;
        }

        if (now - windowStart < WINDOW_NANOS)
        {
            return;
        }

        models = splitModels + fallbackModels;

        BBSPlusPlusMod.LOGGER.info(
            "[BBS++] 逐骨骼提交统计：模型 {}（拆分 {} / 回退 {}），提交 {} 次，单模型最多 {} 组，开关={}",
            models, splitModels, fallbackModels, groups, peakGroups,
            ModelGroupSubmit121.enabled() ? "开" : "关");

        splitModels = 0L;
        fallbackModels = 0L;
        groups = 0L;
        peakGroups = 0L;
        models = 0L;
        windowStart = now;
    }
}
