package gbeic.bbsplusplus.mixin12111;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import gbeic.bbsplusplus.BBSPlusPlusMod;
import mchorse.bbs_mod.client.render.picker.BBSPickerRenderer;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.ModelPreviewRenderer;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.utils.Pair;
import net.minecraft.client.texture.GlTexture;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL45;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.FloatBuffer;
import java.util.Map;

/**
 * 1.21.11 面板预览里「普通左键点模型选不中骨骼」的修复与诊断。
 *
 * <p>实机排查链（2026-11-04 ~ 11-05，反编译 {@code bbs-2.7-1.21.11.jar} 逐层核对）：
 * <ul>
 *   <li>CPU 侧全正常：{@code 映射数=61, Target=20, 投影覆盖=true, picker目标=true}，
 *       索引数 2772、顶点色不透明白、UV2.x 就是骨骼索引；</li>
 *   <li>stencil 里能扫到 ID，但那批 ID 是 <b>gizmo 手柄</b>（{@code StencilMap.setup()}
 *       把 0..19 留给手柄、骨骼从 20 起）—— 模型本身一个像素都没写进纹理；</li>
 *   <li>模型走 {@code BBSPickerRenderer.draw}（颜色 <b>+ 深度</b>附件），gizmo 走
 *       {@code drawColorId}（只有颜色附件）；gizmo 能画、模型不能画，差别就在这里；</li>
 *   <li>深度线探针实测「整行 0.0000」：深度缓冲根本没被清过，{@code LEQUAL_DEPTH_TEST}
 *       下所有模型片元一律判失败。根因是本 mixin 早先那版「同步清屏」在
 *       {@code glClear} 前没有把 {@code depthMask} 置回 true（UI 流水线普遍
 *       {@code writeDepth=false}），颜色清掉了、深度一个字节没清。</li>
 * </ul>
 *
 * <p><b>当前实现</b>：清屏交还给 MC 自己的
 * {@code GlCommandEncoder.clearColorAndDepthTextures}（它内部会 {@code _depthMask(true)}
 * + {@code _colorMask(...)}，而且本来就是同步执行，不存在命令排队问题）。
 * 本 mixin 只保留三件事：pick 前禁 scissor 并恢复、装 preview 投影覆盖、
 * 以及 {@code pick} 失败时用 {@code glGetTextureSubImage} 直读纹理兜底重新解码。
 */
@Mixin(value = StencilFormFramebuffer.class, remap = false)
public abstract class StencilPickFixMixin121
{
    @Shadow
    private int index;

    @Shadow
    private Map<Integer, Pair<Form, String>> indexMap;

    @Shadow
    private int gpuWidth;

    @Shadow
    private int gpuHeight;

    @Shadow
    private GpuTexture colorTexture;

    @Shadow
    private GpuTexture depthTexture;

    /** 4 参 pick 用来 glReadPixels 的复用缓冲区。 */
    @Shadow
    private FloatBuffer pickBuffer;

    @Unique
    private boolean bbspp$projectionInstalled;

    @Unique
    private static long bbspp$lastLogMs;

    @Unique
    private static boolean bbspp$loggedBind;

    @Unique
    private static boolean bbspp$loggedDirectError;

    @Unique
    private static FloatBuffer bbspp$directBuffer;

    /** 全图扫描线复用缓冲。 */
    @Unique
    private static FloatBuffer bbspp$scanBuffer;

    /** 深度线扫描复用缓冲。 */
    @Unique
    private static FloatBuffer bbspp$depthBuffer;

    /** 读深度线用的私有 FBO（只挂 depth 附件）。 */
    @Unique
    private static int bbspp$depthFbo = -1;

    /** picking pass 开始前的 scissor 状态，unbind 时原样恢复。 */
    @Unique
    private static final ThreadLocal<Boolean> bbspp$savedScissor = new ThreadLocal<>();

    @Unique
    private static boolean bbspp$loggedScissor;

    /**
     * 补回 1.20.1 picking pass 里的 {@code _disableScissorTest()}。
     *
     * <p>1.21.11 的 {@code GlCommandEncoder.createRenderPass} 只在「需要清屏」的分支里
     * （{@code OptionalInt/OptionalDouble.isPresent()}）临时禁用 scissor；而
     * {@code BBSPickerRenderer.draw} 传的是 {@code empty()}，因为 stencil 已由 {@code apply()}
     * 清过。于是 UI 阶段仍处于启用状态的 scissor 会直接把 picker 几何裁掉 —— 表现就是
     * picker draw 每帧都在跑、参数全齐，stencil 纹理里却一个 ID 像素都没有。
     * 1.20.1 的 {@code UIPickableFormRenderer} 在 picking pass 前后显式关/开 scissor，
     * 这个移植环节在 1.21.11 丢了。
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

        if (!bbspp$loggedScissor)
        {
            bbspp$loggedScissor = true;
            BBSPlusPlusMod.LOGGER.info("[BBS++] 拾取修复：picking pass 前 scissor 原状态={}，已禁用", was);
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

        if (!bbspp$loggedBind)
        {
            bbspp$loggedBind = true;

            BBSPlusPlusMod.LOGGER.info("[BBS++] 预览拾取：透视投影={}，已绑定 picker 投影覆盖={}",
                projection != null, BBSPickerRenderer.getProjectionOverride() != null);
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

    @Inject(method = "pick(IIII)V", at = @At("RETURN"), remap = false, require = 0)
    private void bbspp$fallbackPick(int x, int y, int radius, int handleMax, CallbackInfo ci)
    {
        /* FBO 路径读回的非空像素数，用于日志对比。 */
        int fboHits = 0;
        int pixels = 0;

        if (this.pickBuffer != null)
        {
            pixels = this.pickBuffer.capacity() / 4;

            for (int i = 0; i < pixels; i++)
            {
                if (this.pickBuffer.get(i * 4 + 3) >= 1F / 255F)
                {
                    fboHits++;
                }
            }
        }

        int textureHits = -1;

        if (this.index == 0 && this.colorTexture instanceof GlTexture glTexture
            && this.gpuWidth > 0 && this.gpuHeight > 0 && radius > 0)
        {
            textureHits = bbspp$readTextureDirect(glTexture.getGlId(), x, y, radius, handleMax);
        }

        long now = System.currentTimeMillis();

        if (now - bbspp$lastLogMs < 1000L)
        {
            return;
        }

        bbspp$lastLogMs = now;

        BBSPlusPlusMod.LOGGER.info(
            "[BBS++] 拾取诊断：xy=({}, {}), radius={}, stencil={}x{}, FBO命中={}/{}, 纹理直读命中={}, index={}, 映射数={}, Target={}, 投影覆盖={}, picker目标={}, 读纹理glId={}, picker目标glId={}, 全图ID={}, 深度线={}",
            x, y, radius, this.gpuWidth, this.gpuHeight, fboHits, pixels,
            textureHits < 0 ? "跳过" : Integer.toString(textureHits), this.index,
            this.indexMap == null ? -1 : this.indexMap.size(),
            BBSPickerRenderer.getTarget(),
            BBSPickerRenderer.getProjectionOverride() != null,
            BBSPickerRenderer.getRenderTargetColor() != null,
            bbspp$glId(this.colorTexture),
            bbspp$glIdOfView(BBSPickerRenderer.getRenderTargetColor()),
            bbspp$scanTexture(),
            bbspp$scanDepth());
    }

    /**
     * 扫一行深度（y = 高度/2），统计「被写过」的像素数，并报告整行的最小/最大值。
     *
     * <p>picker 的 pipeline 是 {@code LEQUAL_DEPTH_TEST} 且 {@code isWriteDepth()=true}，
     * 所以：
     * <ul>
     *   <li>整行都是 1.0 → 深度被正确清过、但没有片元写过（片元没跑或被 discard）；</li>
     *   <li>整行都是 0.0 → 深度根本没被清过（GL 新建纹理默认 0），
     *       {@code LEQUAL} 下任何片元都会被判失败 —— 这就是 2026-11-05 定位到的真凶；</li>
     *   <li>出现中间值 → 片元确实画进去了。</li>
     * </ul>
     *
     * <p>这里必须用「真 FBO + glReadPixels」读：{@code glGetTextureSubImage} 对 DEPTH32
     * 在部分驱动上不可靠（上一轮日志里 0/2560 与 2560/2560 交替正是读回失败的表现）。
     */
    @Unique
    private String bbspp$scanDepth()
    {
        if (!(this.depthTexture instanceof GlTexture glDepth) || this.gpuWidth <= 0 || this.gpuHeight <= 0)
        {
            return "n/a";
        }

        try
        {
            int w = this.gpuWidth;

            if (bbspp$depthBuffer == null || bbspp$depthBuffer.capacity() < w)
            {
                bbspp$depthBuffer = BufferUtils.createFloatBuffer(w);
            }

            FloatBuffer fb = bbspp$depthBuffer;
            fb.clear();
            /* 先填成 -1，读回失败时不会伪装成「深度全 0」。 */
            for (int i = 0; i < w; i++)
            {
                fb.put(i, -1F);
            }

            if (bbspp$depthFbo < 0)
            {
                bbspp$depthFbo = GL30.glGenFramebuffers();
            }

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, bbspp$depthFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, glDepth.getGlId(), 0);
            GL11.glDrawBuffer(GL11.GL_NONE);
            GL11.glReadBuffer(GL11.GL_NONE);
            GL11.glReadPixels(0, this.gpuHeight / 2, w, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, fb);
            /* 走 GlStateManager 回到 0，保证引擎的 FBO 绑定缓存一致。 */
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

            int written = 0;
            int zero = 0;
            float min = Float.MAX_VALUE;
            float max = -Float.MAX_VALUE;

            for (int px = 0; px < w; px++)
            {
                float d = fb.get(px);

                if (d < 0F)
                {
                    return "读取失败（缓冲未被写入）";
                }

                min = Math.min(min, d);
                max = Math.max(max, d);

                if (d < 0.999F)
                {
                    written++;
                }

                if (d <= 0.0001F)
                {
                    zero++;
                }
            }

            String verdict = zero == w ? "（整行 0.0 = 深度没被清过！）" : written == 0 ? "（全 1.0，无片元写深度）" : "";

            return written + "/" + w + " min=" + String.format("%.4f", min) + " max=" + String.format("%.4f", max) + verdict;
        }
        catch (Throwable t)
        {
            return "读取失败:" + t.getClass().getSimpleName();
        }
    }

    /**
     * 稀疏扫描整张 stencil 纹理（5 条水平线 + 5 条垂直线），确认 ID 颜色到底有没有画进来、
     * 落在哪个范围。只在限流日志里跑一次，避免每帧同步读回。
     */
    @Unique
    private String bbspp$scanTexture()
    {
        if (!(this.colorTexture instanceof GlTexture glTexture) || this.gpuWidth <= 0 || this.gpuHeight <= 0)
        {
            return "n/a";
        }

        try
        {
            int maxLine = Math.max(this.gpuWidth, this.gpuHeight) * 4;

            if (bbspp$scanBuffer == null || bbspp$scanBuffer.capacity() < maxLine)
            {
                bbspp$scanBuffer = BufferUtils.createFloatBuffer(maxLine);
            }

            FloatBuffer fb = bbspp$scanBuffer;
            int glId = glTexture.getGlId();
            int hits = 0;
            int minX = Integer.MAX_VALUE;
            int maxX = -1;
            int minY = Integer.MAX_VALUE;
            int maxY = -1;
            int sampleId = -1;

            int[] ys = { 0, this.gpuHeight / 4, this.gpuHeight / 2, this.gpuHeight * 3 / 4, this.gpuHeight - 1 };

            for (int py : ys)
            {
                fb.clear();
                GL45.glGetTextureSubImage(glId, 0, 0, py, 0, this.gpuWidth, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, fb);

                for (int px = 0; px < this.gpuWidth; px++)
                {
                    int base = px * 4;

                    if (fb.get(base + 3) < 1F / 255F)
                    {
                        continue;
                    }

                    hits++;
                    minX = Math.min(minX, px);
                    maxX = Math.max(maxX, px);
                    minY = Math.min(minY, py);
                    maxY = Math.max(maxY, py);

                    if (sampleId < 0)
                    {
                        sampleId = (int) (fb.get(base) * 255F)
                            | ((int) (fb.get(base + 1) * 255F) << 8)
                            | ((int) (fb.get(base + 2) * 255F) << 16);
                    }
                }
            }

            int[] xs = { 0, this.gpuWidth / 4, this.gpuWidth / 2, this.gpuWidth * 3 / 4, this.gpuWidth - 1 };

            for (int px : xs)
            {
                fb.clear();
                GL45.glGetTextureSubImage(glId, 0, px, 0, 0, 1, this.gpuHeight, 1, GL11.GL_RGBA, GL11.GL_FLOAT, fb);

                for (int py = 0; py < this.gpuHeight; py++)
                {
                    int base = py * 4;

                    if (fb.get(base + 3) < 1F / 255F)
                    {
                        continue;
                    }

                    hits++;
                    minX = Math.min(minX, px);
                    maxX = Math.max(maxX, px);
                    minY = Math.min(minY, py);
                    maxY = Math.max(maxY, py);

                    if (sampleId < 0)
                    {
                        sampleId = (int) (fb.get(base) * 255F)
                            | ((int) (fb.get(base + 1) * 255F) << 8)
                            | ((int) (fb.get(base + 2) * 255F) << 16);
                    }
                }
            }

            if (hits == 0)
            {
                return "0（整张纹理无 ID 颜色）";
            }

            return hits + " 范围=[" + minX + "," + minY + "]-[" + maxX + "," + maxY + "] 示例ID=" + sampleId;
        }
        catch (Throwable t)
        {
            return "扫描失败:" + t.getClass().getSimpleName();
        }
    }

    /** 读回用的纹理与 picker 写入目标是同一张时，两个 glId 必须相等。 */
    @Unique
    private static int bbspp$glId(GpuTexture texture)
    {
        return texture instanceof GlTexture gl ? gl.getGlId() : -1;
    }

    @Unique
    private static int bbspp$glIdOfView(GpuTextureView view)
    {
        return view == null ? -1 : bbspp$glId(view.texture());
    }

    /**
     * 直接从 GPU 纹理对象读回拾取区域（不绑定任何 FBO），解码出中心 id / 最近的 gizmo handle id，
     * 与原 {@code pick(IIII)} 的解码规则一致。成功时写回 {@link #index}。
     *
     * @return 读到的非空像素数；-1 表示不支持/失败
     */
    @Unique
    private int bbspp$readTextureDirect(int glId, int x, int y, int radius, int handleMax)
    {
        try
        {
            long cx = Math.max(0L, Math.min((long) x, this.gpuWidth - 1L));
            long cy = Math.max(0L, Math.min((long) y, this.gpuHeight - 1L));
            long r = Math.min((long) radius, Math.max(this.gpuWidth, this.gpuHeight));

            int x0 = (int) Math.max(0L, cx - r);
            int y0 = (int) Math.max(0L, cy - r);
            int x1 = (int) Math.min(this.gpuWidth - 1L, cx + r);
            int y1 = (int) Math.min(this.gpuHeight - 1L, cy + r);
            int w = x1 - x0 + 1;
            int h = y1 - y0 + 1;

            if (w <= 0 || h <= 0)
            {
                return -1;
            }

            int needed = w * h * 4;

            if (bbspp$directBuffer == null || bbspp$directBuffer.capacity() < needed)
            {
                bbspp$directBuffer = BufferUtils.createFloatBuffer(needed);
            }

            FloatBuffer fb = bbspp$directBuffer;
            fb.clear();

            GL45.glGetTextureSubImage(glId, 0, x0, y0, 0, w, h, 1, GL11.GL_RGBA, GL11.GL_FLOAT, fb);

            int hits = 0;
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

                    hits++;

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

            return hits;
        }
        catch (Throwable t)
        {
            if (!bbspp$loggedDirectError)
            {
                bbspp$loggedDirectError = true;
                BBSPlusPlusMod.LOGGER.warn("[BBS++] 拾取纹理直读不可用（{}）", t.getClass().getSimpleName());
            }

            return -1;
        }
    }
}
