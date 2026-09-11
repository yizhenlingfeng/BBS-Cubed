package gbeic.bbsplusplus.performance;

import gbeic.bbsplusplus.BBSPlusPlusMod;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.CubicVAOBuilderRenderer;
import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * P1：把 {@link ModelInstance#setup()} 原本在主线程一次性完成的 VAO 烘焙拆分到多帧。
 *
 * <p>原版流程：模型后台解析完成后，{@code MinecraftClient.execute(() -> ...)} 把
 * "遍历全部 Group → 构建顶点数组 → 计算切线 → new ModelVAO() 上传 GPU" 整个过程
 * 排到主线程一次性执行。大模型（数百个 cube）会在这一帧卡 1~数秒。</p>
 *
 * <p>本类把待烘焙的 Group 列表放进队列，每个客户端 tick 只处理有限个 Group，
 * 其余 Group 在它们被烘焙之前走原版的 CPU 立即渲染路径（{@code CubicCubeRenderer}），
 * 因此模型会"渐进出现"而不是等全部烘焙完才显示。</p>
 *
 * <p>注意：{@link CubicVAOBuilderRenderer#applyGroupTransformations} 在 BBS 中是空实现，
 * 即烘焙时不需要沿继承链累积 Group 矩阵——每个 Group 独立用一个空 MatrixStack 烘焙即可。</p>
 */
public final class ProgressiveVAOBaker
{
    /** 每个 tick 最多烘焙多少个 Group。经验值：2~4 个足以让大模型在数十帧内完成。 */
    private static final int GROUPS_PER_TICK = 2;

    /** 待烘焙任务队列。多个模型同时加载时排队处理。 */
    private static final Deque<BakeTask> queue = new ArrayDeque<>();

    private ProgressiveVAOBaker() {}

    /**
     * 提交一个分帧烘焙任务。
     *
     * @param instance  正在 setup 的 ModelInstance（其 vaos map 是烘焙结果的写入目标）
     * @param model     模型数据
     * @param bigEnough 是否"足够大、值得分帧"。小模型仍走原版一次性路径，避免调度开销。
     */
    public static void submit(ModelInstance instance, Model model, boolean bigEnough)
    {
        List<ModelGroup> groups = new ArrayList<>();
        collectVisible(model.topGroups, groups);

        if (groups.isEmpty())
        {
            return;
        }

        // 小模型直接同步烘焙，不排队（避免一帧只烘 2 个导致小模型也分很多帧）
        if (!bigEnough || groups.size() <= 4)
        {
            bakeGroups(instance, model, groups);
            return;
        }

        queue.addLast(new BakeTask(instance, model, groups));
    }

    /** 每个客户端 tick 调用一次：从队列里取有限个 Group 烘焙。 */
    public static void tick()
    {
        int budget = GROUPS_PER_TICK;

        while (budget > 0 && !queue.isEmpty())
        {
            BakeTask task = queue.peekFirst();

            // instance 已被 delete（世界卸载 / 模型替换）则丢弃
            if (task.instance.getVaos() == null)
            {
                queue.pollFirst();
                continue;
            }

            int done = task.processNext(budget);
            budget -= done;

            if (task.isDone())
            {
                queue.pollFirst();
            }
        }
    }

    private static void collectVisible(List<ModelGroup> groups, List<ModelGroup> out)
    {
        for (ModelGroup group : groups)
        {
            if (group.visible)
            {
                out.add(group);
            }
            if (!group.children.isEmpty())
            {
                collectVisible(group.children, out);
            }
        }
    }

    private static void bakeGroups(ModelInstance instance, Model model, List<ModelGroup> groups)
    {
        CubicVAOBuilderRenderer renderer = new CubicVAOBuilderRenderer(instance.getVaos());
        for (ModelGroup group : groups)
        {
            try
            {
                renderer.renderGroup(null, new MatrixStack(), group, model);
            }
            catch (Throwable t)
            {
                BBSPlusPlusMod.LOGGER.warn("[性能] 分帧烘焙 group {} 失败", group.id, t);
            }
        }
    }

    /** 单个模型的烘焙任务：持有剩余未烘焙的 Group。 */
    private static final class BakeTask
    {
        final ModelInstance instance;
        final Model model;
        final List<ModelGroup> remaining;

        BakeTask(ModelInstance instance, Model model, List<ModelGroup> remaining)
        {
            this.instance = instance;
            this.model = model;
            this.remaining = remaining;
        }

        int processNext(int budget)
        {
            int processed = 0;
            CubicVAOBuilderRenderer renderer = new CubicVAOBuilderRenderer(instance.getVaos());

            while (processed < budget && !remaining.isEmpty())
            {
                ModelGroup group = remaining.remove(0);
                try
                {
                    renderer.renderGroup(null, new MatrixStack(), group, model);
                }
                catch (Throwable t)
                {
                    BBSPlusPlusMod.LOGGER.warn("[性能] 分帧烘焙 group {} 失败", group.id, t);
                }
                processed++;
            }

            return processed;
        }

        boolean isDone()
        {
            return remaining.isEmpty();
        }
    }
}
