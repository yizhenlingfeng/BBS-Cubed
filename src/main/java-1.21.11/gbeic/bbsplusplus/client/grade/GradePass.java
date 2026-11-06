package gbeic.bbsplusplus.client.grade;

import gbeic.bbsplusplus.BBSPlusPlusMod;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.render.ScreenQuadPass;
import mchorse.bbs_mod.forms.renderers.utils.FormOverlay;
import mchorse.bbs_mod.forms.FormRenderCapture;
import mchorse.bbs_mod.graphics.texture.AdoptedTexture;
import mchorse.bbs_mod.graphics.texture.Texture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.MappableRingBuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * 1.21.11 的纹理分级：调色 / 白化走「接管基础绘制」，附魔光效走「额外一层叠加」。
 *
 * <p>为什么基础绘制必须被接管：tint/whiten 改的是模型本体的颜色，1.21.11 的
 * {@code RenderLayer} 只认引擎内建 uniform，自定义的 {@code GradeParams} 块搭不上车
 * （上游 {@code ScreenQuadPass} javadoc 的原话）。所以这里自己建 pipeline + 手写
 * {@code RenderPass}，并在 {@code FormTranslucentQueue.submit} 处把原提交取消掉，
 * 换成自己的 {@code DrawCommand} —— 但**仍然入队**，这样半透明模型的远近排序不变。
 *
 * <p>顶点属性照搬上游 model 管线（POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL）：
 * UV1 指向 overlay swatch（逐骨骼调色板，见 {@code FormOverlay}），UV2 指向 lightmap。
 * 所以基础绘制里逐骨骼 tint 与光照都保住了；代价是 hurt-flash 红闪在分级生效时会被
 * swatch 顶掉（v1 取舍，影响极小）。
 */
public final class GradePass
{
    public static final String UBO_NAME = "GradeParams";
    public static final int UBO_SIZE = 64;

    private static final Identifier GRADE_SHADER = Identifier.of("bbsplusplus", "core/model_grade");
    private static final Identifier GLINT_SHADER = Identifier.of("bbsplusplus", "core/model_glint");
    private static final Identifier GRADE_LOCATION_CULLED = Identifier.of("bbsplusplus", "pipeline/model_grade_culled");
    private static final Identifier GRADE_LOCATION_NO_CULL = Identifier.of("bbsplusplus", "pipeline/model_grade_no_cull");
    private static final Identifier GLINT_LOCATION = Identifier.of("bbsplusplus", "pipeline/model_glint");

    private static RenderPipeline gradeCulled;
    private static RenderPipeline gradeNoCull;
    private static RenderPipeline gradeCulledWorld;
    private static RenderPipeline gradeNoCullWorld;
    private static RenderPipeline glint;
    private static RenderPipeline glintWorld;
    private static MappableRingBuffer ring;
    private static GradeParams cachedParams;
    private static GpuBufferSlice cachedSlice;
    private static boolean broken;

    private GradePass()
    {
    }

    /** 调色/白化的基础绘制：替换原来那条 {@code RenderLayer.draw(built)}。 */
    public static boolean drawBase(FormRenderCapture.Captured captured, Matrix4f modelView, Identifier textureId, GradeParams params, boolean cull)
    {
        RenderPipeline pipeline = gradePipeline(cull, worldForm());

        return pipeline != null && draw(pipeline, captured, modelView, textureId, params);
    }

    /** 附魔光效：在基础绘制之后叠一层 ADDITIVE。 */
    public static boolean drawGlint(FormRenderCapture.Captured captured, Matrix4f modelView, Identifier textureId, GradeParams params)
    {
        RenderPipeline pipeline = glintPipeline(worldForm());

        return pipeline != null && draw(pipeline, captured, modelView, textureId, params);
    }

    /**
     * 预检：基础管线是否真的建起来了。
     *
     * <p>{@code drawBase} 在管线为 null 时会静默返回 false，而调用方（{@code GradeSubmitHook}）
     * 已经 {@code cancel()} 掉原绘制、并把 {@code BuiltBuffer} 关闭了 —— 那时模型会整块消失。
     * 所以「接管」必须先过这道预检，建不起来就老老实实走 BBS 原本的 layer。</p>
     */
    public static boolean readyBase()
    {
        boolean world = worldForm();

        return gradePipeline(false, world) != null && gradePipeline(true, world) != null;
    }

    /** 预检：光效管线是否可用（不可用时最多丢光效，不影响基础绘制）。 */
    public static boolean readyGlint()
    {
        return glintPipeline(worldForm()) != null;
    }

    /**
     * 是否处于「世界帧」里（影片的 AFTER_ENTITIES、模型方块的 vanilla 通道）。
     *
     * <p>世界帧里的绘制必须让 Iris 接管（{@code mirrorIrisPipeline}），否则开光影包时我们的
     * 自定义 pipeline 不在 Iris 的程序表里，模型会黑掉或直接不画；编辑器预览与 GUI 则必须
     * 保持 BBS 自己的着色器，所以两套 pipeline 分开缓存 —— 与上游 {@code BBSShaders.modelPipeline}
     * 的 world 变体同一个思路。
     */
    private static boolean worldForm()
    {
        try
        {
            return BBSRendering.isIrisWorldForms();
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    public static Identifier adopt(Texture texture)
    {
        return texture == null ? null : AdoptedTexture.identifier(texture);
    }

    /** 上传用的直接缓冲区；不缓存的话每帧每个模型都要新建原生内存，GC 压力反而更大。 */
    private static ByteBuffer uploadScratch;
    private static boolean loggedUploadCopy;

    /**
     * 把堆内 ByteBuffer 换成直接缓冲区再交给 GL。
     *
     * <p>{@code FormRenderCapture.copy()} 里是 {@code ByteBuffer.allocate(...)}，也就是堆内缓冲区。
     * LWJGL 的 {@code glNamedBufferStorage(int, ByteBuffer, int)} 只做
     * {@code nglNamedBufferStorage(id, remaining, MemoryUtil.memAddress(buf), flags)}，
     * 而 {@code memAddress} 对堆内缓冲区的返回值恒为 0 —— 驱动于是收到
     * 「size > 0 但 data == NULL」，在 NVIDIA 621.01 上直接读 NULL + 0x10 内存，
     * 崩在 {@code nvoglv64.dll}（EXCEPTION_ACCESS_VIOLATION，Java 层接不住）。
     *
     * <p>v1 的 GlintPass 与 GradePass 都一直这么调，所以这是稳定复现的必崩路径，不是偶发。
     * 上传本身是同步的（驱动在调用返回前就把字节拷走），因此这个 scratch 可以跨 draw 复用。</p>
     */
    private static ByteBuffer directUploadBuffer(ByteBuffer data)
    {
        if (data.isDirect())
        {
            return data;
        }

        int length = data.remaining();

        if (length <= 0)
        {
            return data;
        }

        if (uploadScratch == null || uploadScratch.capacity() < length)
        {
            uploadScratch = ByteBuffer.allocateDirect(Math.max(length, 64 * 1024)).order(ByteOrder.nativeOrder());
        }

        uploadScratch.clear();
        uploadScratch.put(data.duplicate());
        uploadScratch.flip();

        if (!loggedUploadCopy)
        {
            loggedUploadCopy = true;
            BBSPlusPlusMod.LOGGER.info("[BBS++] 纹理分级：BBS 快照是堆内缓冲区，已改为直接缓冲区上传（glNamedBufferStorage 崩溃修复生效）");
        }

        return uploadScratch;
    }

    private static boolean draw(RenderPipeline pipeline, FormRenderCapture.Captured captured, Matrix4f modelView, Identifier textureId, GradeParams params)
    {
        if (captured == null || textureId == null || params == null || modelView == null)
        {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        AbstractTexture texture = client.getTextureManager().getTexture(textureId);

        if (texture == null || texture.getGlTextureView() == null)
        {
            return false;
        }

        GpuDevice device = RenderSystem.getDevice();
        CommandEncoder encoder = device.createCommandEncoder();
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().write(modelView, new Vector4f(1F, 1F, 1F, 1F), new Vector3f(), new Matrix4f());

        /* UBO 必须在开 pass 之前写：ring buffer 的 rotate() 要等上一帧的 fence。 */
        GpuBufferSlice ubo = writeParams(encoder, params);

        VertexFormat format = pipeline.getVertexFormat();
        GpuBuffer vertexBuffer = format.uploadImmediateVertexBuffer(directUploadBuffer(captured.data()));
        int indexCount = captured.params().indexCount();
        RenderSystem.ShapeIndexBuffer sequential = RenderSystem.getSequentialBuffer(captured.params().mode());
        GpuBuffer indexBuffer = sequential.getIndexBuffer(indexCount);
        VertexFormat.IndexType indexType = sequential.getIndexType();

        /* 目标附件必须跟着 1.21.11 的「当前输出」走：面板里的 3D 预览、framebuffer 表单、
         * GUI 元素渲染都靠 RenderSystem.outputColorTextureOverride 改道（见上游
         * ModelPreviewRenderer.begin()），只有世界与普通 GUI 才会落到主 framebuffer。
         * 不认这两个 override 的话，预览里的分级绘制会画到主画面上。 */
        GpuTextureView color = RenderSystem.outputColorTextureOverride;
        GpuTextureView depth = RenderSystem.outputDepthTextureOverride;

        if (color == null)
        {
            Framebuffer framebuffer = client.getFramebuffer();

            color = framebuffer.getColorAttachmentView();
            depth = framebuffer.useDepthAttachment ? framebuffer.getDepthAttachmentView() : null;
        }

        /* Sampler1 = 逐骨骼调色板。swatch 是「用到才注册」的（FormOverlay.upload 里
         * AdoptedTexture.register），还没人调过调色板时 getTexture 会抛，这时退回原版
         * overlay 贴图：它的 DEFAULT_UV 那一格本来就是中性白，UV1 也指向它，正是上游
         * useOverlay() 层的默认绑定，所以结果与没有调色板时一致。 */
        GpuTextureView overlayView = null;

        try
        {
            AbstractTexture overlay = client.getTextureManager().getTexture(FormOverlay.SWATCH);

            if (overlay != null)
            {
                overlayView = overlay.getGlTextureView();
            }
        }
        catch (Throwable ignored)
        {
            /* 没注册过 swatch，走下面的 fallback。 */
        }

        if (overlayView == null)
        {
            overlayView = client.gameRenderer.getOverlayTexture().getTextureView();
        }

        GpuTextureView lightmapView = client.gameRenderer.getLightmapTextureManager().getGlTextureView();

        try (RenderPass pass = encoder.createRenderPass(() -> "bbsplusplus:grade", color, OptionalInt.empty(), depth, OptionalDouble.empty()))
        {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setUniform(UBO_NAME, ubo);
            pass.setVertexBuffer(0, vertexBuffer);
            pass.bindTexture("Sampler0", texture.getGlTextureView(), texture.getSampler());
            pass.bindTexture("Sampler1", overlayView, ScreenQuadPass.nearest());
            pass.bindTexture("Sampler2", lightmapView, ScreenQuadPass.linear());
            pass.setIndexBuffer(indexBuffer, indexType);
            pass.drawIndexed(0, 0, indexCount, 1);
        }

        return true;
    }

    private static GpuBufferSlice writeParams(CommandEncoder encoder, GradeParams params)
    {
        /* 同参数复用上一份切片：MappableRingBuffer 的槽位数是固定的（BUFFER_COUNT），
         * 每 draw 都 rotate() 会在「一帧里有多个分级模型」时被 fence 卡住 ——
         * 而 drawBase 与 drawGlint 对同一个模型用的是同一组参数，光这两次就已经能省掉一半。
         * 复用时不写入，因此也不需要新的 fence；内容没变，重复读同一片段是安全的。 */
        if (cachedSlice != null && params.equals(cachedParams))
        {
            return cachedSlice;
        }

        if (ring == null)
        {
            ring = new MappableRingBuffer(() -> "bbsplusplus:grade_params", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UBO_SIZE);
        }

        ring.rotate();

        GpuBuffer buffer = ring.getBlocking();

        try (GpuBuffer.MappedView view = encoder.mapBuffer(buffer, false, true))
        {
            Std140Builder builder = Std140Builder.intoBuffer(view.data());

            builder.putVec4(params.tintR(), params.tintG(), params.tintB(), params.tintA());
            builder.putVec4(params.whiten(), params.glint() ? 1F : 0F, params.glintPhase(), params.glintBrightness());
            builder.putVec4(params.glintR(), params.glintG(), params.glintB(), params.glintA());
            builder.putVec4(0F, 0F, 0F, 0F);
        }

        GpuBufferSlice slice = buffer.slice(0L, UBO_SIZE);

        cachedParams = params;
        cachedSlice = slice;

        return slice;
    }

    private static RenderPipeline gradePipeline(boolean cull, boolean world)
    {
        if (broken)
        {
            return null;
        }

        if (world)
        {
            if (cull)
            {
                if (gradeCulledWorld == null)
                {
                    gradeCulledWorld = buildGrade(GRADE_LOCATION_CULLED, true, true);
                }

                return gradeCulledWorld;
            }

            if (gradeNoCullWorld == null)
            {
                gradeNoCullWorld = buildGrade(GRADE_LOCATION_NO_CULL, false, true);
            }

            return gradeNoCullWorld;
        }

        if (cull)
        {
            if (gradeCulled == null)
            {
                gradeCulled = buildGrade(GRADE_LOCATION_CULLED, true, false);
            }

            return gradeCulled;
        }

        if (gradeNoCull == null)
        {
            gradeNoCull = buildGrade(GRADE_LOCATION_NO_CULL, false, false);
        }

        return gradeNoCull;
    }

    private static RenderPipeline buildGrade(Identifier location, boolean cull, boolean world)
    {
        return register(location, RenderPipeline.builder()
            .withLocation(location)
            .withVertexShader(GRADE_SHADER)
            .withFragmentShader(GRADE_SHADER)
            .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(true)
            .withCull(cull)
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            .withUniform("Lighting", UniformType.UNIFORM_BUFFER)
            .withUniform(UBO_NAME, UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withSampler("Sampler2")
            .build(), world);
    }

    private static RenderPipeline glintPipeline(boolean world)
    {
        if (broken)
        {
            return null;
        }

        if (world)
        {
            if (glintWorld == null)
            {
                glintWorld = buildGlint(GLINT_LOCATION, true);
            }

            return glintWorld;
        }

        if (glint == null)
        {
            glint = buildGlint(GLINT_LOCATION, false);
        }

        return glint;
    }

    private static RenderPipeline buildGlint(Identifier location, boolean world)
    {
        return register(location, RenderPipeline.builder()
            .withLocation(location)
            .withVertexShader(GLINT_SHADER)
            .withFragmentShader(GLINT_SHADER)
            .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.ADDITIVE)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            .withUniform("Lighting", UniformType.UNIFORM_BUFFER)
            .withUniform(UBO_NAME, UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withSampler("Sampler2")
            .build(), world);
    }

    private static RenderPipeline register(Identifier location, RenderPipeline pipeline, boolean world)
    {
        try
        {
            RenderPipeline registered = RenderPipelines.register(pipeline);

            /* 世界帧里要让 Iris 把我们的 pipeline 映射到它自己的程序上，否则光影包下这块几何不画。 */
            if (world)
            {
                BBSRendering.mirrorIrisPipeline(registered, RenderPipelines.ENTITY_TRANSLUCENT);
            }

            gbeic.bbsplusplus.BBSPlusPlusMod.LOGGER.info("[BBS++] 纹理分级管线已注册：{}（world={}）", location, world);

            return registered;
        }
        catch (Throwable t)
        {
            broken = true;

            gbeic.bbsplusplus.BBSPlusPlusMod.LOGGER.error("[BBS++] 纹理分级管线创建失败（本会话不再尝试）：" + location, t);

            return null;
        }
    }
}
