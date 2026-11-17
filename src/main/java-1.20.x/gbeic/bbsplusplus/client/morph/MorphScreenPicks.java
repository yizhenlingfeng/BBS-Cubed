package gbeic.bbsplusplus.client.morph;

import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelQuad;
import mchorse.bbs_mod.cubic.data.model.ModelVertex;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.LinkedHashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 记录每个 Cube 在<b>实际渲染时</b>投影到屏幕上的矩形。
 *
 * <p>为什么不在 UI 侧自己算投影：BBS 的模型渲染经过多层变换
 * （{@code ModelBindingGeometry} 的根半转、骨骼 rest frame、逐帧姿势矩阵、
 * 相机 view/projection、以及 {@code pos} 平移）。在 UI 侧重新拼这套链，
 * 只要任何一层的顺序或符号写错，投影框就会整体偏移，表现为「点哪里都选不中」。</p>
 *
 * <p>因此在渲染过程中直接投影 Cube 的实际面三角形，并保存角点用于高亮线框。
 * 拾取先用矩形做快速筛选，再用表面三角形和深度确定鼠标下的可见 Cube。</p>
 *
 * <p>记录按视口和模型 id 分组，并在每次该视口绘制前清空，避免同模型的其它预览覆盖拾取数据。</p>
 */
public final class MorphScreenPicks
{
    /**
     * 一个 Cube 在屏幕上的投影结果。
     *
     * <p>{@code corners} 保存 8 个角点的屏幕坐标（0..1 归一化，Y 向下），
     * 用于画出<b>真实的三维棱边</b>——只画外接矩形无法让用户判断立方体的实际大小与朝向。</p>
     *
     * <p>角点顺序按 bit0=x, bit1=y, bit2=z；{@code faces} 每面保存四个 (screenX, screenY,
     * ndcDepth) 顶点，用于把拾取限定到实际表面，而不是只靠外接矩形。</p>
     */
    public record Box(String groupName, String cubeKey, float minX, float minY, float maxX, float maxY,
                      float depth, float[] corners, float[] faces)
    {
        public Box(String groupName, String cubeKey, float minX, float minY, float maxX, float maxY,
                   float depth, float[] corners)
        {
            this(groupName, cubeKey, minX, minY, maxX, maxY, depth, corners, null);
        }

        public boolean contains(float x, float y, float padding)
        {
            return x >= this.minX - padding && x <= this.maxX + padding
                && y >= this.minY - padding && y <= this.maxY + padding;
        }

        /** 该屏幕点下的最近表面深度；旧矩形记录退回到整 Cube 的深度。 */
        public float depthAt(float x, float y)
        {
            if (this.faces == null || this.faces.length < 12 || this.faces.length % 12 != 0)
            {
                return this.depth;
            }

            float nearest = Float.POSITIVE_INFINITY;

            for (int i = 0; i + 11 < this.faces.length; i += 12)
            {
                nearest = Math.min(nearest, triangleDepth(this.faces, i, i + 3, i + 6, x, y));
                nearest = Math.min(nearest, triangleDepth(this.faces, i, i + 6, i + 9, x, y));
            }

            return nearest;
        }

        private static float triangleDepth(float[] vertices, int a, int b, int c, float x, float y)
        {
            float x0 = vertices[a];
            float y0 = vertices[a + 1];
            float z0 = vertices[a + 2];
            float x1 = vertices[b];
            float y1 = vertices[b + 1];
            float z1 = vertices[b + 2];
            float x2 = vertices[c];
            float y2 = vertices[c + 1];
            float z2 = vertices[c + 2];
            float denominator = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2);

            if (Math.abs(denominator) < 1.0E-9F)
            {
                return Float.POSITIVE_INFINITY;
            }

            float wa = ((y1 - y2) * (x - x2) + (x2 - x1) * (y - y2)) / denominator;
            float wb = ((y2 - y0) * (x - x2) + (x0 - x2) * (y - y2)) / denominator;
            float wc = 1F - wa - wb;

            return wa >= -1.0E-5F && wb >= -1.0E-5F && wc >= -1.0E-5F
                ? wa * z0 + wb * z1 + wc * z2 : Float.POSITIVE_INFINITY;
        }

        /**
         * 屏幕面积（归一化单位），仅用于深度相同或近似相同的重叠面片间打破平局。
         * 不能钳到常数，否则较小 Cube 的优先级无法稳定判定。
         */
        /** 12 条棱，按角点位序（x=1, y=2, z=4）连线。 */
        public static final int[][] EDGES = {
            {0, 1}, {2, 3}, {4, 5}, {6, 7},
            {0, 2}, {1, 3}, {4, 6}, {5, 7},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };

        public boolean hasCorners()
        {
            return this.corners != null && this.corners.length == 16;
        }

        public float area()
        {
            return Math.max(1.0E-6F, (this.maxX - this.minX) * (this.maxY - this.minY));
        }
    }

    /**
     * Screen projections are view-specific: the same model can be drawn in the source picker and the
     * morph preview with different cameras, sizes, and poses during one frame.
     */
    private static final Map<Object, Map<String, Map<String, Box>>> BY_VIEW = new WeakHashMap<>();
    private static final Object LEGACY_VIEW = new Object();

    /** 诊断计数：renderCube 实际被调用的次数，以及 key 生成失败次数。 */
    private static int captureCalls;
    private static int keyFailures;

    /**
     * 当前正在采集的模型 id（线程内）。
     *
     * <p>刻意<b>不</b>依赖 {@code MorphRenderContext}：拾取面板显示的是静态模型
     * （进度 0，形变状态被清空），根本没有形变上下文。早期实现把采集挂在形变上下文上，
     * 于是记录函数第一行就 return，屏幕矩形从未被写入——这就是「点了没反应」的真正原因。</p>
     */
    private static final ThreadLocal<String> CAPTURING = new ThreadLocal<>();
    private static final ThreadLocal<Object> CAPTURING_VIEW = new ThreadLocal<>();
    private static final ThreadLocal<IdentityHashMap<ModelGroup, String>> CAPTURE_GROUP_PATHS =
        ThreadLocal.withInitial(IdentityHashMap::new);
    private static final ThreadLocal<IdentityHashMap<ModelGroup, IdentityHashMap<ModelCube, String>>> CAPTURE_CUBE_KEYS =
        ThreadLocal.withInitial(IdentityHashMap::new);

    /**
     * 采集期间使用的<b>投影</b>矩阵。
     *
     * <p>只存 projection：BBS 的 MatrixStack 在 renderUserModel 阶段已包含 view
     * 与相机平移，再乘一次 view 会导致投影全错。</p>
     */
    private static final ThreadLocal<org.joml.Matrix4f> CAPTURE_MATRIX = new ThreadLocal<>();

    /** 采集期间用于换算 NDC 的视口。 */
    private static final ThreadLocal<int[]> CAPTURE_VIEWPORT = new ThreadLocal<>();

    private MorphScreenPicks()
    {
    }

    /**
     * 生成 Cube 经自身 Pivot 旋转后的 8 个模型空间角点。
     *
     * <p>{@code renderCube} 的 HEAD 注入发生在 BBS 应用 Cube Pivot 与旋转之前，
     * 所以采集端必须显式补上这一步，投影范围才能与实际绘制的 Cube 一致。</p>
     */
    public static float[] cubeCorners(ModelCube cube)
    {
        if (cube == null)
        {
            return null;
        }

        float unit = ModelMorphAdapter.MODEL_UNIT;
        float minX = (cube.origin.x - cube.inflate) * unit;
        float minY = (cube.origin.y - cube.inflate) * unit;
        float minZ = (cube.origin.z - cube.inflate) * unit;
        float maxX = (cube.origin.x + cube.size.x + cube.inflate) * unit;
        float maxY = (cube.origin.y + cube.size.y + cube.inflate) * unit;
        float maxZ = (cube.origin.z + cube.size.z + cube.inflate) * unit;
        Matrix4f cubeFrame = MorphMath.cubePivotFrame(cube.pivot, cube.rotate);
        float[] result = new float[24];

        for (int i = 0; i < 8; i++)
        {
            Vector3f corner = new Vector3f(
                (i & 1) == 0 ? minX : maxX,
                (i & 2) == 0 ? minY : maxY,
                (i & 4) == 0 ? minZ : maxZ);
            cubeFrame.transformPosition(corner);
            result[i * 3] = corner.x;
            result[i * 3 + 1] = corner.y;
            result[i * 3 + 2] = corner.z;
        }

        return result;
    }

    /**
     * 投影 Cube 的真实面三角形。MatrixStack 已含骨骼、视图和相机变换；这里补 Cube Pivot/旋转。
     * 返回值每 12 个 float 描述一个面的四个 (x, y, ndcDepth) 顶点；三角面复制末顶点。
     */
    public static float[] projectFaces(ModelCube cube, Matrix4f model, Matrix4f projection)
    {
        if (cube == null || model == null || projection == null || cube.quads == null)
        {
            return null;
        }

        Matrix4f cubeToClip = new Matrix4f(model).mul(MorphMath.cubePivotFrame(cube.pivot, cube.rotate));
        float[] projected = new float[cube.quads.size() * 12];
        float[] faceVertices = new float[12];
        int count = 0;
        Vector4f clip = new Vector4f();

        for (ModelQuad quad : cube.quads)
        {
            if (quad == null || (quad.vertices.size() != 3 && quad.vertices.size() != 4))
            {
                continue;
            }

            boolean inFront = true;

            for (int i = 0; i < 4; i++)
            {
                ModelVertex vertex = quad.vertices.get(Math.min(i, quad.vertices.size() - 1));
                clip.set(vertex.vertex.x, vertex.vertex.y, vertex.vertex.z, 1F);
                cubeToClip.transform(clip);
                projection.transform(clip);

                if (clip.w <= 1.0E-6F || !Float.isFinite(clip.w))
                {
                    inFront = false;
                    break;
                }

                float inverseW = 1F / clip.w;
                faceVertices[i * 3] = clip.x * inverseW * 0.5F + 0.5F;
                faceVertices[i * 3 + 1] = 1F - (clip.y * inverseW * 0.5F + 0.5F);
                faceVertices[i * 3 + 2] = clip.z * inverseW;
            }

            if (!inFront)
            {
                continue;
            }

            System.arraycopy(faceVertices, 0, projected, count, 12);
            count += 12;
        }

        if (count == 0)
        {
            return null;
        }

        return java.util.Arrays.copyOf(projected, count);
    }

    /** 渲染器侧也要保存完整骨骼路径；组名可能在不同父分支下重复。 */
    public static String groupPath(Object group)
    {
        if (!(group instanceof ModelGroup modelGroup))
        {
            return null;
        }

        if (isCapturing())
        {
            return CAPTURE_GROUP_PATHS.get().computeIfAbsent(modelGroup, MorphScreenPicks::computeGroupPath);
        }

        return computeGroupPath(modelGroup);
    }

    private static String computeGroupPath(ModelGroup modelGroup)
    {
        Deque<String> segments = new ArrayDeque<>();

        for (ModelGroup current = modelGroup; current != null; current = current.parent)
        {
            if (current.id != null && !current.id.isEmpty())
            {
                segments.addFirst(current.id);
            }
        }

        return segments.isEmpty() ? null : String.join("/", segments);
    }

    /**
     * 开始记录某个模型的一帧。
     */
    /**
     * 开始采集：UI 在绘制某个模型<b>之前</b>调用。
     *
     * <p>之后该模型每次 {@code renderCube} 都会记录一个屏幕矩形，直到
     * {@link #endCapture()}。用「围绕本次绘制调用」而不是「长期标记某个模型」，
     * 可以避免世界渲染、其它视口或回放时产生无用记录。</p>
     */
    public static void beginCapture(String modelId, org.joml.Matrix4f viewProjection,
                                    int x, int y, int width, int height)
    {
        beginCapture(LEGACY_VIEW, modelId, viewProjection, x, y, width, height);
    }

    /** Begin collecting projected boxes for one concrete render view. */
    public static void beginCapture(Object viewId, String modelId, org.joml.Matrix4f viewProjection,
                                    int x, int y, int width, int height)
    {
        if (modelId == null || modelId.isEmpty() || viewProjection == null)
        {
            CAPTURING.remove();
            CAPTURING_VIEW.remove();
            CAPTURE_MATRIX.remove();
            CAPTURE_VIEWPORT.remove();
            CAPTURE_GROUP_PATHS.remove();
            CAPTURE_CUBE_KEYS.remove();

            return;
        }

        if (viewId == null)
        {
            viewId = LEGACY_VIEW;
        }

        boxesForWrite(viewId, modelId).clear();

        captureCalls = 0;
        keyFailures = 0;

        CAPTURING.set(modelId);
        CAPTURING_VIEW.set(viewId);
        CAPTURE_MATRIX.set(new org.joml.Matrix4f(viewProjection));
        CAPTURE_VIEWPORT.set(new int[] {x, y, width, height});
        CAPTURE_GROUP_PATHS.get().clear();
        CAPTURE_CUBE_KEYS.get().clear();
    }

    public static void endCapture()
    {
        CAPTURING.remove();
        CAPTURING_VIEW.remove();
        CAPTURE_MATRIX.remove();
        CAPTURE_VIEWPORT.remove();
        CAPTURE_GROUP_PATHS.remove();
        CAPTURE_CUBE_KEYS.remove();
    }

    /** 当前是否处于采集状态。 */
    public static boolean isCapturing()
    {
        return CAPTURING.get() != null;
    }

    public static String capturingModelId()
    {
        return CAPTURING.get();
    }

    public static org.joml.Matrix4f captureProjection()
    {
        return CAPTURE_MATRIX.get();
    }

    public static int[] captureViewport()
    {
        return CAPTURE_VIEWPORT.get();
    }

    /**
     * 记录一个 Cube 的屏幕矩形。
     */
    public static void record(String modelId, String cubeKey, Box box)
    {
        if (modelId == null || modelId.isEmpty() || cubeKey == null || box == null)
        {
            return;
        }

        /* 同一个短 key 可能出现在不同骨骼路径里，因此用「完整路径|短key」作为记录键。 */
        Object viewId = CAPTURING_VIEW.get();

        if (viewId == null)
        {
            viewId = LEGACY_VIEW;
        }

        boxesForWrite(viewId, modelId)
            .put(box.groupName() + "|" + cubeKey, box);
    }

    public static Map<String, Box> boxes(String modelId)
    {
        return boxes(LEGACY_VIEW, modelId);
    }

    public static Map<String, Box> boxes(Object viewId, String modelId)
    {
        if (modelId == null || modelId.isEmpty())
        {
            return Map.of();
        }

        Map<String, Map<String, Box>> byModel = BY_VIEW.get(viewId == null ? LEGACY_VIEW : viewId);

        return byModel == null ? Map.of() : byModel.getOrDefault(modelId, Map.of());
    }

    private static Map<String, Box> boxesForWrite(Object viewId, String modelId)
    {
        return BY_VIEW.computeIfAbsent(viewId, key -> new LinkedHashMap<>())
            .computeIfAbsent(modelId, key -> new LinkedHashMap<>());
    }

    /**
     * 在屏幕坐标处找出命中的 Cube。
     *
     * <p>先按光栅化表面的深度选前景 Cube，深度相同时再优先选屏幕面积较小的 Cube。</p>
     */
    public static Box pick(String modelId, float x, float y, float padding)
    {
        return pick(LEGACY_VIEW, modelId, x, y, padding);
    }

    public static Box pick(Object viewId, String modelId, float x, float y, float padding)
    {
        Box best = null;
        float bestDepth = Float.MAX_VALUE;
        float bestArea = Float.MAX_VALUE;

        for (Box box : boxes(viewId, modelId).values())
        {
            if (!box.contains(x, y, padding))
            {
                continue;
            }

            float depth = box.depthAt(x, y);
            float area = box.area();

            if (!Float.isFinite(depth))
            {
                continue;
            }

            if (depth < bestDepth - 1.0E-5F
                || (Math.abs(depth - bestDepth) <= 1.0E-5F && area < bestArea - 1.0E-9F))
            {
                best = box;
                bestDepth = depth;
                bestArea = area;
            }
        }

        return best;
    }

    /**
     * 生成 Cube 的<b>短 key</b>（不含组路径前缀）。
     *
     * <p>为什么不用完整路径：骨架侧用的是嵌套路径
     * （如 {@code anchor/anchor_low/low_body/body/left_arm}），而渲染器只拿得到
     * {@code ModelGroup.id}（单段名 {@code left_arm}）。两边前缀必然不同，
     * 直接拼完整 key 永远匹配不上——这正是「点了没反应」的原因。</p>
     *
     * <p>改为记录短 key，由 UI 侧用「组名 + 短 key」在自己的骨架里反查唯一 Cube。</p>
     *
     *
     * <p>渲染器只拿到 {@code ModelGroup} 与 {@code ModelCube}，没有骨架快照，
     * 因此这里按与 {@code ModelMorphAdapter} 相同的规则复算。</p>
     *
     * <p>关键：两边的规则必须逐字一致，否则拾取到的 key 在列表里查不到，
     * 表现就是「点了没反应」。规则是——</p>
     * <ul>
     *   <li>无名 Cube：{@code groupPath + ":#" + 组内序号}</li>
     *   <li>有名字且组内唯一：{@code groupPath + ":" + name}（<b>不带</b>后缀）</li>
     *   <li>有名字但组内重名：{@code groupPath + ":" + name + "#" + 组内序号}</li>
     * </ul>
     */
    public static String keyFor(Object group, Object cube)
    {
        if (!(group instanceof mchorse.bbs_mod.cubic.data.model.ModelGroup modelGroup)
            || !(cube instanceof mchorse.bbs_mod.cubic.data.model.ModelCube modelCube))
        {
            return null;
        }

        if (isCapturing())
        {
            return CAPTURE_CUBE_KEYS.get().computeIfAbsent(modelGroup, MorphScreenPicks::buildCubeKeys)
                .get(modelCube);
        }

        return buildCubeKeys(modelGroup).get(modelCube);
    }

    private static IdentityHashMap<ModelCube, String> buildCubeKeys(ModelGroup group)
    {
        Map<String, Integer> nameCounts = new HashMap<>();

        for (ModelCube cube : group.cubes)
        {
            String name = cube.name == null ? "" : cube.name;

            if (!name.isEmpty())
            {
                nameCounts.merge(name, 1, Integer::sum);
            }
        }

        IdentityHashMap<ModelCube, String> keys = new IdentityHashMap<>();

        for (int i = 0; i < group.cubes.size(); i++)
        {
            ModelCube cube = group.cubes.get(i);
            String name = cube.name == null ? "" : cube.name;
            String key = name.isEmpty() ? "#" + i
                : name + (nameCounts.getOrDefault(name, 0) > 1 ? "#" + i : "");

            keys.put(cube, key);
        }

        return keys;
    }

    /**
     * 诊断用：列出某模型所有盒子的归一化范围。
     */
    public static String describe(String modelId)
    {
        return describe(LEGACY_VIEW, modelId);
    }

    public static String describe(Object viewId, String modelId)
    {
        StringBuilder builder = new StringBuilder();

        for (Map.Entry<String, Box> entry : boxes(viewId, modelId).entrySet())
        {
            Box box = entry.getValue();

            builder.append(entry.getKey())
                .append("[x ").append(String.format("%.2f", box.minX()))
                .append("..").append(String.format("%.2f", box.maxX()))
                .append(", y ").append(String.format("%.2f", box.minY()))
                .append("..").append(String.format("%.2f", box.maxY()))
                .append("] ");
        }

        return builder.length() == 0 ? "(empty)" : builder.toString();
    }

    public static int captureCalls()
    {
        return captureCalls;
    }

    public static int keyFailures()
    {
        return keyFailures;
    }

    /** 由渲染器在每次采集尝试时调用，用于区分「没调用」与「key 生成失败」。 */
    public static void countCaptureCall(boolean keyOk)
    {
        captureCalls++;

        if (!keyOk)
        {
            keyFailures++;
        }
    }

    public static void clear()
    {
        BY_VIEW.clear();
        CAPTURING.remove();
        CAPTURING_VIEW.remove();
        CAPTURE_MATRIX.remove();
        CAPTURE_VIEWPORT.remove();
        CAPTURE_GROUP_PATHS.remove();
        CAPTURE_CUBE_KEYS.remove();
    }
}
