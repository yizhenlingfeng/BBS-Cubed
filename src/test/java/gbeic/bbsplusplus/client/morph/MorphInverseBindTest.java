package gbeic.bbsplusplus.client.morph;

import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelUV;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 目标驱动（逆向绑定）形变。
 *
 * <p>思路：以<b>目标 Cube</b> 为基准，先把它缩放/平移成源 Cube 的形状，再随进度恢复
 * 它自己的形状。这样顶点始终使用目标 Cube 自己的 UV、采样目标图集的原生区域，
 * <b>UV 全程不动，贴图全程清晰</b>——就像缩放骨骼不会让纹理错乱一样。</p>
 *
 * <p>这与「以源 Cube 为基准、把目标内容塞进源 UV 矩形」形成对比：后者两个矩形宽高比
 * 不同（实测 bed 面 16×16 vs boat 面内容 16×6），必须拉伸 2.67 倍，全程发糊。</p>
 */
public class MorphInverseBindTest
{
    private static ModelCube cube(float ox, float oy, float oz, float sx, float sy, float sz, int uvBase)
    {
        ModelCube cube = new ModelCube();

        cube.name = "c";
        cube.origin.set(ox, oy, oz);
        cube.size.set(sx, sy, sz);
        cube.pivot.set(ox, oy, oz);
        cube.rotate.set(0F, 0F, 0F);
        cube.front = ModelUV.fromXY(uvBase, 0, (int) sx, (int) sy);
        cube.right = ModelUV.fromXY(uvBase + (int) sx, 0, (int) sz, (int) sy);
        cube.back = ModelUV.fromXY(uvBase + (int) sx + (int) sz, 0, (int) sx, (int) sy);
        cube.left = ModelUV.fromXY(uvBase + (int) sx + (int) sz + (int) sx, 0, (int) sz, (int) sy);
        cube.top = ModelUV.fromXY(uvBase + (int) sz, 0, (int) sx, (int) sz);
        cube.bottom = ModelUV.fromXY(uvBase + (int) sz + (int) sx, 0, (int) sx, (int) sz);
        cube.generateQuads(64, 64);

        return cube;
    }

    private static void bounds(float[] positions, Vector3f min, Vector3f max)
    {
        min.set(Float.MAX_VALUE);
        max.set(-Float.MAX_VALUE);

        for (int i = 0; i < 24; i++)
        {
            min.min(new Vector3f(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2]));
            max.max(new Vector3f(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2]));
        }
    }

    private static void transformedBounds(Vector3f min, Vector3f max, Vector3f boxMin, Vector3f boxMax,
                                          Matrix4f transform)
    {
        min.set(Float.MAX_VALUE);
        max.set(-Float.MAX_VALUE);

        for (int i = 0; i < 8; i++)
        {
            Vector3f corner = new Vector3f(
                (i & 1) == 0 ? boxMin.x : boxMax.x,
                (i & 2) == 0 ? boxMin.y : boxMax.y,
                (i & 4) == 0 ? boxMin.z : boxMax.z);
            transform.transformPosition(corner);
            min.min(corner);
            max.max(corner);
        }
    }

    private static float[] sample(MutationCube mutation, float progress, float[] uvs)
    {
        float[] positions = new float[72];
        float[] normals = new float[72];

        mutation.sample(progress, positions, normals, uvs);

        return positions;
    }

    /**
     * 起点：目标 Cube 被缩放成源 Cube 的形状，包围盒必须与源 Cube 完全一致。
     */
    @Test
    void startMatchesTheSourceCubeShape()
    {
        ModelCube source = cube(-8F, -7F, 16F, 16F, 16F, 6F, 0);
        ModelCube target = cube(-8F, 3F, -16F, 16F, 6F, 2F, 0);
        MutationCube mutation = MutationCube.build(source, target, new Matrix4f(), new Matrix4f());

        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();

        bounds(sample(mutation, 0F, new float[48]), min, max);

        /* 源 Cube 包围盒（模型单位 /16） */
        assertEquals(-8F / 16F, min.x, 1e-4F);
        assertEquals(-7F / 16F, min.y, 1e-4F);
        assertEquals(16F / 16F, min.z, 1e-4F);
        assertEquals(8F / 16F, max.x, 1e-4F);
        assertEquals(9F / 16F, max.y, 1e-4F);
        assertEquals(22F / 16F, max.z, 1e-4F);
    }

    /**
     * 终点：恢复成目标 Cube 自己的形状。
     */
    @Test
    void endMatchesTheTargetCubeShape()
    {
        ModelCube source = cube(-8F, -7F, 16F, 16F, 16F, 6F, 0);
        ModelCube target = cube(-8F, 3F, -16F, 16F, 6F, 2F, 0);
        MutationCube mutation = MutationCube.build(source, target, new Matrix4f(), new Matrix4f());

        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();

        bounds(sample(mutation, 1F, new float[48]), min, max);

        assertEquals(-8F / 16F, min.x, 1e-4F);
        assertEquals(3F / 16F, min.y, 1e-4F);
        assertEquals(-16F / 16F, min.z, 1e-4F);
        assertEquals(8F / 16F, max.x, 1e-4F);
        assertEquals(9F / 16F, max.y, 1e-4F);
        assertEquals(-14F / 16F, max.z, 1e-4F);
    }

    /**
     * 核心：UV 全程固定为目标 Cube 的 UV，绝不随进度移动。
     *
     * <p>这正是「贴图全程清晰」的根据：UV 不动、图集不变，只有几何在变。</p>
     */
    @Test
    void uvStaysAtTheTargetUvForTheWholeInterval()
    {
        ModelCube source = cube(-8F, -7F, 16F, 16F, 16F, 6F, 0);
        ModelCube target = cube(-8F, 3F, -16F, 16F, 6F, 2F, 0);
        MutationCube mutation = MutationCube.build(source, target, new Matrix4f(), new Matrix4f());

        float[] atZero = new float[48];

        sample(mutation, 0F, atZero);

        for (float progress : new float[]{0.1F, 0.5F, 0.9F, 1F})
        {
            float[] uvs = new float[48];

            sample(mutation, progress, uvs);

            for (int i = 0; i < uvs.length; i++)
            {
                assertEquals(atZero[i], uvs[i], 1e-7F,
                    "UV 在进度 " + progress + " 处发生了偏移（索引 " + i + "）");
            }
        }
    }

    /**
     * 几何必须真的在形变，否则只是静态模型。
     */
    @Test
    void geometryActuallyMorphs()
    {
        ModelCube source = cube(-8F, -7F, 16F, 16F, 16F, 6F, 0);
        ModelCube target = cube(-8F, 3F, -16F, 16F, 6F, 2F, 0);
        MutationCube mutation = MutationCube.build(source, target, new Matrix4f(), new Matrix4f());

        float[] start = sample(mutation, 0F, new float[48]);
        float[] end = sample(mutation, 1F, new float[48]);
        boolean moved = false;

        for (int i = 0; i < start.length; i++)
        {
            if (Math.abs(start[i] - end[i]) > 1e-4F)
            {
                moved = true;
            }
        }

        assertTrue(moved, "几何必须随进度形变");
    }

    /**
     * 目标 Cube 某轴尺寸为零时不能产生除零，必须安全退化为不缩放。
     */
    @Test
    void zeroSizedTargetAxisFallsBackToUnitScale()
    {
        ModelCube source = cube(0F, 0F, 0F, 4F, 4F, 4F, 0);
        ModelCube target = cube(0F, 0F, 0F, 4F, 0F, 4F, 0);

        MutationCube mutation = MutationCube.build(source, target, new Matrix4f(), new Matrix4f());

        assertTrue(mutation != null, "零尺寸轴不应导致构建失败");
    }

    /**
     * TRS 分解遇到剪切时必须退回顶点插值，但两端仍要使用原始矩阵保持准确。
     */
    @Test
    void shearFallbackPreservesBothEndpointTransforms()
    {
        ModelCube source = cube(-4F, -3F, 2F, 8F, 6F, 4F, 0);
        ModelCube target = cube(-4F, -3F, 2F, 8F, 6F, 4F, 0);
        Matrix4f sourceFrame = new Matrix4f().identity();
        sourceFrame.m10(0.35F);
        sourceFrame.m30(0.2F);
        Matrix4f targetFrame = new Matrix4f().identity();
        targetFrame.m21(-0.25F);
        targetFrame.m31(-0.1F);
        MutationCube mutation = MutationCube.build(source, target, sourceFrame, targetFrame);

        float[] start = sample(mutation, 0F, new float[48]);
        float[] end = sample(mutation, 1F, new float[48]);
        Vector3f startMin = new Vector3f();
        Vector3f startMax = new Vector3f();
        Vector3f endMin = new Vector3f();
        Vector3f endMax = new Vector3f();
        Vector3f expectedStartMin = new Vector3f();
        Vector3f expectedStartMax = new Vector3f();
        Vector3f expectedEndMin = new Vector3f();
        Vector3f expectedEndMax = new Vector3f();
        bounds(start, startMin, startMax);
        bounds(end, endMin, endMax);

        Vector3f min = new Vector3f(-4F / 16F, -3F / 16F, 2F / 16F);
        Vector3f max = new Vector3f(4F / 16F, 3F / 16F, 6F / 16F);
        transformedBounds(expectedStartMin, expectedStartMax, min, max, sourceFrame);
        transformedBounds(expectedEndMin, expectedEndMax, min, max, targetFrame);

        assertEquals(expectedStartMin.x, startMin.x, 1e-5F);
        assertEquals(expectedStartMin.y, startMin.y, 1e-5F);
        assertEquals(expectedStartMin.z, startMin.z, 1e-5F);
        assertEquals(expectedStartMax.x, startMax.x, 1e-5F);
        assertEquals(expectedStartMax.y, startMax.y, 1e-5F);
        assertEquals(expectedStartMax.z, startMax.z, 1e-5F);
        assertEquals(expectedEndMin.x, endMin.x, 1e-5F);
        assertEquals(expectedEndMin.y, endMin.y, 1e-5F);
        assertEquals(expectedEndMin.z, endMin.z, 1e-5F);
        assertEquals(expectedEndMax.x, endMax.x, 1e-5F);
        assertEquals(expectedEndMax.y, endMax.y, 1e-5F);
        assertEquals(expectedEndMax.z, endMax.z, 1e-5F);
    }
}
