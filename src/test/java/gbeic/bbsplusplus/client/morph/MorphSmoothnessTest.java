package gbeic.bbsplusplus.client.morph;

import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelUV;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 形变的视觉连续性。
 *
 * <p>回归背景：源模型帧之后的第一刻显得突兀。原因有两处——</p>
 * <ol>
 *   <li>克隆体用固定进度阈值判定是否绘制，会在某一 tick 从「不画」直接跳到「画」；</li>
 *   <li>源内容被压进比它更小的目标 UV 矩形（实测 16×16 → 16×6），分辨率不可逆丢失，
 *       首帧几何虽已恢复成源形状，画面却明显变糊。</li>
 * </ol>
 *
 * <p>对应修法：可见性改用<b>几何位移</b>判定（位移从 0 连续增长），画布按目标图集
 * 超采样存储以保住源细节。</p>
 */
public class MorphSmoothnessTest
{
    private static ModelCube cube(float ox, float oy, float oz, float sx, float sy, float sz,
                                  int uv, int textureWidth, int textureHeight)
    {
        ModelCube cube = new ModelCube();

        cube.name = "c";
        cube.origin.set(ox, oy, oz);
        cube.size.set(sx, sy, sz);
        cube.pivot.set(ox, oy, oz);
        cube.rotate.set(0F, 0F, 0F);
        cube.front = ModelUV.fromXY(uv, 0, (int) sx, (int) sy);
        cube.right = ModelUV.fromXY(uv + (int) sx, 0, (int) sz, (int) sy);
        cube.back = ModelUV.fromXY(uv + (int) sx + (int) sz, 0, (int) sx, (int) sy);
        cube.left = ModelUV.fromXY(uv + (int) sx + (int) sz + (int) sx, 0, (int) sz, (int) sy);
        cube.top = ModelUV.fromXY(uv + (int) sz, 0, (int) sx, (int) sz);
        cube.bottom = ModelUV.fromXY(uv + (int) sz + (int) sx, 0, (int) sx, (int) sz);
        cube.generateQuads(textureWidth, textureHeight);

        return cube;
    }

    /**
     * 起点：几何尚未分离，不应绘制（否则多个重合共面几何会深度竞争）。
     */
    @Test
    void cloneIsHiddenWhileStillCoincident()
    {
        ModelCube source = cube(-8F, -7F, 16F, 16F, 16F, 6F, 0, 64, 64);
        ModelCube target = cube(-8F, 3F, -16F, 16F, 6F, 2F, 0, 128, 64);
        MutationCube mutation = MutationCube.build(source, target, new Matrix4f(), new Matrix4f());

        assertFalse(mutation.hasSeparated(0F), "起点尚未分离时不应绘制");
    }

    /**
     * 关键：一旦开始分离就必须立刻可见，且不得在之后的进度里再次消失。
     *
     * <p>用位移判定后，「开始绘制」发生在位移越过极小阈值处——阈值远小于一个像素，
     * 因此这一刻在视觉上是连续的，不会出现突然冒出的观感。</p>
     */
    @Test
    void cloneBecomesVisibleEarlyAndStaysVisible()
    {
        ModelCube source = cube(-8F, -7F, 16F, 16F, 16F, 6F, 0, 64, 64);
        ModelCube target = cube(-8F, 3F, -16F, 16F, 6F, 2F, 0, 128, 64);
        MutationCube mutation = MutationCube.build(source, target, new Matrix4f(), new Matrix4f());

        boolean visibleOnce = false;
        float firstVisible = -1F;

        for (int step = 1; step <= 100; step++)
        {
            float progress = step / 100F;

            if (mutation.hasSeparated(progress))
            {
                if (!visibleOnce)
                {
                    visibleOnce = true;
                    firstVisible = progress;
                }
            }
            else if (visibleOnce)
            {
                throw new AssertionError("克隆体在进度 " + progress + " 处消失，可见性必须单调");
            }
        }

        assertTrue(visibleOnce, "克隆体必须在某个进度开始可见");
        assertTrue(firstVisible <= 0.05F,
            "克隆体应当很早就可见（实际 " + firstVisible + "），否则前段几乎看不出分化");
    }

    /**
     * 汇聚体到达终点前必须已经收敛，由真实目标模型接管。
     */
    @Test
    void convergenceCompletesBeforeTheEnd()
    {
        ModelCube source = cube(-8F, -7F, 16F, 16F, 16F, 6F, 0, 64, 64);
        ModelCube target = cube(-8F, 3F, -16F, 16F, 6F, 2F, 0, 128, 64);
        MutationCube mutation = MutationCube.build(source, target, new Matrix4f(), new Matrix4f());

        assertTrue(mutation.hasConverged(1F), "终点必须判定为已收敛");
        assertFalse(mutation.hasConverged(0F), "起点不应判定为已收敛");
    }

    /**
     * N:1 的次要过渡体在接近汇聚时必须停止绘制，避免终点处共面几何互相闪烁。
     */
    @Test
    void secondaryConvergenceSlotsAreSuppressedNearTheEnd()
    {
        ModelCube source = cube(-4F, -4F, -4F, 8F, 8F, 8F, 0, 64, 64);
        ModelCube target = cube(-4F, -4F, -4F, 8F, 8F, 8F, 0, 64, 64);
        Matrix4f targetFrame = new Matrix4f().translation(2F, 0F, 0F);
        MutationCube mutation = MutationCube.build(source, target, new Matrix4f(), targetFrame);
        MorphPlan.Slot secondary = new MorphPlan.Slot(mutation, false, 2, 1);

        assertTrue(secondary.visibleAt(0.97F), "分离阶段的次要过渡体仍应可见");
        assertFalse(secondary.visibleAt(0.98F), "98% 之后应停止绘制次要过渡体");
    }

    /**
     * 画布超采样：目标图集放大后，源面的每个像素都应能在画布上找到对应，
     * 从而不再出现「首帧变糊」。
     */
    @Test
    void supersampledCanvasPreservesSourceDetail()
    {
        /* 源面 16x16，目标面 16x6 —— 实测的 bed/boat 比例，纵向会被压缩。 */
        int sourceWidth = 16;
        int sourceHeight = 16;
        int targetWidth = 16;
        int targetHeight = 6;
        int supersample = 4;
        int canvasWidth = targetWidth * supersample;
        int canvasHeight = targetHeight * supersample;

        /* 源内容每行一个不同颜色，便于检测纵向细节是否被保留。 */
        int[] source = new int[sourceWidth * sourceHeight];

        for (int y = 0; y < sourceHeight; y++)
        {
            for (int x = 0; x < sourceWidth; x++)
            {
                source[y * sourceWidth + x] = 0xFF000000 | (y << 8);
            }
        }

        MutationCube.UvRegion region = new MutationCube.UvRegion(
            0F, 0F, 1F, 1F,
            0F, 0F, 1F, 1F);

        int[] canvas = MorphTextures.remapSourceInto(
            java.util.List.of(region), source, sourceWidth, sourceHeight, canvasWidth, canvasHeight);

        /* 画布上不同行应当保留源图的不同行，而不是被压成少数几行重复。 */
        java.util.Set<Integer> rows = new java.util.HashSet<>();

        for (int y = 0; y < canvasHeight; y++)
        {
            rows.add(canvas[y * canvasWidth]);
        }

        assertTrue(rows.size() >= sourceHeight,
            "超采样画布应保留源面的全部 " + sourceHeight + " 行细节，实际只有 " + rows.size() + " 种");
    }
}
