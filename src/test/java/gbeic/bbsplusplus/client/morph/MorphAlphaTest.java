package gbeic.bbsplusplus.client.morph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 形变贴图的 alpha 语义：平滑插值，实现真正的淡入淡出。
 *
 * <p>BBS 的模型渲染支持半透明：{@code ModelFormRenderer} 在纹理
 * {@code hasTranslucency()} 为真时，会把它送进 {@code FormTranslucentQueue}
 * 做延迟排序。因此这里可以直接产出中间 alpha，得到平滑的渐显/渐隐。</p>
 *
 * <p>历史教训：早期实现把 alpha 二值化成 0/255（cutout 思维），导致「透明 → 有纹理」
 * 的块在形变第一刻凭空出现；后来又试过 Bayer 抖动，产生明显噪点。两者都被放弃。</p>
 */
public class MorphAlphaTest
{
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    private static final int OPAQUE_RED = 0xFFFF0000;
    private static final int TRANSPARENT = 0x00000000;

    /**
     * 核心：alpha 必须随进度平滑变化，不能是 0/255 硬切。
     */
    @Test
    void alphaInterpolatesSmoothly()
    {
        int start = MorphTextures.blendPixel(TRANSPARENT, OPAQUE_RED, 0F);
        int middle = MorphTextures.blendPixel(TRANSPARENT, OPAQUE_RED, 0.5F);
        int end = MorphTextures.blendPixel(TRANSPARENT, OPAQUE_RED, 1F);

        assertEquals(0, start >>> 24, "起点应完全透明");
        assertEquals(128, middle >>> 24, 2, "中点应约为半透明");
        assertEquals(255, end >>> 24, "终点应完全不透明");
    }

    /**
     * 反向：不透明 → 透明同样平滑。
     */
    @Test
    void alphaFadesOutSmoothly()
    {
        int middle = MorphTextures.blendPixel(OPAQUE_WHITE, TRANSPARENT, 0.5F);

        assertEquals(128, middle >>> 24, 2, "淡出中点应约为半透明");
    }

    /**
     * 关键回归：源全透明时，进度 0 必须完全不可见。
     *
     * <p>这正是「形变第一刻凭空冒出带纹理的面」的成因。</p>
     */
    @Test
    void transparentSourceIsInvisibleAtProgressZero()
    {
        assertEquals(0, MorphTextures.blendPixel(TRANSPARENT, OPAQUE_RED, 0F) >>> 24);
    }

    /**
     * alpha 单调递增，不得出现回跳。
     */
    @Test
    void alphaIsMonotonic()
    {
        int previous = -1;

        for (int step = 0; step <= 32; step++)
        {
            int alpha = MorphTextures.blendPixel(TRANSPARENT, OPAQUE_RED, step / 32F) >>> 24;

            assertTrue(alpha >= previous, "alpha 应单调不减（step " + step + "）");
            previous = alpha;
        }
    }

    /**
     * 源像素全透明时不能把它的 RGB（往往是纯黑）混进来，
     * 否则半透明阶段会出现发黑的脏色。
     */
    @Test
    void transparentSourceDoesNotDarkenColour()
    {
        int blended = MorphTextures.blendPixel(TRANSPARENT, OPAQUE_WHITE, 0.5F);

        assertEquals(0xFF, (blended >> 16) & 0xFF, "红色分量应保持为目标的 255");
        assertEquals(0xFF, (blended >> 8) & 0xFF, "绿色分量应保持为目标的 255");
        assertEquals(0xFF, blended & 0xFF, "蓝色分量应保持为目标的 255");
    }

    /**
     * 不透明源仍要正常参与颜色渐变。
     */
    @Test
    void opaqueSourceStillBlendsColour()
    {
        int middle = MorphTextures.blendPixel(OPAQUE_WHITE, OPAQUE_RED, 0.5F);

        assertEquals(0xFF, (middle >> 16) & 0xFF, "红色分量应保持");
        assertEquals(128, middle & 0xFF, 2, "蓝色分量应约为一半");
    }

    /**
     * 半透明扫描：用于决定是否把纹理标记为 translucent。
     */
    @Test
    void semiTransparentScanFollowsBbsThreshold()
    {
        mchorse.bbs_mod.utils.resources.Pixels pixels =
            mchorse.bbs_mod.utils.resources.Pixels.fromSize(2, 1);

        try
        {
            java.nio.ByteBuffer buffer = pixels.getBuffer();

            /* fromSize 已把 position 推到末尾，写入前先归零。 */
            buffer.position(0);

            /* 第 1 像素全不透明，第 2 像素半透明。 */
            buffer.put((byte) 255).put((byte) 0).put((byte) 0).put((byte) 255);
            buffer.put((byte) 255).put((byte) 0).put((byte) 0).put((byte) 128);

            assertTrue(MorphTextures.hasSemiTransparent(pixels),
                "含 alpha=128 的像素应判定为半透明");
        }
        finally
        {
            pixels.delete();
        }
    }
}
