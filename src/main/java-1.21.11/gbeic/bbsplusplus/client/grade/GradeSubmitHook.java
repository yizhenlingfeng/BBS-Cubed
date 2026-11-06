package gbeic.bbsplusplus.client.grade;

import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.FormRenderCapture;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.renderers.utils.FormOverlay;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;


/**
 * {@code FormTranslucentQueue.submit} 的注入体：调色/白化接管基础绘制，附魔光效追加一层。
 *
 * <p>为什么放在这里：1.21.11 里整个模型的顶点只在这一个点交给
 * {@link FormTranslucentQueue} 排队重放，这里能同时拿到顶点快照、模型纹理、以及在
 * {@code ModelInstance.render} 里压好的 {@link GradeContext} 参数。
 *
 * <p>描述符怎么写：开发环境里方法签名的 MC 类型是 yarn 名（{@code BuiltBuffer}），
 * 产物里是 intermediary（{@code net/minecraft/class_9801}）。实测 Loom 的 remapJar
 * **会把 {@code @Inject(method=…)} 字符串里的 MC 类型一起重映射**，所以 mixin 里只写
 * yarn 描述符即可，产物自动变成 {@code submit(Lnet/minecraft/class_9801;…)V} —— 不要
 * 再手写一份混淆描述符的孪生 mixin，否则产物里会两份同时生效、同一帧画两遍。
 *
 * <p><b>只开附魔光效时的顺序陷阱（2026-11-04 修复）</b>：本方法在 {@code submit} 的 HEAD 执行。
 * 若只有光效、没有调色/白化，原绘制不能被 cancel —— 而上游 {@code submit} 在普通路径里是
 * **立即** {@code layer.draw(built)}，队列未开启时 {@link FormTranslucentQueue#add} 也是立即
 * {@code draw()}。在 HEAD 里就 add 光效命令，会让 ADDITIVE 光效先画、随后被不透明的基础层整片
 * 覆盖，视觉上等于「光效没生效」。所以「仅光效」分支改为在 HEAD 暂存快照，由
 * {@link #afterSubmit()} 在原 {@code submit} 返回之后再投递光效命令：队列开启时它排在基础
 * 命令之后（稳定排序），队列未开启时它立即画在已经画好的基础层之上，两种情况都正确。
 *
 * <p>调色那条还带上逐骨骼调色板（{@link FormOverlay#snapshot()}），否则帧末重放时 swatch
 * 已经被别的模型改写了。
 */
public final class GradeSubmitHook
{
    private static boolean logged;
    private static boolean loggedGlintOnly;

    /** 仅光效通道时，HEAD 暂存、{@code submit} RETURN 时消费的快照。 */
    private static final ThreadLocal<PendingGlint> PENDING = new ThreadLocal<>();

    private record PendingGlint(FormRenderCapture.Captured captured, Matrix4f modelView,
                                Identifier textureId, GradeParams params, Vector3f origin, int[] overlay)
    {
    }

    private GradeSubmitHook()
    {
    }

    /** 首次接管时打一条，方便实机确认注入真的命中了（mixin 都是 require = 0，静默失败看不出来）。 */
    private static void logOnce()
    {
        if (logged)
        {
            return;
        }

        logged = true;
        gbeic.bbsplusplus.BBSPlusPlusMod.LOGGER.info("[BBS++] 纹理分级已接管模型绘制（调色/白化/附魔光效生效）");
    }

    /** 首次走「仅光效」通道时打一条，实机可据此区分「参数没解析到」与「绘制没执行」。 */
    private static void logGlintOnce()
    {
        if (loggedGlintOnly)
        {
            return;
        }

        loggedGlintOnly = true;
        gbeic.bbsplusplus.BBSPlusPlusMod.LOGGER.info("[BBS++] 附魔光效已提交（仅光效通道，基础绘制保持原样）");
    }

    /**
     * @return {@code true} 表示「原绘制已被接替，调用方应 cancel 掉 submit」。
     */
    public static boolean handle(BuiltBuffer built, BBSShaders.ModelVariant variant, Texture texture, float alpha, StencilMap stencilMap, Vector3f origin, boolean tinted)
    {
        Boolean decided = GradeDecisions.get(built);

        if (decided != null)
        {
            /* 幂等命中（同一个 BuiltBuffer 重复进入）：保留本次已经暂存的光效快照。 */
            return decided;
        }

        /* 新的提交从干净状态开始：万一 RETURN 注入没匹配上（require = 0 静默），
         * 上一次的快照也不该被下一次绘制误用。 */
        PENDING.remove();

        GradeParams params = GradeContext.current();

        if (!params.active() || built == null || stencilMap != null || variant == null)
        {
            return false;
        }

        /* 顶点格式必须与 GradePipeline 声明的一致。
         * 同一个 submit 还被 BillboardFormRenderer / TrailFormRenderer 用来提交简化格式的
         * 三角形（POSITION_TEXTURE_COLOR、POSITION_TEXTURE_LIGHT_COLOR），那些缓冲区没有
         * overlay/light/normal 槽位；硬接管等于把 32 字节的顶点按 56 字节的布局去读，
         * 结果是跑飞的颜色/法线（严重时 GL_INVALID_OPERATION）。这类绘制请继续走原 layer。 */
        if (built.getDrawParameters().format() != VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL)
        {
            return false;
        }

        /* 只在单 pass 上接管：分割 pass（PASS_TRANSLUCENT/PASS_OPAQUE）里那套半透分割
         * 逻辑还没移植，硬接管会让模型出现硬边。 */
        if (variant.pass() != FormTranslucentQueue.PASS_SINGLE)
        {
            return false;
        }

        Identifier textureId = GradePass.adopt(texture);

        if (textureId == null)
        {
            return false;
        }

        FormRenderCapture.Captured captured = FormRenderCapture.copy(built);

        if (captured == null || captured.params().vertexCount() <= 0)
        {
            GradeDecisions.put(built, false);

            return false;
        }

        Matrix4f modelView = new Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix());
        int[] overlay = FormOverlay.hasPalette() ? FormOverlay.snapshot() : null;
        Vector3f glintOrigin = origin == null ? new Vector3f() : new Vector3f(origin);

        /* 管线预检：建不起来就一个字都别改，让原绘制照常发生（否则模型会整块消失）。 */
        boolean base = params.needBase() && GradePass.readyBase();
        boolean glint = params.needGlint() && GradePass.readyGlint();

        if (!base && !glint)
        {
            GradeDecisions.put(built, false);

            return false;
        }

        PendingGlint deferred = new PendingGlint(captured, modelView, textureId, params, glintOrigin, overlay);

        if (base)
        {
            /* 接管基础绘制时，两条命令都在这里入队：先基础（写深度），再光效（叠加、不写深度）。
             * 队列未开启时 add() 会立即 draw，顺序同样成立。 */
            FormTranslucentQueue.add(new GradeDrawCommand(glintOrigin, captured, modelView, textureId, params, variant.cull(), overlay));
            logOnce();

            if (glint)
            {
                FormTranslucentQueue.add(new GlintDrawCommand(glintOrigin, captured, modelView, textureId, params, overlay));
            }

            /* 原绘制由我们接替（调用方会 cancel），所以 built 只能由这里关掉：
             * 上游那条路径是在 layer.draw 里关的，我们不走它了。 */
            built.close();
            GradeDecisions.put(built, true);

            return true;
        }

        /* 只要光效：基础绘制照旧走 BBS 的 layer，不 cancel；光效推迟到 submit RETURN 之后叠加。 */
        PENDING.set(deferred);
        logGlintOnce();
        GradeDecisions.put(built, false);

        return false;
    }

    /**
     * 原 {@code submit} 返回之后追加附魔光效层。
     *
     * <p>由 {@code FormTranslucentQueueGradeMixin121} 的 RETURN 注入调用。这里不依赖
     * {@link GradeDecisions}：HEAD 是否 cancel 都安全 —— 接管基础绘制的分支不会写 PENDING，
     * 而仅光效的分支不会 cancel。</p>
     */
    public static void afterSubmit()
    {
        PendingGlint pending = PENDING.get();

        if (pending == null)
        {
            return;
        }

        PENDING.remove();

        try
        {
            FormTranslucentQueue.add(new GlintDrawCommand(pending.origin(), pending.captured(),
                pending.modelView(), pending.textureId(), pending.params(), pending.overlay()));
        }
        catch (Throwable t)
        {
            gbeic.bbsplusplus.BBSPlusPlusMod.LOGGER.error("[BBS++] 附魔光效提交失败（本次绘制跳过光效）", t);
        }
    }
}
