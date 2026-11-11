package gbeic.bbsplusplus.mixin12111;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.textures.GpuTexture;
import gbeic.bbsplusplus.BBSPlusPlusMod;
import mchorse.bbs_mod.client.render.picker.BBSPickerRenderer;
import mchorse.bbs_mod.graphics.ModelPreviewRenderer;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import net.minecraft.client.texture.GlTexture;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL45;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.FloatBuffer;

/**
 * 1.21.11 面板预览里「普通左键点模型选不中骨骼」的修复。
 *
 * <h2>本 mixin 做的三件事</h2>
 * <ol>
 *   <li>{@code apply()} 前禁用 scissor、结束后恢复 —— 补回 1.20.1 picking pass 里的
 *       {@code _disableScissorTest()}。1.21.11 的 {@code GlCommandEncoder.createRenderPass}
 *       只在「需要清屏」的分支里临时关 scissor，而 picker 传的是 {@code empty()}，
 *       于是 UI 阶段的 scissor 会把整个 picker 几何裁掉。</li>
 *   <li>{@code apply()} 后把 {@code ModelPreviewRenderer.PROJECTION} 装成 picker 的投影覆盖，
 *       {@code unbind()} 后清掉。</li>
 *   <li>{@code pick()} 什么都没选中（{@code index == 0}）时，用 {@code glGetTextureSubImage}
 *       直接从 stencil 纹理读回窗口重新解码，作为 FBO 读回失败的兜底。</li>
 * </ol>
 *
 * <h2>两条必须守住的不变量</h2>
 * <ul>
 *   <li><b>不要动 draw/read buffer</b>：这里曾用 {@code glDrawBuffer(GL_NONE)} 读深度，
 *       读完只把 FBO 绑回 0 而没恢复 draw buffer，导致默认帧缓冲此后丢弃一切绘制
 *       （界面在、世界没了）。</li>
 *   <li><b>读回坐标必须按纹理真实尺寸裁剪</b>：{@code gpuWidth/gpuHeight} 是逻辑尺寸，
 *       自定义尺寸或窗口缩放期间可能大于真实纹理，直接用会触发
 *       {@code GL_INVALID_VALUE: The y values exceeds ...} 刷屏。</li>
 * </ul>
 */
@Mixin(value = StencilFormFramebuffer.class, remap = false)
public abstract class StencilPickFixMixin121
{
    @Shadow
    private int index;

    @Shadow
    private int gpuWidth;

    @Shadow
    private int gpuHeight;

    @Shadow
    private GpuTexture colorTexture;

    @Unique
    private boolean bbspp$projectionInstalled;

    @Unique
    private static boolean bbspp$loggedDirectError;

    @Unique
    private static FloatBuffer bbspp$directBuffer;

    /** picking pass 开始前的 scissor 状态，unbind 时原样恢复。 */
    @Unique
    private static final ThreadLocal<Boolean> bbspp$savedScissor = new ThreadLocal<>();

    /**
     * 补回 1.20.1 picking pass 里的 {@code _disableScissorTest()}。
     */
    @Inject(method = "apply()V", at = @At("HEAD"), remap = false, require = 0)
    private void bbspp$disableScissor(CallbackInfo ci)
    {
        boolean was = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        bbspp$savedScissor.set(was);

        if (was)
        {
            GlStateManager._disableScissorTest();
        }
    }

    @Inject(method = "apply()V", at = @At("RETURN"), remap = false, require = 0)
    private void bbspp$bindPreviewProjection(CallbackInfo ci)
    {
        Matrix4f projection = ModelPreviewRenderer.PROJECTION;

        if (projection != null && BBSPickerRenderer.getProjectionOverride() == null)
        {
            BBSPickerRenderer.setProjectionOverride(projection);
            this.bbspp$projectionInstalled = true;
        }
    }

    @Inject(method = "unbind()V", at = @At("RETURN"), remap = false, require = 0)
    private void bbspp$restorePreviewProjection(CallbackInfo ci)
    {
        if (this.bbspp$projectionInstalled)
        {
            this.bbspp$projectionInstalled = false;
            BBSPickerRenderer.setProjectionOverride(null);
        }

        Boolean was = bbspp$savedScissor.get();

        if (was != null)
        {
            bbspp$savedScissor.remove();

            if (was)
            {
                GlStateManager._enableScissorTest();
            }
        }
    }

    /**
     * FBO 读回一个 ID 都没有时，直接从纹理读回窗口重新解码。
     *
     * <p>只在 {@code index == 0}（确实没选中）时跑，选中了就不做任何读回 —— 正常游玩下
     * 这个兜底几乎不会执行。</p>
     */
    @Inject(method = "pick(IIII)V", at = @At("RETURN"), remap = false, require = 0)
    private void bbspp$fallbackPick(int x, int y, int radius, int handleMax, CallbackInfo ci)
    {
        if (this.index != 0 || radius <= 0)
        {
            return;
        }

        if (this.colorTexture instanceof GlTexture glTexture)
        {
            bbspp$readTextureDirect(glTexture, x, y, radius, handleMax);
        }
    }

    /**
     * 直接从 GPU 纹理对象读回拾取区域（不绑定任何 FBO），解码出中心 id / 最近的 gizmo handle id，
     * 与原 {@code pick(IIII)} 的解码规则一致。成功时写回 {@link #index}。
     *
     * <p><b>坐标必须按纹理真实尺寸裁剪</b>：{@code gpuWidth/gpuHeight} 是逻辑尺寸，
     * 自定义尺寸/窗口缩放期间可能大于真实纹理，直接用会把读回区域推出纹理边界。</p>
     */
    @Unique
    private void bbspp$readTextureDirect(GlTexture texture, int x, int y, int radius, int handleMax)
    {
        int realWidth = texture.getWidth(0);
        int realHeight = texture.getHeight(0);

        if (realWidth <= 0 || realHeight <= 0)
        {
            return;
        }

        try
        {
            /* 逻辑坐标 → 纹理坐标：两者通常相等，尺寸切换的那一两帧才会不同。 */
            long cx = x;
            long cy = y;

            if (this.gpuWidth > 0 && this.gpuHeight > 0)
            {
                cx = x * (long) realWidth / this.gpuWidth;
                cy = y * (long) realHeight / this.gpuHeight;
            }

            cx = Math.max(0L, Math.min(cx, realWidth - 1L));
            cy = Math.max(0L, Math.min(cy, realHeight - 1L));

            long r = Math.min(radius, Math.max(realWidth, realHeight));

            int x0 = (int) Math.max(0L, cx - r);
            int y0 = (int) Math.max(0L, cy - r);
            int x1 = (int) Math.min(realWidth - 1L, cx + r);
            int y1 = (int) Math.min(realHeight - 1L, cy + r);
            int w = x1 - x0 + 1;
            int h = y1 - y0 + 1;

            if (w <= 0 || h <= 0)
            {
                return;
            }

            int needed = w * h * 4;

            if (bbspp$directBuffer == null || bbspp$directBuffer.capacity() < needed)
            {
                bbspp$directBuffer = BufferUtils.createFloatBuffer(needed);
            }

            FloatBuffer fb = bbspp$directBuffer;
            fb.clear();

            GL45.glGetTextureSubImage(texture.getGlId(), 0, x0, y0, 0, w, h, 1, GL11.GL_RGBA, GL11.GL_FLOAT, fb);

            /* 读回失败时把错误码吞掉，避免它混进 MC 的 GL 调试回调里变成噪声。 */
            GL11.glGetError();

            int centerId = 0;
            int nearestHandle = 0;
            long nearestDist = Long.MAX_VALUE;
            long radiusSq = (long) radius * radius;
            int icx = (int) cx;
            int icy = (int) cy;

            for (int py = 0; py < h; py++)
            {
                for (int px = 0; px < w; px++)
                {
                    int base = (py * w + px) * 4;

                    if ((int) (fb.get(base + 3) * 255F) < 1)
                    {
                        continue;
                    }

                    int id = (int) (fb.get(base) * 255F)
                        | ((int) (fb.get(base + 1) * 255F) << 8)
                        | ((int) (fb.get(base + 2) * 255F) << 16);
                    int fx = x0 + px;
                    int fy = y0 + py;

                    if (fx == icx && fy == icy)
                    {
                        centerId = id;
                    }

                    if (id >= 1 && id <= handleMax)
                    {
                        long dx = fx - icx;
                        long dy = fy - icy;
                        long dist = dx * dx + dy * dy;

                        if (dist <= radiusSq && dist < nearestDist)
                        {
                            nearestDist = dist;
                            nearestHandle = id;
                        }
                    }
                }
            }

            int resolved = nearestHandle != 0 ? nearestHandle : centerId;

            if (resolved != 0)
            {
                this.index = resolved;
            }
        }
        catch (Throwable t)
        {
            if (!bbspp$loggedDirectError)
            {
                bbspp$loggedDirectError = true;
                BBSPlusPlusMod.LOGGER.warn("[BBS++] 拾取纹理直读不可用（{}）", t.getClass().getSimpleName());
            }
        }
    }
}
