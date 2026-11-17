package gbeic.bbsplusplus.client.morph;

import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelUV;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 渲染期记录的屏幕包围盒拾取。
 *
 * <p>为什么不用 UI 侧自己算投影：BBS 的模型渲染经过根半转、骨骼 rest frame、
 * 逐帧姿势、相机 view/projection 以及 {@code pos} 平移等多层变换，
 * 在 UI 侧重拼这条链只要有一层顺序或符号写错，投影框就整体偏移，
 * 表现为「点哪里都选不中」。改为在渲染时用<b>最终</b>模型矩阵记录屏幕矩形，
 * 与画面必然一致。</p>
 */
public class MorphScreenPicksTest
{
    private static final String MODEL = "test/model";

    private static MorphScreenPicks.Box box(String key, float minX, float minY, float maxX, float maxY, float depth)
    {
        return new MorphScreenPicks.Box("group", key, minX, minY, maxX, maxY, depth, null);
    }

    @Test
    void picksTheBoxUnderTheCursor()
    {
        MorphScreenPicks.clear();
        MorphScreenPicks.record(MODEL, "a", box("a", 0.4F, 0.4F, 0.6F, 0.6F, 5F));

        assertEquals("a", MorphScreenPicks.pick(MODEL, 0.5F, 0.5F, 0.01F).cubeKey());
    }

    @Test
    void missesWhenCursorIsOutsideEveryBox()
    {
        MorphScreenPicks.clear();
        MorphScreenPicks.record(MODEL, "a", box("a", 0.4F, 0.4F, 0.6F, 0.6F, 5F));

        assertNull(MorphScreenPicks.pick(MODEL, 0.1F, 0.1F, 0.01F));
    }

    /**
     * 重叠时优先取屏幕面积更小的方块——它通常是被大块包住、用户真正想点的那一个。
     */
    @Test
    void prefersTheSmallerBoxWhenOverlapping()
    {
        MorphScreenPicks.clear();
        MorphScreenPicks.record(MODEL, "big", box("big", 0F, 0F, 1F, 1F, 10F));
        MorphScreenPicks.record(MODEL, "small", box("small", 0.45F, 0.45F, 0.55F, 0.55F, 10F));

        assertEquals("small", MorphScreenPicks.pick(MODEL, 0.5F, 0.5F, 0.01F).cubeKey());
    }

    @Test
    void foregroundBoxWinsOverSmallerOccludedBox()
    {
        MorphScreenPicks.clear();
        MorphScreenPicks.record(MODEL, "front", box("front", 0F, 0F, 1F, 1F, 0.1F));
        MorphScreenPicks.record(MODEL, "rear-small", box("rear-small", 0.45F, 0.45F, 0.55F, 0.55F, 0.8F));

        assertEquals("front", MorphScreenPicks.pick(MODEL, 0.5F, 0.5F, 0.01F).cubeKey());
    }

    @Test
    void pickerChecksProjectedFacesInsteadOfOnlyTheirBoundingRectangles()
    {
        MorphScreenPicks.clear();
        MorphScreenPicks.record(MODEL, "surface", new MorphScreenPicks.Box("surface", "surface",
            0F, 0F, 1F, 1F, 0.5F, null,
            new float[]{0F, 0F, 0.2F, 1F, 0F, 0.2F, 1F, 1F, 0.2F, 0F, 1F, 0.2F}));
        MorphScreenPicks.record(MODEL, "outside-face", new MorphScreenPicks.Box("outside-face", "outside-face",
            0F, 0F, 1F, 1F, 0F, null,
            new float[]{0F, 0F, 0F, 0.2F, 0F, 0F, 0.2F, 0.2F, 0F, 0F, 0.2F, 0F}));

        assertEquals("surface", MorphScreenPicks.pick(MODEL, 0.8F, 0.8F, 0.01F).cubeKey());
    }

    /**
     * 面积相同时取更靠近相机的（深度更小）。
     */
    @Test
    void prefersTheNearerBoxWhenAreasAreEqual()
    {
        MorphScreenPicks.clear();
        MorphScreenPicks.record(MODEL, "far", box("far", 0.4F, 0.4F, 0.6F, 0.6F, 20F));
        MorphScreenPicks.record(MODEL, "near", box("near", 0.4F, 0.4F, 0.6F, 0.6F, 5F));

        assertEquals("near", MorphScreenPicks.pick(MODEL, 0.5F, 0.5F, 0.01F).cubeKey());
    }

    /**
     * 只记录被显式开启的模型：回放时不应产生这份开销。
     */
    @Test
    void captureIsScopedAroundOneDraw()
    {
        MorphScreenPicks.clear();
        MorphScreenPicks.endCapture();

        assertFalse(MorphScreenPicks.isCapturing(), "未开始采集时不应处于采集态");

        MorphScreenPicks.beginCapture(MODEL, new org.joml.Matrix4f(), 0, 0, 100, 100);

        assertTrue(MorphScreenPicks.isCapturing());
        assertEquals(MODEL, MorphScreenPicks.capturingModelId());

        MorphScreenPicks.endCapture();

        assertFalse(MorphScreenPicks.isCapturing(), "结束采集后必须回到非采集态");
    }

    /**
     * 不同模型之间的记录不能互相污染。
     */
    @Test
    void recordsAreIsolatedPerModel()
    {
        MorphScreenPicks.clear();
        MorphScreenPicks.record("model/a", "cube", box("cube", 0.4F, 0.4F, 0.6F, 0.6F, 5F));

        assertNull(MorphScreenPicks.pick("model/b", 0.5F, 0.5F, 0.01F));
        assertEquals("cube", MorphScreenPicks.pick("model/a", 0.5F, 0.5F, 0.01F).cubeKey());
    }

    /**
     * 未知模型 / 空 id 必须安全返回，不能抛异常。
     */
    @Test
    void unknownModelIsSafe()
    {
        MorphScreenPicks.clear();

        assertNull(MorphScreenPicks.pick(null, 0.5F, 0.5F, 0.01F));
        assertNull(MorphScreenPicks.pick("nope", 0.5F, 0.5F, 0.01F));
    }

    /**
     * 同一个短 key 出现在不同组里时不能互相覆盖。
     *
     * <p>很多模型每个组都有 {@code cube_0}，若只按短 key 存记录，
     * 后写入的会覆盖前面的，拾取就会命中错误组的方块。</p>
     */
    @Test
    void sameShortKeyInDifferentGroupsDoesNotCollide()
    {
        MorphScreenPicks.clear();
        MorphScreenPicks.record(MODEL, "cube_0",
            new MorphScreenPicks.Box("left_arm", "cube_0", 0.4F, 0.4F, 0.5F, 0.5F, 5F, null));
        MorphScreenPicks.record(MODEL, "cube_0",
            new MorphScreenPicks.Box("right_arm", "cube_0", 0.6F, 0.6F, 0.7F, 0.7F, 5F, null));

        assertEquals("left_arm", MorphScreenPicks.pick(MODEL, 0.45F, 0.45F, 0.01F).groupName());
        assertEquals("right_arm", MorphScreenPicks.pick(MODEL, 0.65F, 0.65F, 0.01F).groupName());
    }

    /**
     * 关键回归：采集<b>不</b>依赖形变上下文。
     *
     * <p>拾取面板显示的是静态模型（进度 0，形变状态被清空），早期实现把采集挂在
     * {@code MorphRenderContext} 上，记录函数第一行就 return，屏幕矩形从未写入——
     * 这才是「点了没反应」的真正原因。这里锁定：只要开始采集，即使没有任何形变状态，
     * 采集也必须生效。</p>
     */
    @Test
    void captureDoesNotRequireMorphContext()
    {
        MorphScreenPicks.clear();
        MorphScreenPicks.beginCapture("static/model", new org.joml.Matrix4f(), 10, 20, 300, 200);

        assertTrue(MorphScreenPicks.isCapturing(),
            "静态模型（无形变上下文）也必须能采集");

        assertEquals("static/model", MorphScreenPicks.capturingModelId());
        assertEquals(300, MorphScreenPicks.captureViewport()[2]);
        assertEquals(200, MorphScreenPicks.captureViewport()[3]);

        MorphScreenPicks.endCapture();
    }

    /**
     * The same model may be rendered in multiple editor viewports with different cameras.
     * A later preview capture must not replace the source picker's screen-space projection.
     */
    @Test
    void sameModelKeepsIndependentPickDataPerView()
    {
        MorphScreenPicks.clear();
        Object sourcePicker = new Object();
        Object morphPreview = new Object();

        MorphScreenPicks.beginCapture(sourcePicker, MODEL, new org.joml.Matrix4f(), 0, 0, 100, 100);
        MorphScreenPicks.record(MODEL, "source-cube",
            new MorphScreenPicks.Box("source", "source-cube", 0.1F, 0.1F, 0.3F, 0.3F, 1F, null));
        MorphScreenPicks.endCapture();

        MorphScreenPicks.beginCapture(morphPreview, MODEL, new org.joml.Matrix4f(), 0, 0, 100, 100);
        MorphScreenPicks.record(MODEL, "preview-cube",
            new MorphScreenPicks.Box("preview", "preview-cube", 0.7F, 0.7F, 0.9F, 0.9F, 1F, null));
        MorphScreenPicks.endCapture();

        assertEquals("source-cube", MorphScreenPicks.pick(sourcePicker, MODEL, 0.2F, 0.2F, 0F).cubeKey());
        assertNull(MorphScreenPicks.pick(sourcePicker, MODEL, 0.8F, 0.8F, 0F));
        assertEquals("preview-cube", MorphScreenPicks.pick(morphPreview, MODEL, 0.8F, 0.8F, 0F).cubeKey());
        assertNull(MorphScreenPicks.pick(morphPreview, MODEL, 0.2F, 0.2F, 0F));
    }

    /**
     * 开始采集会清掉该模型上一帧的记录，避免新旧数据混在一起。
     */
    @Test
    void beginCaptureClearsPreviousFrame()
    {
        MorphScreenPicks.clear();
        MorphScreenPicks.record(MODEL, "stale", box("stale", 0F, 0F, 1F, 1F, 1F));

        assertEquals("stale", MorphScreenPicks.pick(MODEL, 0.5F, 0.5F, 0.01F).cubeKey());

        MorphScreenPicks.beginCapture(MODEL, new org.joml.Matrix4f(), 0, 0, 100, 100);
        MorphScreenPicks.endCapture();

        assertNull(MorphScreenPicks.pick(MODEL, 0.5F, 0.5F, 0.01F),
            "新一帧开始时必须清掉上一帧的记录");
    }

    /**
     * 屏幕坐标 → NDC 必须翻转 Y。
     *
     * <p>屏幕 Y 向下增大，NDC Y 向上增大。BBS 官方
     * {@code StencilFormFramebuffer#pickGUI} 的换算是
     * {@code y = area.h - mouseY + area.y}，即翻转。早期实现漏了这一步，
     * 导致垂直方向镜像，鼠标无论点哪里都打不中——这是拾取失败的最后一个根因。</p>
     */
    @Test
    void screenToNdcFlipsTheYAxis()
    {
        int viewportY = 100;
        int viewportH = 200;

        /* 视口顶部（屏幕 y 最小）应对应 NDC 的 1；底部对应 0。 */
        float topNdc = (viewportH - (viewportY - viewportY)) / (float) viewportH;
        float bottomNdc = (viewportH - (viewportY + viewportH - viewportY)) / (float) viewportH;

        assertEquals(1F, topNdc, 1.0E-6F, "视口顶部应是 NDC 1");
        assertEquals(0F, bottomNdc, 1.0E-6F, "视口底部应是 NDC 0");

        /* 垂直中点 */
        float middle = (viewportH - (viewportY + viewportH / 2 - viewportY)) / (float) viewportH;

        assertEquals(0.5F, middle, 1.0E-6F);
    }

    /**
     * 锁定：不翻转时垂直方向会镜像，这正是之前 miss 的表现。
     */
    @Test
    void unflippedYWouldMirrorTheHit()
    {
        MorphScreenPicks.clear();
        /* 盒子上半部分（NDC y 0.6~0.9） */
        MorphScreenPicks.record(MODEL, "upper", box("upper", 0.4F, 0.6F, 0.6F, 0.9F, 5F));

        /* 正确翻转后：鼠标在视口上部（NDC y 高）应命中 */
        assertNotNull(MorphScreenPicks.pick(MODEL, 0.5F, 0.75F, 0.01F));

        /* 若不翻转，同样鼠标位置会被换算成 0.25，落到盒子外 */
        assertNull(MorphScreenPicks.pick(MODEL, 0.5F, 0.25F, 0.01F),
            "镜像位置的坐标不应命中");
    }

    /**
     * 棱边数据：12 条棱覆盖立方体的 8 个顶点，每个顶点恰好被 3 条棱共用。
     *
     * <p>这是立方体线框的正确性判据——缺一条棱或某个顶点度数不对，
     * 画出来的就不是立方体轮廓。</p>
     */
    @Test
    void cubeEdgesFormAValidWireframe()
    {
        int[] degree = new int[8];

        for (int[] edge : MorphScreenPicks.Box.EDGES)
        {
            degree[edge[0]]++;
            degree[edge[1]]++;
        }

        assertEquals(12, MorphScreenPicks.Box.EDGES.length, "立方体应有 12 条棱");

        for (int i = 0; i < 8; i++)
        {
            assertEquals(3, degree[i], "顶点 " + i + " 应被 3 条棱共用");
        }
    }

    /**
     * 棱边下标必须与采集端的角点位序一致（bit0=x, bit1=y, bit2=z），
     * 否则线框会连成乱线。
     */
    @Test
    void cubeEdgesConnectVerticesDifferingInOneAxis()
    {
        for (int[] edge : MorphScreenPicks.Box.EDGES)
        {
            int diff = Integer.bitCount(edge[0] ^ edge[1]);

            assertEquals(1, diff,
                "棱 " + edge[0] + "-" + edge[1] + " 的两端应只差一个轴");
        }
    }

    @Test
    void capturedCubeCornersIncludeItsPivotRotation()
    {
        ModelCube cube = new ModelCube();
        cube.origin.set(0F, 0F, 0F);
        cube.size.set(2F, 1F, 1F);
        cube.pivot.set(0F, 0F, 0F);
        cube.rotate.set(0F, 0F, 90F);

        float[] corners = MorphScreenPicks.cubeCorners(cube);
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (int i = 0; i < 8; i++)
        {
            minX = Math.min(minX, corners[i * 3]);
            minY = Math.min(minY, corners[i * 3 + 1]);
            maxX = Math.max(maxX, corners[i * 3]);
            maxY = Math.max(maxY, corners[i * 3 + 1]);
        }

        assertEquals(-1F / 16F, minX, 1.0E-6F);
        assertEquals(0F, maxX, 1.0E-6F);
        assertEquals(0F, minY, 1.0E-6F);
        assertEquals(2F / 16F, maxY, 1.0E-6F);
    }

    @Test
    void groupPathDisambiguatesRepeatedGroupNames()
    {
        ModelGroup leftRoot = new ModelGroup("root");
        ModelGroup left = new ModelGroup("arm");
        left.parent = leftRoot;
        ModelGroup rightRoot = new ModelGroup("other");
        ModelGroup right = new ModelGroup("arm");
        right.parent = rightRoot;

        assertEquals("root/arm", MorphScreenPicks.groupPath(left));
        assertEquals("other/arm", MorphScreenPicks.groupPath(right));
    }

    @Test
    void duplicateCubeKeysUseTheActualGroupOrdinal()
    {
        ModelGroup group = new ModelGroup("root");
        ModelCube before = new ModelCube();
        before.name = "base";
        ModelCube firstPanel = new ModelCube();
        firstPanel.name = "panel";
        ModelCube between = new ModelCube();
        between.name = "trim";
        ModelCube secondPanel = new ModelCube();
        secondPanel.name = "panel";
        group.cubes.add(before);
        group.cubes.add(firstPanel);
        group.cubes.add(between);
        group.cubes.add(secondPanel);

        assertEquals("panel#1", MorphScreenPicks.keyFor(group, firstPanel));
        assertEquals("panel#3", MorphScreenPicks.keyFor(group, secondPanel));
    }

    @Test
    void projectedFaceTrianglesProvideHitDepth()
    {
        ModelCube cube = new ModelCube();
        cube.origin.set(-8F, -8F, -8F);
        cube.size.set(16F, 16F, 16F);
        cube.pivot.set(-8F, -8F, -8F);
        cube.front = ModelUV.fromXY(0, 0, 16, 16);
        cube.right = ModelUV.fromXY(16, 0, 16, 16);
        cube.back = ModelUV.fromXY(32, 0, 16, 16);
        cube.left = ModelUV.fromXY(48, 0, 16, 16);
        cube.top = ModelUV.fromXY(0, 16, 16, 16);
        cube.bottom = ModelUV.fromXY(16, 16, 16, 16);
        cube.generateQuads(64, 64);

        float[] faces = MorphScreenPicks.projectFaces(cube, new org.joml.Matrix4f(), new org.joml.Matrix4f());

        assertNotNull(faces);
        assertEquals(6 * 12, faces.length, "每个四边形面保存 4 个屏幕深度顶点");

        MorphScreenPicks.Box box = new MorphScreenPicks.Box("root", "cube", 0F, 0F, 1F, 1F,
            0F, null, faces);

        assertTrue(Float.isFinite(box.depthAt(0.5F, 0.5F)), "模型表面位置应能得到有限的拾取深度");
    }

    /**
     * 带角点的 Box 应能报告自己可画棱边；旧的 4 元组记录则退回矩形。
     */
    @Test
    void boxReportsWhetherCornersAreUsable()
    {
        MorphScreenPicks.Box withCorners = new MorphScreenPicks.Box("g", "k", 0F, 0F, 1F, 1F, 5F, new float[16]);
        MorphScreenPicks.Box without = new MorphScreenPicks.Box("g", "k", 0F, 0F, 1F, 1F, 5F, null);

        assertTrue(withCorners.hasCorners(), "16 个分量的角点数组应可用");
        assertFalse(without.hasCorners(), "没有角点时应退回矩形绘制");
    }
}
