package gbeic.bbsplusplus.mixin12111;

import com.mojang.blaze3d.textures.GpuTexture;
import gbeic.bbsplusplus.BBSPlusPlusMod;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复「用了自定义尺寸 / 影片预览之后，世界渲染错误 + 每帧一条 GL_INVALID_VALUE」。
 *
 * <h2>根因</h2>
 * 1.21.11 的「高品质」画质（Fabulous）把世界拆成主帧缓冲 + 半透明帧缓冲两层，
 * 渲染时由 {@code WorldRenderer} 调 {@code Framebuffer.copyDepthFrom(主帧缓冲)}
 * 把主层深度拷进半透明层。而 {@code Framebuffer.copyDepthFrom} 的实现是：
 *
 * <pre>
 * encoder.copyTextureToTexture(from.getDepthAttachment(), this.getDepthAttachment(),
 *                              0, 0, 0, 0, 0, this.textureWidth, this.textureHeight);
 * </pre>
 *
 * 也就是说<b>拷贝区域取的是「目标帧缓冲自己声明的 textureWidth/textureHeight」</b>，
 * 完全不看源纹理的真实大小。BBS 的影片预览 / 视频导出会把 {@code mc.framebuffer} 换成
 * 自定义尺寸（日志里出现过 1514×592、756×280），而
 * {@code BBSRendering.resizeExtraFramebuffers()} 又把半透明帧缓冲调成窗口尺寸。
 * 两端不一致时那个 y 区间就超出源纹理边界：
 * {@code GL_INVALID_VALUE: The y values exceeds the boundaries of the corresponding image object}，
 * 每帧一条；半透明层的深度则保持垃圾值，于是「世界渲染错误」（挡住关系全乱）。
 *
 * <h2>修法</h2>
 * 在 {@code copyDepthFrom} 入口把「声明的宽高」临时缩到
 * {@code min(目标声明尺寸, 源深度纹理真实尺寸)}，方法返回时再恢复。
 * 尺寸本来就一致时完全不改；不一致时退化成「只拷重叠区域」，不再报错。
 * 第一次裁剪会打一条 WARN 带上两边尺寸，便于继续追上游为什么不同步。
 */
@Mixin(value = Framebuffer.class)
public class FramebufferDepthCopyMixin121
{
    @Shadow
    public int textureWidth;

    @Shadow
    public int textureHeight;

    @Unique
    private int bbspp$savedWidth;

    @Unique
    private int bbspp$savedHeight;

    @Unique
    private boolean bbspp$shrunk;

    @Unique
    private static boolean bbspp$loggedMismatch;

    @Inject(method = "copyDepthFrom", at = @At("HEAD"))
    private void bbspp$shrinkToSource(Framebuffer from, CallbackInfo info)
    {
        this.bbspp$shrunk = false;

        if (from == null)
        {
            return;
        }

        GpuTexture source = from.getDepthAttachment();

        if (source == null)
        {
            return;
        }

        int width = source.getWidth(0);
        int height = source.getHeight(0);

        if (width <= 0 || height <= 0)
        {
            return;
        }

        if (width >= this.textureWidth && height >= this.textureHeight)
        {
            return;
        }

        if (!bbspp$loggedMismatch)
        {
            bbspp$loggedMismatch = true;

            BBSPlusPlusMod.LOGGER.warn(
                "[BBS++] 深度拷贝尺寸不一致：目标声明 {}x{}，源深度纹理实际 {}x{}，已按重叠区域裁剪（上游自定义尺寸与窗口帧缓冲不同步）",
                this.textureWidth, this.textureHeight, width, height);
        }

        this.bbspp$savedWidth = this.textureWidth;
        this.bbspp$savedHeight = this.textureHeight;
        this.bbspp$shrunk = true;
        this.textureWidth = Math.min(this.textureWidth, width);
        this.textureHeight = Math.min(this.textureHeight, height);
    }

    @Inject(method = "copyDepthFrom", at = @At("RETURN"))
    private void bbspp$restoreDeclaredSize(Framebuffer from, CallbackInfo info)
    {
        if (!this.bbspp$shrunk)
        {
            return;
        }

        this.textureWidth = this.bbspp$savedWidth;
        this.textureHeight = this.bbspp$savedHeight;
        this.bbspp$shrunk = false;
    }
}
