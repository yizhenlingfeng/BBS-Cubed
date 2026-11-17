package gbeic.bbsplusplus.client.morph;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 形变画布：源内容映射进目标 UV 区域。
 *
 * <p>回归背景：过渡体的顶点使用<b>目标 Cube 的 UV</b>。早期实现在进度接近 0 时返回
 * <b>原始源图集</b>，于是用「目标 UV」去采源图集上完全无关的位置——现场表现就是
 * 源模型帧之后连续几刻出现严重错乱的贴图。</p>
 *
 * <p>正确做法是始终使用「源内容已映射进目标区域」的画布：进度 0 时它恰好等于源模型外观，
 * 因为源内容就摆在与目标 UV 对应的矩形里。</p>
 */
public class MorphTextureCanvasTest
{
    /** 编码坐标的假图集。 */
    private static int[] pattern(int width, int height)
    {
        int[] colors = new int[width * height];

        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                colors[y * width + x] = 0xFF000000 | (x << 8) | y;
            }
        }

        return colors;
    }

    private static int[] canvas(List<MutationCube.UvRegion> regions, int[] source, int sw, int sh, int tw, int th)
    {
        return MorphTextures.remapSourceInto(regions, source, sw, sh, tw, th);
    }

    /**
     * 核心：源内容必须出现在<b>目标</b>矩形里，因为几何用目标 UV 采样。
     */
    @Test
    void sourceContentLandsInTheTargetRectangle()
    {
        int sw = 64;
        int sh = 64;
        int tw = 128;
        int th = 64;
        int[] source = pattern(sw, sh);
        /* 源矩形：左上 8x8；目标矩形：目标图集中部 16x16 */
        MutationCube.UvRegion region = new MutationCube.UvRegion(
            0F, 0F, 8F / sw, 8F / sh,
            32F / tw, 16F / th, 48F / tw, 32F / th);

        int[] result = canvas(List.of(region), source, sw, sh, tw, th);

        /* 目标矩形左上角 (32,16) 应取到源矩形左上角 (0,0) 的内容 */
        assertEquals(source[0], result[16 * tw + 32]);
    }

    /**
     * 目标矩形内的内容必须来自该面的源矩形，而不是底图的近似缩放。
     *
     * <p>底图（源图集整体缩放）只用于覆盖未映射区域；已映射的面必须被逐面精确搬入，
     * 否则形变时贴图会错位。这里用与底图明显不同的专属色验证逐面映射确实生效。</p>
     */
    @Test
    void targetRectangleUsesTheFaceSpecificSourceContent()
    {
        int sw = 64;
        int sh = 64;
        int tw = 128;
        int th = 64;
        /* 源图集整体为绿色，仅该面矩形内为红色。 */
        int[] source = new int[sw * sh];

        for (int y = 0; y < sh; y++)
        {
            for (int x = 0; x < sw; x++)
            {
                boolean inRect = x >= 8 && x < 16 && y >= 8 && y < 16;

                source[y * sw + x] = inRect ? 0xFFFF0000 : 0xFF00FF00;
            }
        }

        MutationCube.UvRegion region = new MutationCube.UvRegion(
            8F / sw, 8F / sh, 16F / sw, 16F / sh,
            32F / tw, 16F / th, 48F / tw, 32F / th);

        int[] result = canvas(List.of(region), source, sw, sh, tw, th);

        /* 目标矩形左上角必须取到源矩形的专属红色，而不是底图缩放出来的绿色。 */
        assertEquals(0xFFFF0000, result[16 * tw + 32], "目标矩形应使用该面的源内容");
        assertEquals(0xFFFF0000, result[31 * tw + 47], "目标矩形右下角也应是该面内容");
    }

    /**
     * 画布尺寸必须等于目标图集尺寸——几何采样用的是目标 UV。
     */
    @Test
    void canvasMatchesTargetAtlasSize()
    {
        int[] source = pattern(64, 64);
        int[] result = canvas(List.of(), source, 64, 64, 128, 64);

        assertEquals(128 * 64, result.length);
    }

    /**
     * 退化区域必须被跳过而不是越界写入。
     */
    @Test
    void degenerateRegionIsSkipped()
    {
        int[] source = pattern(16, 16);
        MutationCube.UvRegion flat = new MutationCube.UvRegion(
            0F, 0F, 0F, 0F, 0.5F, 0F, 0.5F, 0F);

        int[] result = canvas(List.of(flat), source, 16, 16, 16, 16);

        assertEquals(16 * 16, result.length);
    }

    /**
     * 无区域信息时也要产出可用的目标尺寸画布（近似缩放），不能返回空。
     */
    @Test
    void missingRegionsStillProduceAUsableCanvas()
    {
        int[] source = pattern(64, 64);
        int[] result = canvas(null, source, 64, 64, 128, 64);

        assertEquals(128 * 64, result.length);

        boolean anyOpaque = false;

        for (int color : result)
        {
            if ((color >>> 24) != 0)
            {
                anyOpaque = true;
            }
        }

        assertEquals(true, anyOpaque, "画布不应全透明");
    }
}
