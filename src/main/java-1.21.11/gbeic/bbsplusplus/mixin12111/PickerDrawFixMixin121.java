package gbeic.bbsplusplus.mixin12111;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import gbeic.bbsplusplus.BBSPlusPlusMod;
import mchorse.bbs_mod.client.render.picker.BBSPickerRenderer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.render.BuiltBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 1.21.11 picker 绘制端的修复。
 *
 * <p>修复的是「模型拾取一个片元都没画出来」：{@code picker_models.fsh} 开头是
 * {@code color = texture(Sampler0, texCoord0) * vertexColor; if (color.a < 0.1) discard;}，
 * 而模型拾取时 Sampler0 指向的是 albedo（可能采到 alpha=0 或 mipmap 不完整的 adopted 纹理），
 * 顶点色 alpha 也可能为 0 —— 两条都会让整模型被 discard。拾取只关心「几何轮廓 → 骨骼 ID」，
 * 不需要 albedo，所以这里：</p>
 * <ol>
 *   <li>把 Sampler0 换成 1×1 不透明白纹理 + NEAREST 采样器；</li>
 *   <li>把缓冲区里每个顶点的 alpha 改成 255；</li>
 *   <li>绘制期间临时清掉 {@code RenderSystem.outputColorTextureOverride/outputDepthTextureOverride}，
 *       防止拾取被改道到预览 FBO，绘制结束原样恢复。</li>
 * </ol>
 *
 * <h2>不要去掉 pass 的深度附件</h2>
 * 早先那版 {@code @Redirect} 把 {@code createRenderPass(name, color, clear, depth, clearDepth)}
 * 降级成只有颜色附件的三参重载，实测对拾取命中毫无帮助，却让 picker 的深度写入行为偏离上游
 * （{@code LEQUAL_DEPTH_TEST} 依赖深度被清过）。上面三条修复都与深度无关，pass 按上游方式建。
 */
@Mixin(value = BBSPickerRenderer.class, remap = false)
public abstract class PickerDrawFixMixin121
{
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
            /* usage：COPY_DST(1) | TEXTURE_BINDING(4) | RENDER_ATTACHMENT(8)
             * —— clearColorTexture 走 validateColorAttachment，必须有 RENDER_ATTACHMENT。 */
            GpuTexture tex = RenderSystem.getDevice().createTexture("bbspp_picker_opaque", 13, TextureFormat.RGBA8, 1, 1, 1, 1);

            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(tex, 0xFFFFFFFF);

            bbspp$whiteView = RenderSystem.getDevice().createTextureView(tex);
            bbspp$nearest = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);

            return true;
        }
        catch (Throwable t)
        {
            BBSPlusPlusMod.LOGGER.warn("[BBS++] 拾取修复：不透明拾取纹理创建失败（{}）", t.getClass().getSimpleName());

            return false;
        }
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
        }

        bbspp$savedColorOverride.set(RenderSystem.outputColorTextureOverride);
        bbspp$savedDepthOverride.set(RenderSystem.outputDepthTextureOverride);
        RenderSystem.outputColorTextureOverride = null;
        RenderSystem.outputDepthTextureOverride = null;

        bbspp$prepareUploadable(buffer);
        bbspp$forceOpaqueAlpha(buffer);
    }

    @Inject(
        method = "draw(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/render/BuiltBuffer;Lorg/joml/Matrix4f;)V",
        at = @At("RETURN"),
        require = 0
    )
    private static void bbspp$afterPickerDraw(RenderPipeline pipeline, BuiltBuffer buffer, Matrix4f modelView, CallbackInfo ci)
    {
        RenderSystem.outputColorTextureOverride = bbspp$savedColorOverride.get();
        RenderSystem.outputDepthTextureOverride = bbspp$savedDepthOverride.get();
        bbspp$savedColorOverride.remove();
        bbspp$savedDepthOverride.remove();
    }

    /**
     * {@code uploadImmediateVertexBuffer} 按 {@code ByteBuffer.remaining()} 上传：position 若停在
     * limit 处，上传的就是 0 字节，绘制索引时顶点全部越界 —— 一个片元都没有。
     * 这里只在 {@code remaining() <= 0} 时回卷。
     */
    @Unique
    private static void bbspp$prepareUploadable(BuiltBuffer buffer)
    {
        try
        {
            ByteBuffer src = buffer == null ? null : buffer.getBuffer();

            if (src != null && src.limit() - src.position() <= 0)
            {
                src.position(0);
            }
        }
        catch (Throwable ignored)
        {
        }
    }

    /**
     * 把拾取缓冲区里每个顶点的 color.a 改成 255。
     *
     * <p>顶点布局（{@code POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL}）：
     * pos 3F(0..11) / color is 4B(12..15)，alpha 在 +15。</p>
     */
    @Unique
    private static void bbspp$forceOpaqueAlpha(BuiltBuffer buffer)
    {
        if (buffer == null)
        {
            return;
        }

        try
        {
            ByteBuffer src = buffer.getBuffer();
            BuiltBuffer.DrawParameters params = buffer.getDrawParameters();

            if (src == null || params == null)
            {
                return;
            }

            int stride = params.format().getVertexSize();
            int count = params.vertexCount();

            if (stride < 16 || count <= 0 || src.capacity() < (long) stride * count)
            {
                return;
            }

            ByteBuffer bb = src.duplicate().order(ByteOrder.nativeOrder());

            for (int i = 0; i < count; i++)
            {
                int at = i * stride + 15;

                if ((bb.get(at) & 0xFF) != 0xFF)
                {
                    bb.put(at, (byte) 0xFF);
                }
            }
        }
        catch (Throwable ignored)
        {
        }
    }
}
