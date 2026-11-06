package gbeic.bbsplusplus.client.grade;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.render.picker.BBSPickerRenderer;
import mchorse.bbs_mod.forms.FormRenderCapture;
import mchorse.bbs_mod.graphics.ModelPreviewRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/**
 * 面板内 3D 预览里的纹理分级。
 *
 * <p>为什么需要单独的钩子：预览走的是 {@code ModelInstance.render} 里的另一条分支
 * （{@code ModelPreviewRenderer.ACTIVE && TEXTURE != null} → {@code layer.draw(built)}），
 * 直接调 {@code RenderLayer.draw}，**不经过** {@code FormTranslucentQueue}，所以
 * {@code FormTranslucentQueueGradeMixin121} 那条路在预览里根本不会触发 —— 表现就是
 * 「世界里的模型变了色，面板预览里没变」。
 *
 * <p>拦截点是 {@code RenderLayer.draw(BuiltBuffer)}：只有当预览正在渲染
 * （{@link ModelPreviewRenderer#ACTIVE}）且当前 {@link GradeContext} 确实要分级时才接管，
 * 其它任何 {@code RenderLayer.draw} 都原样放行。接管时我们负责关掉 {@code BuiltBuffer}
 * （原本是 {@code RenderLayer.draw} 的职责），失败则不取消、把关闭职责还回去。
 *
 * <p><b>只开附魔光效（2026-11-04 修复）</b>：旧实现第一句就是
 * {@code if (!params.needBase()) return false;}，于是「没开调色/白化、只开了附魔光效」的模型在
 * 预览里一层光效都不画。现在改成两阶段：
 * <ul>
 *   <li>需要调色/白化 → 仍在 HEAD 接管，自己画基础层 + 光效层，然后 cancel 原绘制；</li>
 *   <li>只要光效，或基础管线不可用 → 不 cancel，让原 {@code RenderLayer.draw} 先画基础层，
 *       由 {@link #afterDraw()} 在 RETURN 时叠加光效层（ADDITIVE 必须在基础层之后）。</li>
 * </ul>
 *
 * <p><b>拾取通道守卫</b>：预览的 picking pass 与可见 pass 都处于
 * {@code ModelPreviewRenderer.ACTIVE} 之内，但前者把 {@link BBSPickerRenderer} 的渲染目标
 * 指向 stencil texture，用 ID 颜色编码骨骼。此时若让分级 shader 接管 {@code RenderLayer.draw}，
 * 会把普通纹理颜色写进 picking 目标，点击就读不到骨骼索引（表现为「点模型选不中肢体」）。
 * 所以只要 picker 的离屏目标处于激活状态，本钩子一律放行。
 */
public final class PreviewGradeHook
{
    private static boolean failed;
    private static boolean logged;
    private static boolean loggedGlint;

    /** 仅光效通道时，HEAD 暂存、{@code RenderLayer.draw} RETURN 时消费的快照。 */
    private static final ThreadLocal<PendingGlint> PENDING = new ThreadLocal<>();

    private record PendingGlint(FormRenderCapture.Captured captured, Matrix4f modelView,
                                Identifier textureId, GradeParams params)
    {
    }

    private PreviewGradeHook()
    {
    }

    /** 首次接管预览时打一条，实机确认预览钩子命中（mixin `require = 0`，静默失败看不出来）。 */
    private static void logOnce()
    {
        if (logged)
        {
            return;
        }

        logged = true;
        gbeic.bbsplusplus.BBSPlusPlusMod.LOGGER.info("[BBS++] 纹理分级已接管面板预览绘制");
    }

    private static void logGlintOnce()
    {
        if (loggedGlint)
        {
            return;
        }

        loggedGlint = true;
        gbeic.bbsplusplus.BBSPlusPlusMod.LOGGER.info("[BBS++] 附魔光效已在面板预览叠加");
    }

    /** @return true 表示已经自己画完，调用方应 cancel 原来的 {@code RenderLayer.draw}。 */
    public static boolean handle(BuiltBuffer built)
    {
        Boolean decided = built == null ? null : GradeDecisions.get(built);

        if (decided != null)
        {
            /* 幂等命中：保留本次已经暂存的光效快照。 */
            return decided;
        }

        /* 新的绘制从干净状态开始，避免 RETURN 注入未命中时把旧光效错画到别的模型上。 */
        PENDING.remove();

        if (built == null || failed || !ModelPreviewRenderer.ACTIVE)
        {
            return false;
        }

        /* picking pass：ID 颜色必须原样落到 stencil target，不能换成分级 shader。 */
        if (BBSPickerRenderer.getRenderTargetColor() != null)
        {
            return false;
        }

        GradeParams params = GradeContext.current();

        if (params == null || (!params.needBase() && !params.needGlint()))
        {
            return false;
        }

        /* 与 GradeSubmitHook 同一条约束：只接管与 GradePipeline 顶点格式一致的缓冲区。
         * 预览里也可能混进简化格式的绘制（billboard/trail 预览），那些交给原 layer。 */
        if (built.getDrawParameters().format() != VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL)
        {
            return false;
        }

        boolean base = params.needBase() && GradePass.readyBase();
        boolean glint = params.needGlint() && GradePass.readyGlint();

        if (!base && !glint)
        {
            return false;
        }

        Identifier textureId = GradePass.adopt(BBSModClient.getTextures().getLastBound());

        if (textureId == null)
        {
            return false;
        }

        FormRenderCapture.Captured captured;

        try
        {
            captured = FormRenderCapture.copy(built);
        }
        catch (Throwable t)
        {
            failed = true;
            gbeic.bbsplusplus.BBSPlusPlusMod.LOGGER.error("[BBS++] 预览分级：顶点快照失败，后续预览回退到原绘制", t);

            return false;
        }

        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());

        if (base)
        {
            boolean drawn;

            try
            {
                /* 预览里的 layer 是按 isCulling() 选的，但钩子里读不到那个值；不剔除只会多画背面，
                 * 对封闭模型看不见，比误剔除（模型缺面）安全。 */
                drawn = GradePass.drawBase(captured, modelView, textureId, params, false);
            }
            catch (Throwable t)
            {
                failed = true;
                gbeic.bbsplusplus.BBSPlusPlusMod.LOGGER.error("[BBS++] 预览分级绘制失败，后续预览回退到原绘制", t);

                return false;
            }

            if (drawn)
            {
                if (glint)
                {
                    try
                    {
                        GradePass.drawGlint(captured, modelView, textureId, params);
                        logGlintOnce();
                    }
                    catch (Throwable t)
                    {
                        gbeic.bbsplusplus.BBSPlusPlusMod.LOGGER.error("[BBS++] 预览附魔光效绘制失败", t);
                    }
                }

                logOnce();
                built.close();
                GradeDecisions.put(built, true);

                return true;
            }
        }

        if (!glint)
        {
            return false;
        }

        /* 仅光效（或基础管线不可用）：原 layer 照常画，光效推迟到 RETURN 之后叠加。 */
        PENDING.set(new PendingGlint(captured, modelView, textureId, params));
        GradeDecisions.put(built, false);

        return false;
    }

    /**
     * 原 {@code RenderLayer.draw} 返回之后叠加附魔光效层。
     *
     * <p>由 {@code RenderLayerPreviewGradeMixin121} 的 RETURN 注入调用。缓冲区此时可能已被
     * 原绘制关闭，所以用的是 HEAD 时复制的顶点快照，不碰 {@code BuiltBuffer}。</p>
     */
    public static void afterDraw()
    {
        PendingGlint pending = PENDING.get();

        if (pending == null)
        {
            return;
        }

        PENDING.remove();

        try
        {
            if (GradePass.drawGlint(pending.captured(), pending.modelView(), pending.textureId(), pending.params()))
            {
                logGlintOnce();
            }
        }
        catch (Throwable t)
        {
            failed = true;
            gbeic.bbsplusplus.BBSPlusPlusMod.LOGGER.error("[BBS++] 预览附魔光效绘制失败，后续预览回退到原绘制", t);
        }
    }
}
