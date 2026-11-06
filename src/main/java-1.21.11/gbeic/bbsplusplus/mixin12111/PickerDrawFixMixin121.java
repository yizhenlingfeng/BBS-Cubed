package gbeic.bbsplusplus.mixin12111;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.function.Supplier;
import java.util.OptionalInt;
import java.util.OptionalDouble;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import gbeic.bbsplusplus.BBSPlusPlusMod;
import mchorse.bbs_mod.client.render.picker.BBSPickerRenderer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.texture.GlTexture;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ByteOrder;

/**
 * 1.21.11 picker 绘制端的修复与诊断。
 *
 * <p>2026-11-05 的关键发现（对 {@code bbs-2.7-1.21.11.jar} 反编译 + 实机日志比对）：
 * <ul>
 *   <li>整张 stencil 纹理里被扫到的 ID 像素（{@code 示例ID=17}）其实是 <b>gizmo 手柄</b>，
 *       不是模型。{@code StencilMap.setup()} 从 20 开始分配骨骼 ID，0..19 留给 gizmo 手柄；</li>
 *   <li>gizmo 走 {@code BBSPickerRenderer.drawColorId}（<b>只有颜色附件</b>的 pass）→ 能画出来；</li>
 *   <li>模型走 {@code BBSPickerRenderer.draw}（颜色 + 深度附件）→ 一个像素都没有。</li>
 * </ul>
 *
 * <p>两条路径的差别只剩两点：深度附件、以及 {@code picker_models.fsh} 的第一句
 * {@code color = texture(Sampler0, texCoord0) * vertexColor; if (color.a < 0.1) discard;}。
 * 后者只要 Sampler0 指向的纹理"采样出来 alpha=0"（mipmap 不完整、adopted 纹理绑定错、
 * 采样器带了 mipmap 过滤等），整模型就会被全 discard —— 与「gizmo 有、模型全无」完全吻合。
 *
 * <p>所以这里做两件事：
 * <ol>
 *   <li><b>把拾取用的 Sampler0 换成 1×1 不透明白纹理 + NEAREST 采样器</b>：拾取只需要
 *       「几何轮廓 → 骨骼 ID」，不需要 albedo。这样既绕开纹理 alpha 相关的一切坑，
 *       也让结果只取决于几何（对骨块拾取反而更稳）。</li>
 *   <li>限流打印第一个顶点的 position / color / UV2.x（UV2.x 就是骨骼索引），
 *       用来区分「几何没上传对」和「片元没跑」。</li>
 * </ol>
 */
@Mixin(value = BBSPickerRenderer.class, remap = false)
public abstract class PickerDrawFixMixin121
{
    @Shadow
    private static GpuTextureView sampler0View;

    @Shadow
    private static GpuSampler sampler0;

    @Shadow
    private static GpuTextureView targetColor;

    @Shadow
    private static GpuTextureView targetDepth;

    @Unique
    private static boolean bbspp$colorOnlyLogged;

    @Unique
    private static int bbspp$calls;

    /** 正常走到 RETURN 的次数；与 calls 不等说明 draw 中途抛异常被 FormUtilsClient 吞掉了。 */
    @Unique
    private static int bbspp$completed;

    @Unique
    private static long bbspp$lastLogMs;

    @Unique
    private static boolean bbspp$loggedSampler;

    @Unique
    private static boolean bbspp$loggedVertex;

    @Unique
    private static GpuTexture bbspp$whiteTex;

    @Unique
    private static GpuTextureView bbspp$whiteView;

    @Unique
    private static GpuSampler bbspp$nearest;

    /** picker draw 期间临时清掉引擎输出覆盖，防止绘制被改道到预览 FBO。 */
    @Unique
    private static final ThreadLocal<GpuTextureView> bbspp$savedColorOverride = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<GpuTextureView> bbspp$savedDepthOverride = new ThreadLocal<>();

    /** 1×1 纯白不透明纹理，只在第一次拾取时创建一次。 */
    @Unique
    private static boolean bbspp$ensureOpaqueSampler()
    {
        if (bbspp$whiteView != null && bbspp$nearest != null)
        {
            return true;
        }

        try
        {
            GpuDevice device = RenderSystem.getDevice();

            /* usage：COPY_DST(1) | TEXTURE_BINDING(4) | RENDER_ATTACHMENT(8)
             * —— clearColorTexture 走 validateColorAttachment，必须有 RENDER_ATTACHMENT。 */
            GpuTexture tex = device.createTexture("bbspp_picker_opaque", 13, TextureFormat.RGBA8, 1, 1, 1, 1);

            device.createCommandEncoder().clearColorTexture(tex, 0xFFFFFFFF);

            bbspp$whiteTex = tex;
            bbspp$whiteView = device.createTextureView(tex);
            bbspp$nearest = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);

            return true;
        }
        catch (Throwable t)
        {
            BBSPlusPlusMod.LOGGER.warn("[BBS++] 拾取修复：不透明拾取纹理创建失败（{}）", t.getClass().getSimpleName());

            return false;
        }
    }

    /**
     * 把模型拾取用的 pass 从「颜色 + 深度」降级成「只有颜色」。
     *
     * <p>对照组：gizmo 走 {@code drawColorId}（只有颜色附件）→ 一直能正常写进 stencil；
     * 模型走 {@code draw}（多挂一个 DEPTH32 深度附件）→ 一个片元都没有。深度本身已经被
     * 正确清成 1.0（深度线实测 min=1.0 max=1.0），所以这里是在验证「深度附件本身」
     * 是不是让这趟绘制失效的原因（例如 FBO 组合在这台机器/驱动上不可用）。
     */
    @Redirect(
        method = "draw(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/render/BuiltBuffer;Lorg/joml/Matrix4f;)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalInt;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPass;"
        ),
        require = 0
    )
    private static RenderPass bbspp$colorOnlyPass(CommandEncoder encoder, Supplier<String> name, GpuTextureView color, OptionalInt clearColor, GpuTextureView depth, OptionalDouble clearDepth)
    {
        if (bbspp$colorOnlyLogged)
        {
            return encoder.createRenderPass(name, color, clearColor);
        }

        bbspp$colorOnlyLogged = true;
        BBSPlusPlusMod.LOGGER.info("[BBS++] 拾取修复：模型拾取 pass 已去掉深度附件（对照组：gizmo 的 drawColorId 也是这么画的）");

        return encoder.createRenderPass(name, color, clearColor);
    }

    @Inject(
        method = "draw(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/render/BuiltBuffer;Lorg/joml/Matrix4f;)V",
        at = @At("HEAD"),
        require = 0
    )
    private static void bbspp$beforePickerDraw(RenderPipeline pipeline, BuiltBuffer buffer, Matrix4f modelView, CallbackInfo ci)
    {
        if (bbspp$ensureOpaqueSampler())
        {
            /* 拾取只看几何，不看 albedo —— 换成 1×1 白纹理后 fsh 的 alpha cutout 恒通过。 */
            BBSPickerRenderer.setSampler0(bbspp$whiteView, bbspp$nearest);

            if (!bbspp$loggedSampler)
            {
                bbspp$loggedSampler = true;
                BBSPlusPlusMod.LOGGER.info("[BBS++] 拾取修复：Sampler0 已换成不透明白纹理（拾取不再受 albedo alpha 影响）");
            }
        }

        bbspp$savedColorOverride.set(RenderSystem.outputColorTextureOverride);
        bbspp$savedDepthOverride.set(RenderSystem.outputDepthTextureOverride);
        RenderSystem.outputColorTextureOverride = null;
        RenderSystem.outputDepthTextureOverride = null;

        bbspp$calls++;

        /* 第二处 discard 来源：fsh 里 color = texture * vertexColor，顶点色 alpha 若为 0
         * 同样会被整片丢掉。拾取只关心几何，直接把缓冲区里每个顶点的 alpha 改成 255。 */
        int patched = bbspp$forceOpaqueAlpha(buffer);

        /* uploadImmediateVertexBuffer 是按 ByteBuffer.remaining() 上传的：若缓冲区停在末尾
         * （remaining=0），上传的就是 0 字节，绘制 2772 个索引时全部越界 → 一个片元都没有。
         * 这里只打印状态；真的为 0 才回卷，避免无谓改动。 */
        String upload = bbspp$ensureUploadable(buffer);

        if (!bbspp$loggedVertex && buffer != null && targetColor != null)
        {
            bbspp$loggedVertex = true;
            BBSPlusPlusMod.LOGGER.info("[BBS++] 拾取顶点抽样：{}（alpha 修补 {} 个）", bbspp$describeVertex(buffer), patched);
            BBSPlusPlusMod.LOGGER.info("[BBS++] 拾取裁剪体检：{}", bbspp$clipCheck(pipeline, buffer, modelView));
            BBSPlusPlusMod.LOGGER.info("[BBS++] 拾取上传状态：{}", upload);
        }

        long now = System.currentTimeMillis();

        if (now - bbspp$lastLogMs < 1000L)
        {
            return;
        }

        bbspp$lastLogMs = now;

        BBSPlusPlusMod.LOGGER.info(
            "[BBS++] picker 绘制 #{}（完整返回 {}）：色目标={}, 深目标={}, Sampler0在白纹理={}, Target={}, 索引数={}, 投影覆盖={}, 改道前覆盖={}",
            bbspp$calls, bbspp$completed,
            targetColor != null, targetDepth != null, sampler0View == bbspp$whiteView,
            BBSPickerRenderer.getTarget(),
            buffer == null ? -1 : buffer.getDrawParameters().indexCount(),
            BBSPickerRenderer.getProjectionOverride() != null,
            bbspp$savedColorOverride.get() != null);
    }

    @Inject(
        method = "draw(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/render/BuiltBuffer;Lorg/joml/Matrix4f;)V",
        at = @At("RETURN"),
        require = 0
    )
    private static void bbspp$afterPickerDraw(RenderPipeline pipeline, BuiltBuffer buffer, Matrix4f modelView, CallbackInfo ci)
    {
        bbspp$completed++;

        RenderSystem.outputColorTextureOverride = bbspp$savedColorOverride.get();
        RenderSystem.outputDepthTextureOverride = bbspp$savedDepthOverride.get();
        bbspp$savedColorOverride.remove();
        bbspp$savedDepthOverride.remove();
    }

    /**
     * 把拾取缓冲区里每个顶点的 color.a 改成 255。
     *
     * <p>{@code picker_models.fsh} 用 {@code color.a < 0.1} 做 alpha cutout，
     * 而 {@code color = texture(Sampler0) * vertexColor} —— 顶点色的 alpha 参与乘法。
     * 拾取只要几何轮廓，不需要顶点色透明度，所以这里统一置满。
     *
     * <p>顶点布局（{@code POSITION_COLOR_TEXTURE_LIGHT_NORMAL}）：
     * pos 3F(0..11) / color 4B(12..15) / uv0 / uv1 / uv2 / normal，alpha 在 +15。
     *
     * @return 被改写的顶点数（0 表示本来就是不透明，未做任何改动）
     */
    @Unique
    private static int bbspp$forceOpaqueAlpha(BuiltBuffer buffer)
    {
        if (buffer == null)
        {
            return 0;
        }

        try
        {
            ByteBuffer src = buffer.getBuffer();
            BuiltBuffer.DrawParameters params = buffer.getDrawParameters();

            if (src == null || params == null)
            {
                return 0;
            }

            int stride = params.format().getVertexSize();
            int count = params.vertexCount();

            if (stride < 16 || count <= 0 || src.capacity() < (long) stride * count)
            {
                return 0;
            }

            ByteBuffer bb = src.duplicate().order(ByteOrder.nativeOrder());
            int patched = 0;

            for (int i = 0; i < count; i++)
            {
                int at = i * stride + 15;

                if ((bb.get(at) & 0xFF) != 0xFF)
                {
                    bb.put(at, (byte) 0xFF);
                    patched++;
                }
            }

            return patched;
        }
        catch (Throwable t)
        {
            return -1;
        }
    }

    /**
     * 检查（必要时修复）顶点缓冲区的上传状态。
     *
     * <p>{@code VertexFormat.uploadToBuffer} 用的是 {@code data.remaining()}：position 若停在
     * limit 处，上传就是 0 字节，相当于拿空顶点缓冲画 2772 个索引 —— 顶点全部越界，
     * 既没有颜色也没有深度写入。这里只在 {@code remaining() <= 0} 时回卷并记录。
     */
    @Unique
    private static String bbspp$ensureUploadable(BuiltBuffer buffer)
    {
        try
        {
            ByteBuffer src = buffer.getBuffer();

            if (src == null)
            {
                return "缓冲区=null";
            }

            int pos = src.position();
            int lim = src.limit();
            int cap = src.capacity();
            boolean rewound = false;

            if (lim - pos <= 0)
            {
                src.position(0);
                rewound = true;
            }

            return "position=" + pos + ", limit=" + lim + ", capacity=" + cap
                + ", remaining=" + (lim - pos) + (rewound ? "（已回卷到 0）" : "");
        }
        catch (Throwable t)
        {
            return "检查失败:" + t.getClass().getSimpleName();
        }
    }

    @Unique
    private static int bbspp$glIdOfView(GpuTextureView view)
    {
        return view != null && view.texture() instanceof GlTexture gl ? gl.getGlId() : -1;
    }

    /**
     * 裁剪体检：把前几个顶点走一遍 {@code Projection * ModelView}，看它们到底落不落在裁剪体内。
     *
     * <p>这是「几何没错、状态也没错，却一个片元都没有」时唯一还能查的东西：
     * <ul>
     *   <li>顶点算出来是「外」→ 投影或 modelview 不是我们以为的那个矩阵（画到屏幕外了）；</li>
     *   <li>顶点是「内」而深度线仍然全 1.0 → 光栅化/绘制调用被驱动层拒了，看末尾的 GL 错误码。</li>
     * </ul>
     * 同时比对 pipeline 声明的顶点 stride 与缓冲区实际的 stride（不一致会让顶点数据被错位读取）。
     */
    @Unique
    private static String bbspp$clipCheck(RenderPipeline pipeline, BuiltBuffer buffer, Matrix4f modelView)
    {
        try
        {
            Matrix4f projection = BBSPickerRenderer.getProjectionOverride();
            Matrix4f combined = (projection == null ? new Matrix4f() : new Matrix4f(projection)).mul(new Matrix4f(modelView));

            BuiltBuffer.DrawParameters params = buffer.getDrawParameters();
            ByteBuffer src = buffer.getBuffer().duplicate().order(ByteOrder.nativeOrder());

            src.clear();

            int stride = params.format().getVertexSize();
            int pipelineStride = pipeline.getVertexFormat().getVertexSize();
            int count = Math.min(params.vertexCount(), 3);
            StringBuilder sb = new StringBuilder("投影=");

            sb.append(projection == null ? "null" : "有");
            sb.append(", stride 缓冲=").append(stride).append(" pipeline=").append(pipelineStride);
            sb.append(stride == pipelineStride ? "（一致）" : "（不一致！）");
            sb.append(", 目标glId=").append(bbspp$glIdOfView(targetColor));

            for (int i = 0; i < count; i++)
            {
                int at = i * stride;
                Vector4f p = new Vector4f(src.getFloat(at), src.getFloat(at + 4), src.getFloat(at + 8), 1F);

                combined.transform(p);

                boolean inside = Math.abs(p.x) <= p.w && Math.abs(p.y) <= p.w && p.z >= -p.w && p.z <= p.w;

                sb.append(", v").append(i).append("=(")
                    .append(String.format("%.2f", p.x)).append(", ")
                    .append(String.format("%.2f", p.y)).append(", ")
                    .append(String.format("%.2f", p.z)).append(", w=").append(String.format("%.2f", p.w))
                    .append(inside ? " 内)" : " 外)");
            }

            IntBuffer box = BufferUtils.createIntBuffer(4);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, box);
            IntBuffer vp = BufferUtils.createIntBuffer(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);

            sb.append(", scissor=").append(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST))
                .append(box.get(0)).append(",").append(box.get(1)).append(" ").append(box.get(2)).append("x").append(box.get(3));
            sb.append(", viewport=").append(vp.get(0)).append(",").append(vp.get(1)).append(" ").append(vp.get(2)).append("x").append(vp.get(3));
            sb.append(", 深度测试=").append(GL11.glIsEnabled(GL11.GL_DEPTH_TEST))
                .append(" func=0x").append(Integer.toHexString(GL11.glGetInteger(GL11.GL_DEPTH_FUNC)))
                .append(" mask=").append(GL11.glGetInteger(GL11.GL_DEPTH_WRITEMASK) != 0)
                .append(", 剔除=").append(GL11.glIsEnabled(GL11.GL_CULL_FACE))
                .append(", glError=0x").append(Integer.toHexString(GL11.glGetError()));

            return sb.toString();
        }
        catch (Throwable t)
        {
            return "体检失败:" + t.getClass().getSimpleName();
        }
    }

    /**
     * 读第一个顶点：position（0..11）、color（12..15）、UV2.x（28..29，即骨骼索引）。
     * 顶点布局按 {@code POSITION_COLOR_TEXTURE_LIGHT_NORMAL}：pos 3F / color 4B / uv0 2F /
     * uv1 2S / uv2 2S / normal 3B+pad = 36 字节。
     */
    @Unique
    private static String bbspp$describeVertex(BuiltBuffer buffer)
    {
        try
        {
            ByteBuffer src = buffer.getBuffer();

            if (src == null || src.capacity() < 36)
            {
                return "缓冲区为空（cap=" + (src == null ? -1 : src.capacity()) + "）";
            }

            ByteBuffer bb = src.duplicate().order(ByteOrder.nativeOrder());

            bb.clear();

            float x = bb.getFloat(0);
            float y = bb.getFloat(4);
            float z = bb.getFloat(8);
            int r = bb.get(12) & 0xFF;
            int g = bb.get(13) & 0xFF;
            int b = bb.get(14) & 0xFF;
            int a = bb.get(15) & 0xFF;
            int uv2 = bb.getShort(28) & 0xFFFF;

            return "pos=(" + String.format("%.3f", x) + ", " + String.format("%.3f", y) + ", " + String.format("%.3f", z)
                + "), color=(" + r + "," + g + "," + b + "," + a + "), UV2.x=" + uv2
                + ", 容量=" + src.capacity() + ", 索引数=" + buffer.getDrawParameters().indexCount();
        }
        catch (Throwable t)
        {
            return "抽样失败:" + t.getClass().getSimpleName();
        }
    }
}
