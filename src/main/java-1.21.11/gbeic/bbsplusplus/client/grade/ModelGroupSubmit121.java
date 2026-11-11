package gbeic.bbsplusplus.client.grade;

import gbeic.bbsplusplus.BBSPlusPlusMod;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.CubicCubeRenderer;
import mchorse.bbs_mod.cubic.render.ICubicRenderer;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 逐骨骼（逐组）提交的骨架 —— 施工表 B2-01。
 *
 * <h2>为什么需要它</h2>
 * 1.20.x 里 {@code CubicVAORendererMixin} 能在「一个 draw 内部」按 {@link ModelGroup} 切换
 * 贴图 / PBR / 光效，因为当时是一次模型 = 一次 raw-GL 绘制，切换只是改 shader uniform 与绑定。
 * 1.21.11 把模型变成「一次 {@code BuiltBuffer} → 一次 {@code FormTranslucentQueue.submit}」，
 * 贴图、深度写、剔除、分级参数全部固化在那一次提交的 {@code RenderLayer} 上，
 * 所以逐骨骼特性必须先把「一次提交」拆成「每根骨骼一次提交」。
 *
 * <h2>拆分粒度必须是「每个组」，不能是「每个顶层组」</h2>
 * 2026-11-12 的实机反馈「肢体 PBR 无反应」就是粒度错了：绝大多数 Blockbench 模型只有
 * <b>一个</b>顶层组（{@code root}），其余骨骼都是它的子组。按顶层组拆时
 * {@code canSplit} 直接返回 false（组数 == 1），逐组贴图 / PBR / 光效一次都不会执行；
 * 即使有多个顶层组，子骨骼的 PBR 也读不到 —— 因为一个 {@code BuiltBuffer} 只能有一张贴图。
 * 现在与 1.20.1 的 {@code processRenderRecursively} 一致：<b>每个可见组各造一个缓冲</b>。
 *
 * <h2>为什么是两阶段</h2>
 * 颜色叠加（{@code FormOverlay}）走的是「调色板」：遍历期间每个骨骼的叠加色各占调色板一个
 * 纹素，顶点用 UV2 指过去，整张调色板在<b>遍历结束后</b>才上传一次。所以不能边遍历边提交 ——
 * 那样先提交的组会采样到还没填完的调色板。这里先把每组的顶点缓冲全部造好，再交给调用方
 * （由它上传调色板、然后逐组提交）。
 *
 * <h2>开关与回退</h2>
 * <ul>
 *   <li>系统属性 {@code bbspp.disable_per_bone_split} 存在时整体关闭（kill-switch 约定）。</li>
 *   <li>组数超过 {@link #MAX_GROUPS} 时回退整模型提交，避免 draw call 爆炸。</li>
 *   <li>拾取通道（{@code stencilMap != null}）与面板预览（{@code ModelPreviewRenderer.ACTIVE}）
 *       一律回退：这两条路径的绘制在调用方 {@code ModelInstance#render} 里、位于本次提交之后，
 *       在骨架里提前提交会绕过它们。</li>
 *   <li>拆分过程中抛任何异常都永久回退整模型提交（{@link #broken}），只记一次日志。</li>
 * </ul>
 */
public final class ModelGroupSubmit121
{
    /** 组数（含子组）超过这个数量就不再拆，直接整模型提交（B2-08 的降级线）。 */
    public static final int MAX_GROUPS = 96;

    private static final String KILL_SWITCH = "bbspp.disable_per_bone_split";

    private static boolean broken;
    private static boolean loggedFailure;
    private static boolean loggedTooMany;

    private ModelGroupSubmit121()
    {
    }

    /** 一个组造好的顶点缓冲，连同它的组。 */
    public record GroupBuffer(BuiltBuffer built, ModelGroup group)
    {
    }

    /**
     * 关闭开关是否生效。属性存在即关闭，与项目其余 kill-switch 的约定一致。
     */
    public static boolean enabled()
    {
        return !broken && System.getProperty(KILL_SWITCH) == null;
    }

    /**
     * 是否允许把这次模型绘制拆成逐组提交。
     *
     * @param model          正在绘制的立方体模型
     * @param picking        {@code stencilMap != null}（拾取通道）
     * @param previewVisible 面板预览正在接管这次绘制
     */
    public static boolean canSplit(Model model, boolean picking, boolean previewVisible)
    {
        if (model == null || picking || previewVisible || !enabled())
        {
            return false;
        }

        int groups = countGroups(model.topGroups, MAX_GROUPS);

        if (groups > MAX_GROUPS && !loggedTooMany)
        {
            loggedTooMany = true;

            BBSPlusPlusMod.LOGGER.warn(
                "[BBS++] 逐骨骼提交：模型组数超过上限 {}（本次统计已截断），回退整模型提交 —— 逐骨骼贴图/PBR/光效在该模型上不会生效",
                MAX_GROUPS);
        }

        return groups > 1 && groups <= MAX_GROUPS;
    }

    /** 组数（含子组）统计；一旦超过 {@code limit} 就直接返回，用来做 O(组数) 的廉价判定。 */
    private static int countGroups(List<ModelGroup> groups, int limit)
    {
        if (groups == null)
        {
            return 0;
        }

        int total = 0;

        for (ModelGroup group : groups)
        {
            total += 1 + countGroups(group.children, limit);

            if (total > limit)
            {
                return total;
            }
        }

        return total;
    }

    /**
     * 把一次模型遍历拆成「每个组一次 {@code BuiltBuffer}」。
     *
     * <p>调用方传进来的 {@code builder}（上游那条整模型提交用的）会被刻意留空：
     * 留空后上游的 {@code endNullable()} 返回 {@code null}，它自己那条提交自然跳过。</p>
     *
     * <p>{@code sink} 在<b>全部组的缓冲都造好之后</b>才被调用一次，调用方可以在这时上传
     * 颜色调色板、再逐组提交（见类注释「为什么是两阶段」）。</p>
     *
     * @return 是否真的拆了。返回 {@code false} 表示调用方必须自己走整模型路径。
     */
    public static boolean split(ICubicRenderer renderProcessor, Model model, MatrixStack stack, Consumer<List<GroupBuffer>> sink)
    {
        List<GroupBuffer> buffers = new ArrayList<>();

        try
        {
            walk(renderProcessor, model, stack, model.topGroups, buffers);

            sink.accept(buffers);

            ModelGroupStats121.recordSplit(buffers.size());

            return true;
        }
        catch (Throwable e)
        {
            /* 拆分出任何问题都必须让整帧继续。已经造好的缓冲在这里释放，不会提交；
             * 从下一帧起 broken 让 canSplit 恒为 false，永久退回整模型提交。 */
            for (GroupBuffer buffer : buffers)
            {
                try
                {
                    buffer.built().close();
                }
                catch (Throwable ignored)
                {
                }
            }

            if (!loggedFailure)
            {
                loggedFailure = true;

                BBSPlusPlusMod.LOGGER.warn("[BBS++] 逐骨骼提交失败，已永久回退整模型提交（-Dbbspp.disable_per_bone_split 可显式关闭该路径）", e);
            }

            broken = true;

            return false;
        }
    }

    /**
     * 与 {@code CubicRenderer.processRenderRecursively} 同构的遍历，但把每个组的几何写进
     * <b>自己的</b>缓冲。
     *
     * <p>上游那个方法是 private，没法复用；这里只用 {@link ICubicRenderer} 的公开契约
     * （{@code applyGroupTransformations} + {@code renderGroup}），所以两者的骨骼变换、
     * 可见性判断与停止语义必须保持逐行一致 —— 改动这里时请对照上游同步。</p>
     *
     * @return 是否遇到「整棵子树已处理完」的提前终止
     */
    private static boolean walk(ICubicRenderer renderProcessor, Model model, MatrixStack stack, List<ModelGroup> groups, List<GroupBuffer> out)
    {
        if (groups == null)
        {
            return false;
        }

        for (ModelGroup group : groups)
        {
            stack.push();
            renderProcessor.applyGroupTransformations(stack, group);

            if (group.isVisible())
            {
                BufferBuilder builder = Tessellator.getInstance().begin(
                    VertexFormat.DrawMode.QUADS,
                    VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);

                boolean stopped = renderProcessor.renderGroup(builder, stack, group, model);

                /* 焊接补丁在遍历期间被扣着，必须每组排空一次，否则弯折处的补丁会串到下一组。 */
                if (renderProcessor instanceof CubicCubeRenderer cube)
                {
                    cube.finish(builder);
                }

                BuiltBuffer built = builder.endNullable();

                /* 没有几何的组（纯枢轴/纯变换）通常在这里就是 null，不会进 out。 */
                if (built != null)
                {
                    out.add(new GroupBuffer(built, group));
                }

                if (stopped)
                {
                    stack.pop();

                    return true;
                }
            }

            if (walk(renderProcessor, model, stack, group.children, out))
            {
                stack.pop();

                return true;
            }

            stack.pop();
        }

        return false;
    }
}
