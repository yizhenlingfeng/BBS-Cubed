package gbeic.bbsplusplus.client.screen;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import gbeic.bbsplusplus.BBSPlusPlusMod;
import gbeic.bbsplusplus.clips.screen.ColorEffect;
import mchorse.bbs_mod.client.render.OffscreenTarget;
import mchorse.bbs_mod.client.render.ScreenQuadPass;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.MappableRingBuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * 1.21.11 的屏幕调色 / 电影效果：离屏渲染 + 已记录的 blit。
 *
 * <p>为什么不能沿用 1.20 的写法：那版直接 {@code glCopyTexSubImage2D} 复制主 framebuffer、
 * 再手搓 VAO 画回去，而 1.21.11 的 GUI 是两阶段延迟合成（{@code ImmediateGui} 在帧末才把
 * {@code GuiRenderState} 交给 {@code GuiRenderer}），mid-record 立刻画的 pass 会被之后的合成
 * 覆盖掉。{@link OffscreenTarget} 的类注释专门记了这条。所以这里改成：
 * 手写一个 {@code RenderPass} 把调色结果渲进离屏纹理，再用
 * {@code batcher.texturedBox(glId, ...)} 这条**已记录**的路径把它 blit 回画面 ——
 * 与上游 {@code UIPixelsEditor.renderSelection()} 完全同一个套路。</p>
 *
 * <p>参数走自己的 std140 UBO（{@code ScreenGrade}），不用原版 {@code PostEffectProcessor}：
 * 那条路的 uniform 在 {@code parseEffect} 时就写死进常量缓冲了，而这里的 brightness/time
 * 每帧都在变。</p>
 */
public final class ScreenGradeRenderer
{
    private static final Identifier PIPELINE_ID = Identifier.of("bbsplusplus", "pipeline/screen_grade");
    private static final Identifier SHADER = Identifier.of("bbsplusplus", "core/screen_grade");
    private static final String UBO_NAME = "ScreenGrade";

    /** 11 个 vec4（176 字节）：全部 16 字节对齐，和 shader 里的 ScreenGrade 块逐字段对应。 */
    private static final int UBO_SIZE = 176;

    private static final OffscreenTarget TARGET = new OffscreenTarget("bbsplusplus:screen_grade");
    private static final MappableRingBuffer[] RING = new MappableRingBuffer[1];

    private static RenderPipeline pipeline;
    private static boolean broken;

    private static Params cachedParams;
    private static GpuBufferSlice cachedSlice;

    private ScreenGradeRenderer()
    {
    }

    /**
     * 把这一帧所有 {@link ColorEffect} 叠加后跑一次全屏调色，并登记一次 blit。
     *
     * @param batcher 帧末叠加层的批处理；实际合成发生在 {@code ImmediateGui.end()}
     */
    public static void apply(Batcher2D batcher, List<ColorEffect> effects)
    {
        Params params = accumulate(effects);

        if (!params.any())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        Framebuffer fb = mc.getFramebuffer();
        int width = fb.textureWidth;
        int height = fb.textureHeight;

        if (width <= 0 || height <= 0)
        {
            return;
        }

        RenderPipeline pipeline = pipeline();

        if (pipeline == null)
        {
            return;
        }

        GpuTextureView target = TARGET.ensure(width, height);
        GpuTextureView source = fb.getColorAttachmentView();

        if (target == null || source == null)
        {
            return;
        }

        /* UBO 必须在开 pass 之前写完：ring 的 rotate() 要等上一帧的 fence。 */
        GpuBufferSlice ubo = writeParams(params);

        if (ubo == null)
        {
            return;
        }

        boolean drawn = ScreenQuadPass.draw("bbsplusplus:screen_grade", new ScreenQuadPass.Quad(pipeline, target, width, height)
            .rect(0, 0, width, height)
            .uv(0F, 0F, 1F, 1F)
            .texture(source, ScreenQuadPass.linear())
            .ubo(UBO_NAME, ubo)
            .clear());

        if (!drawn)
        {
            return;
        }

        int scaledWidth = mc.getWindow().getScaledWidth();
        int scaledHeight = mc.getWindow().getScaledHeight();

        /* v1=height, v2=0：本工程里所有离屏 target 的 blit 都带这个 V 翻转
         * （见 UIPixelsEditor.renderSelection），跟着用才能对上。 */
        batcher.texturedBox(TARGET.getGlId(), 0xFFFFFFFF,
            0, 0, scaledWidth, scaledHeight,
            0F, height, width, 0F,
            width, height);
    }

    /**
     * 按 1.20 的累加语义合并多个效果。
     *
     * <p>规则必须原样保留：vignette 取最大强度、grade 全累加、distort 全累加、
     * cinematic 除 lensDistortion 累加外都取最大，time 直取最后一个。</p>
     */
    private static Params accumulate(List<ColorEffect> effects)
    {
        float vigStr = 0F;
        float vigSmooth = 0.5F;
        float vigR = 0F, vigG = 0F, vigB = 0F;
        float brightness = 0F, contrast = 0F, saturation = 0F, hue = 0F;
        float liftR = 0F, liftG = 0F, liftB = 0F;
        float gammaR = 0F, gammaG = 0F, gammaB = 0F;
        float gainR = 0F, gainG = 0F, gainB = 0F;
        float distortX = 0F, distortY = 0F;
        float aberration = 0F, vhs = 0F, lensDistortion = 0F, vintage = 0F;
        float radialBlur = 0F, rain = 0F, dust = 0F, lightLeak = 0F, time = 0F;

        for (ColorEffect e : effects)
        {
            if (e.hasVignette)
            {
                vigStr = Math.max(vigStr, e.vignetteStrength);
                vigSmooth = e.vignetteSmoothness;
                vigR = ((e.vignetteColor >> 16) & 0xFF) / 255F;
                vigG = ((e.vignetteColor >> 8) & 0xFF) / 255F;
                vigB = (e.vignetteColor & 0xFF) / 255F;
            }

            if (e.hasGrade)
            {
                brightness += e.brightness;
                contrast += e.contrast;
                saturation += e.saturation;
                hue += e.hue;
                liftR += e.liftR;
                liftG += e.liftG;
                liftB += e.liftB;
                gammaR += e.gammaR;
                gammaG += e.gammaG;
                gammaB += e.gammaB;
                gainR += e.gainR;
                gainG += e.gainG;
                gainB += e.gainB;
            }

            if (e.hasDistort)
            {
                distortX += e.distortX;
                distortY += e.distortY;
            }

            if (e.hasCinematic)
            {
                aberration = Math.max(aberration, e.aberration);
                vhs = Math.max(vhs, e.vhs);
                lensDistortion += e.lensDistortion;
                vintage = Math.max(vintage, e.vintage);
                radialBlur = Math.max(radialBlur, e.radialBlur);
                rain = Math.max(rain, e.rain);
                dust = Math.max(dust, e.dust);
                lightLeak = Math.max(lightLeak, e.lightLeak);
                time = e.time;
            }
        }

        return new Params(vigStr, vigSmooth, vigR, vigG, vigB,
            brightness, contrast, saturation, hue,
            liftR, liftG, liftB, gammaR, gammaG, gammaB, gainR, gainG, gainB,
            distortX, distortY,
            aberration, vhs, lensDistortion, vintage, radialBlur, rain, dust, lightLeak, time);
    }

    private static GpuBufferSlice writeParams(Params params)
    {
        if (cachedSlice != null && params.equals(cachedParams))
        {
            return cachedSlice;
        }

        if (RING[0] == null)
        {
            RING[0] = new MappableRingBuffer(() -> "bbsplusplus:screen_grade", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UBO_SIZE);
        }

        RING[0].rotate();

        GpuBuffer buffer = RING[0].getBlocking();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        try (GpuBuffer.MappedView view = encoder.mapBuffer(buffer, false, true))
        {
            Std140Builder builder = Std140Builder.intoBuffer(view.data());

            builder.putVec4(params.vigStr, params.vigSmooth, 0F, 0F);
            builder.putVec4(params.vigR, params.vigG, params.vigB, 0F);
            builder.putVec4(params.brightness, params.contrast, params.saturation, params.hue);
            builder.putVec4(params.liftR, params.liftG, params.liftB, 0F);
            builder.putVec4(params.gammaR, params.gammaG, params.gammaB, 0F);
            builder.putVec4(params.gainR, params.gainG, params.gainB, 0F);
            /* grain 在 1.20 那版里是恒 0/1/0 的占位，这里保持一致。 */
            builder.putVec4(0F, 1F, 0F, 0F);
            builder.putVec4(params.distortX, params.distortY, 0F, 0F);
            builder.putVec4(params.aberration, params.vhs, params.lensDistortion, params.vintage);
            builder.putVec4(params.radialBlur, params.rain, params.dust, params.lightLeak);
            builder.putVec4(params.time, 0F, 0F, 0F);
        }

        GpuBufferSlice slice = buffer.slice(0L, UBO_SIZE);

        cachedParams = params;
        cachedSlice = slice;

        return slice;
    }

    private static RenderPipeline pipeline()
    {
        if (broken)
        {
            return null;
        }

        if (pipeline != null)
        {
            return pipeline;
        }

        try
        {
            pipeline = RenderPipelines.register(RenderPipeline.builder()
                .withLocation(PIPELINE_ID)
                .withVertexShader(SHADER)
                .withFragmentShader(SHADER)
                .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withCull(false)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform(UBO_NAME, UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .build());
        }
        catch (Throwable t)
        {
            broken = true;
            BBSPlusPlusMod.LOGGER.error("[BBS++] 屏幕调色管线创建失败，本帧起停用屏幕效果", t);
            return null;
        }

        return pipeline;
    }

    /** 冻结的一帧参数；同时用来做「参数没变就复用同一个 UBO 切片」的缓存键。 */
    private record Params(
        float vigStr, float vigSmooth, float vigR, float vigG, float vigB,
        float brightness, float contrast, float saturation, float hue,
        float liftR, float liftG, float liftB,
        float gammaR, float gammaG, float gammaB,
        float gainR, float gainG, float gainB,
        float distortX, float distortY,
        float aberration, float vhs, float lensDistortion, float vintage,
        float radialBlur, float rain, float dust, float lightLeak, float time
    ) {
        /** 四个通道全关就等于「没有任何屏幕效果」，此时连离屏 pass 都不该开。 */
        boolean any()
        {
            return this.vigStr > 0.001F
                || Math.abs(this.brightness) > 0.001F
                || Math.abs(this.contrast) > 0.001F
                || Math.abs(this.saturation) > 0.001F
                || Math.abs(this.hue) > 0.01F
                || Math.abs(this.liftR) > 0.001F || Math.abs(this.liftG) > 0.001F || Math.abs(this.liftB) > 0.001F
                || Math.abs(this.gammaR) > 0.001F || Math.abs(this.gammaG) > 0.001F || Math.abs(this.gammaB) > 0.001F
                || Math.abs(this.gainR) > 0.001F || Math.abs(this.gainG) > 0.001F || Math.abs(this.gainB) > 0.001F
                || Math.abs(this.distortX) > 0.0001F || Math.abs(this.distortY) > 0.0001F
                || this.aberration > 0.001F
                || this.vhs > 0.001F
                || Math.abs(this.lensDistortion) > 0.001F
                || this.vintage > 0.001F
                || this.radialBlur > 0.001F
                || this.rain > 0.001F
                || this.dust > 0.001F
                || this.lightLeak > 0.001F;
        }
    }
}
