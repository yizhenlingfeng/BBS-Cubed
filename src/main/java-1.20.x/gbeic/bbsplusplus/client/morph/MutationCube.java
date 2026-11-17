package gbeic.bbsplusplus.client.morph;

import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelQuad;
import mchorse.bbs_mod.cubic.data.model.ModelVertex;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * 一个目标 Cube 的「逆向绑定」形变关系。
 *
 * <p>采用目标驱动思路：以<b>目标 Cube</b> 为基准，先把它缩放/平移成源 Cube 的形状，
 * 再随着进度恢复它自己的形状。</p>
 *
 * <p>这样做的关键好处是 <b>UV 完全不用移动</b>：顶点始终使用目标 Cube 自己的 UV，
 * 采样目标图集的原生区域，因此贴图全程保持清晰——就像缩放骨骼不会让纹理错乱一样。
 * 若反过来以源 Cube 为基准、把目标内容塞进源 UV 矩形，两个矩形宽高比不同就必须拉伸，
 * 必然发糊。</p>
 *
 * <p>姿态按「平移线性 + 旋转四元数 slerp + 缩放分量插值」求解，绝不对完整矩阵逐元素插值。
 * 遇到镜像、零缩放、剪切或不可逆矩阵时不做危险求逆，回退到逐顶点线性插值。</p>
 */
public final class MutationCube
{
    public static final int FACE_COUNT = 6;

    private static final int CORNERS = 4;
    private static final int VERTEX_STRIDE = 3;
    private static final int UV_STRIDE = 2;

    /** 判定目标 Cube 某轴尺寸是否退化到无法求缩放比的阈值。 */
    private static final float SIZE_EPSILON = 1.0E-4F;

    /**
     * 判定「已分离」的位移阈值，约 1/4096 模型单位。
     *
     * <p>取值远小于一个像素：足以让深度比较稳定分出先后，又不会产生可见位移，
     * 因此克隆体「开始绘制」的时刻在视觉上是连续的。</p>
     */
    private static final float SEPARATION_EPSILON = 1F / 4096F;

    private static final Vector3f[] LOCAL_NORMALS = {
        new Vector3f(0F, 0F, -1F), new Vector3f(1F, 0F, 0F), new Vector3f(0F, 0F, 1F),
        new Vector3f(-1F, 0F, 0F), new Vector3f(0F, 1F, 0F), new Vector3f(0F, -1F, 0F)
    };

    /** 起点：目标 Cube 被变换成源 Cube 形状时的姿态（源骨骼空间）。 */
    private final MorphMath.Trs startPose;
    /** 终点：目标 Cube 自己的姿态（源骨骼空间）。 */
    private final MorphMath.Trs endPose;

    /** 目标 Cube 的局部包围盒，顶点由它生成。 */
    private final Vector3f boxMin;
    private final Vector3f boxMax;

    private final int faceMask;
    private final int[] faceWinding = new int[FACE_COUNT];
    private final int[] cornerBits = new int[FACE_COUNT * CORNERS];

    /** 目标 Cube 自己的 UV，全程不变。 */
    private final float[] uvs = new float[FACE_COUNT * CORNERS * UV_STRIDE];

    private final boolean linear;
    private final float[] startPositions = new float[FACE_COUNT * CORNERS * VERTEX_STRIDE];
    private final float[] endPositions = new float[FACE_COUNT * CORNERS * VERTEX_STRIDE];
    private final float[] startNormals = new float[FACE_COUNT * CORNERS * VERTEX_STRIDE];
    private final float[] endNormals = new float[FACE_COUNT * CORNERS * VERTEX_STRIDE];

    /** 各面的源/目标 UV 矩形，供贴图把源内容映射进目标区域后与目标淡化。 */
    private final java.util.List<UvRegion> regions = new java.util.ArrayList<>();

    /**
     * 起点到终点的总位移量估计（平移 + 旋转弧长 + 缩放差）。
     *
     * <p>用来判断「几何是否已经真的分开」。判定基于<b>位移</b>而不是进度阈值：
     * 位移由进度连续驱动，从 0 开始增长，因此越过极小阈值的时刻本身就是连续的，
     * 不会出现「某一 tick 突然出现」的跳变。</p>
     */
    private float motion;

    private float stagger;

    private MutationCube(MorphMath.Trs startPose, MorphMath.Trs endPose,
                         Vector3f boxMin, Vector3f boxMax, int faceMask, boolean linear)
    {
        this.startPose = startPose;
        this.endPose = endPose;
        this.boxMin = boxMin;
        this.boxMax = boxMax;
        this.faceMask = faceMask;
        this.linear = linear;
    }

    /**
     * 克隆分化的错峰起始比例：越靠后的过渡体越晚开始分离。
     */
    public void setStagger(float stagger)
    {
        this.stagger = MorphMath.clamp01(stagger);
    }

    public int faceMask()
    {
        return this.faceMask;
    }

    public int winding(int face)
    {
        return this.faceWinding[face];
    }

    public java.util.List<UvRegion> regions()
    {
        return this.regions;
    }

    public boolean hasFace(int face)
    {
        return (this.faceMask & (1 << face)) != 0;
    }

    /**
     * 几何是否已经与起点姿态拉开到足以避免深度竞争的程度。
     *
     * <p>阈值取极小值（约 1/4096 模型单位，远小于一个像素），既能稳定分出深度先后，
     * 又不会产生可见位移——因此「开始绘制」这一刻在视觉上是连续的。</p>
     */
    public boolean hasSeparated(float localProgress)
    {
        return this.motion <= 0F || MorphMath.clamp01(localProgress) * this.motion > SEPARATION_EPSILON;
    }

    /**
     * 几何是否已经回到终点姿态附近（用于汇聚体提前退场）。
     */
    public boolean hasConverged(float globalProgress)
    {
        float t = MorphMath.clamp01(globalProgress);

        return this.motion <= 0F || (1F - t) * this.motion <= SEPARATION_EPSILON;
    }

    public float localProgress(float globalProgress)
    {
        float t = MorphMath.clamp01(globalProgress);

        if (this.stagger <= 0F)
        {
            return t;
        }

        return MorphMath.clamp01((t - this.stagger) / (1F - this.stagger));
    }

    /**
     * 采样到给定的输出缓冲区。UV 恒定为目标 Cube 的 UV，与进度无关。
     */
    public void sample(float progress, float[] positions, float[] normals, float[] outUvs)
    {
        float t = this.localProgress(progress);

        System.arraycopy(this.uvs, 0, outUvs, 0, this.uvs.length);

        if (this.linear)
        {
            this.sampleLinear(t, positions, normals);
        }
        else
        {
            this.sampleTrs(t, positions, normals);
        }
    }

    private void sampleTrs(float t, float[] positions, float[] normals)
    {
        MorphMath.Trs pose = this.startPose.lerp(this.endPose, t);
        Matrix3f rotationScale = pose.rotationScale();
        Matrix3f normalMatrix = MorphMath.normalMatrix(rotationScale);
        Vector3f corner = new Vector3f();
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();

        for (int face = 0; face < FACE_COUNT; face++)
        {
            if (!this.hasFace(face))
            {
                continue;
            }

            normal.set(LOCAL_NORMALS[face]);
            normalMatrix.transform(normal);
            MorphMath.safeNormalize(normal, LOCAL_NORMALS[face]);

            for (int i = 0; i < CORNERS; i++)
            {
                int faceCorner = face * CORNERS + i;
                int offset = faceCorner * VERTEX_STRIDE;

                cornerAt(this.cornerBits[faceCorner], this.boxMin, this.boxMax, corner);
                rotationScale.transform(corner);
                position.set(corner).add(pose.translation());

                if (!MorphMath.isFinite(position))
                {
                    position.set(this.startPositions[offset], this.startPositions[offset + 1], this.startPositions[offset + 2]);
                }

                positions[offset] = position.x;
                positions[offset + 1] = position.y;
                positions[offset + 2] = position.z;
                normals[offset] = normal.x;
                normals[offset + 1] = normal.y;
                normals[offset + 2] = normal.z;
            }
        }
    }

    private void sampleLinear(float t, float[] positions, float[] normals)
    {
        Vector3f value = new Vector3f();

        for (int face = 0; face < FACE_COUNT; face++)
        {
            if (!this.hasFace(face))
            {
                continue;
            }

            for (int i = 0; i < CORNERS; i++)
            {
                int faceCorner = face * CORNERS + i;
                int offset = faceCorner * VERTEX_STRIDE;

                for (int axis = 0; axis < VERTEX_STRIDE; axis++)
                {
                    float from = this.startPositions[offset + axis];
                    float to = this.endPositions[offset + axis];

                    positions[offset + axis] = from + (to - from) * t;
                }

                value.set(
                    this.startNormals[offset] + (this.endNormals[offset] - this.startNormals[offset]) * t,
                    this.startNormals[offset + 1] + (this.endNormals[offset + 1] - this.startNormals[offset + 1]) * t,
                    this.startNormals[offset + 2] + (this.endNormals[offset + 2] - this.startNormals[offset + 2]) * t
                );
                MorphMath.safeNormalize(value, LOCAL_NORMALS[face]);

                normals[offset] = value.x;
                normals[offset + 1] = value.y;
                normals[offset + 2] = value.z;
            }
        }
    }

    /**
     * 建立一条 目标 Cube 的逆向绑定形变关系。
     *
     * @param sourceCube  映射的源 Cube，只用来决定起始形状
     * @param targetCube  目标 Cube，提供 UV 与最终形状
     * @param sourceFrame 源 Cube 的 Pivot 帧（源骨骼空间）
     * @param targetFrame 目标 Cube 的 Pivot 帧，已跨骨骼反求到<b>同一源骨骼空间</b>
     */
    public static MutationCube build(ModelCube sourceCube, ModelCube targetCube,
                                     Matrix4f sourceFrame, Matrix4f targetFrame)
    {
        if (sourceCube == null || targetCube == null || sourceFrame == null || targetFrame == null)
        {
            return null;
        }

        Vector3f sourceMin = boxMin(sourceCube);
        Vector3f sourceMax = boxMax(sourceCube);
        Vector3f targetMin = boxMin(targetCube);
        Vector3f targetMax = boxMax(targetCube);
        Vector3f sourceSize = new Vector3f(sourceMax).sub(sourceMin);
        Vector3f targetSize = new Vector3f(targetMax).sub(targetMin);
        Vector3f scale = new Vector3f(
            ratio(sourceSize.x, targetSize.x),
            ratio(sourceSize.y, targetSize.y),
            ratio(sourceSize.z, targetSize.z));

        /* bind 把目标 Cube 的包围盒映射成源 Cube 的包围盒：p' = p * scale + translate。 */
        Vector3f translate = new Vector3f(
            sourceMin.x - targetMin.x * scale.x,
            sourceMin.y - targetMin.y * scale.y,
            sourceMin.z - targetMin.z * scale.z);
        Matrix4f bind = new Matrix4f().translate(translate).scale(scale);

        /* 起点 = 源 Pivot 帧 × bind：栈里已应用源骨骼矩阵，因此这里再乘上它正好得到源 Cube 的外观。 */
        Matrix4f startMatrix = new Matrix4f(sourceFrame).mul(bind);
        Matrix4f endMatrix = new Matrix4f(targetFrame);

        ModelQuad[] faces = new ModelQuad[FACE_COUNT];
        ModelQuad[] sourceFaces = new ModelQuad[FACE_COUNT];
        int faceMask = 0;

        for (ModelQuad quad : targetCube.quads)
        {
            if (quad.vertices.size() == CORNERS)
            {
                int face = classifyFace(quad.normal);

                faces[face] = quad;
                faceMask |= 1 << face;
            }
        }

        for (ModelQuad quad : sourceCube.quads)
        {
            if (quad.vertices.size() == CORNERS)
            {
                sourceFaces[classifyFace(quad.normal)] = quad;
            }
        }

        if (faceMask == 0)
        {
            return null;
        }

        boolean linear = startMatrix.determinant() < 0F || endMatrix.determinant() < 0F;
        MorphMath.Trs startPose;
        MorphMath.Trs endPose;

        try
        {
            startPose = MorphMath.decompose(startMatrix);
            endPose = MorphMath.decompose(endMatrix);
        }
        catch (IllegalArgumentException exception)
        {
            startPose = new MorphMath.Trs(new Vector3f(), new Quaternionf(), new Vector3f(1F, 1F, 1F));
            endPose = new MorphMath.Trs(new Vector3f(), new Quaternionf(), new Vector3f(1F, 1F, 1F));
            linear = true;
        }

        MutationCube cube = new MutationCube(startPose, endPose, targetMin, targetMax, faceMask, linear);
        cube.motion = motionOf(startPose, endPose, targetMin, targetMax);
        Vector3f targetCenter = new Vector3f(targetMin).add(targetMax).mul(0.5F);

        for (int face = 0; face < FACE_COUNT; face++)
        {
            ModelQuad quad = faces[face];

            if (quad == null)
            {
                continue;
            }

            cube.faceWinding[face] = windingOf(quad);

            Map<Integer, Float2> uvByBits = uvByBits(quad, targetCenter);

            for (int i = 0; i < CORNERS; i++)
            {
                int faceCorner = face * CORNERS + i;
                int bits = bitsOf(quad.vertices.get(i).vertex, targetCenter);
                int uvOffset = faceCorner * UV_STRIDE;
                Float2 uv = uvByBits.get(bits);

                cube.cornerBits[faceCorner] = bits;

                if (uv != null)
                {
                    cube.uvs[uvOffset] = uv.u;
                    cube.uvs[uvOffset + 1] = uv.v;
                }
            }

            if (linear)
            {
                writeLinearFace(cube, face, targetMin, targetMax, startMatrix, true);
                writeLinearFace(cube, face, targetMin, targetMax, endMatrix, false);
            }

            ModelQuad sourceQuad = sourceFaces[face];

            if (sourceQuad != null)
            {
                cube.regions.add(new UvRegion(
                    uvMin(sourceQuad, 0), uvMin(sourceQuad, 1), uvMax(sourceQuad, 0), uvMax(sourceQuad, 1),
                    uvMin(quad, 0), uvMin(quad, 1), uvMax(quad, 0), uvMax(quad, 1)));
            }
        }

        return cube;
    }

    /**
     * 逐顶点线性回退模式的端点位置与法线（都作用在目标 Cube 的包围盒上）。
     */
    private static void writeLinearFace(MutationCube cube, int face, Vector3f min, Vector3f max,
                                        Matrix4f matrix, boolean start)
    {
        float[] positions = start ? cube.startPositions : cube.endPositions;
        float[] normals = start ? cube.startNormals : cube.endNormals;
        Vector3f corner = new Vector3f();
        Vector3f normal = new Vector3f();
        Matrix3f normalMatrix = MorphMath.normalMatrix(new Matrix3f(matrix));

        normal.set(LOCAL_NORMALS[face]);
        normalMatrix.transform(normal);
        MorphMath.safeNormalize(normal, LOCAL_NORMALS[face]);

        for (int i = 0; i < CORNERS; i++)
        {
            int faceCorner = face * CORNERS + i;
            int offset = faceCorner * VERTEX_STRIDE;

            cornerAt(cube.cornerBits[faceCorner], min, max, corner);
            matrix.transformPosition(corner);

            positions[offset] = corner.x;
            positions[offset + 1] = corner.y;
            positions[offset + 2] = corner.z;
            normals[offset] = normal.x;
            normals[offset + 1] = normal.y;
            normals[offset + 2] = normal.z;
        }
    }

    /**
     * 估计两端姿态之间的最大位移量，作为「是否分离」判定的尺度。
     */
    private static float motionOf(MorphMath.Trs start, MorphMath.Trs end, Vector3f min, Vector3f max)
    {
        Vector3f translation = new Vector3f(end.translation()).sub(start.translation());
        float distance = translation.length();
        /* 四元数夹角：|dot| 越接近 1 越同向，夹角 = 2*acos(|dot|)。 */
        float dot = Math.abs(start.rotation().dot(end.rotation()));
        float angle = 2F * (float) java.lang.Math.acos(Math.min(1F, dot));
        float radius = new Vector3f(max).sub(min).length() * 0.5F;
        Vector3f scale = new Vector3f(end.scale()).sub(start.scale());

        return distance + Math.abs(angle) * radius + scale.length() * radius;
    }

    private static float ratio(float source, float target)
    {
        return Math.abs(target) < SIZE_EPSILON ? 1F : source / target;
    }

    private static void cornerAt(int bits, Vector3f min, Vector3f max, Vector3f out)
    {
        out.set(
            (bits & 1) == 0 ? min.x : max.x,
            (bits & 2) == 0 ? min.y : max.y,
            (bits & 4) == 0 ? min.z : max.z
        );
    }

    private static int classifyFace(Vector3f normal)
    {
        if (normal.x < -0.5F) return 3;
        if (normal.x > 0.5F) return 1;
        if (normal.y < -0.5F) return 5;
        if (normal.y > 0.5F) return 4;
        if (normal.z < -0.5F) return 0;

        return 2;
    }

    private static int bitsOf(Vector3f position, Vector3f center)
    {
        int bits = 0;

        if (position.x > center.x) bits |= 1;
        if (position.y > center.y) bits |= 2;
        if (position.z > center.z) bits |= 4;

        return bits;
    }

    private static int windingOf(ModelQuad quad)
    {
        if (quad.vertices.size() < 3)
        {
            return 0;
        }

        Vector3f a = quad.vertices.get(0).vertex;
        Vector3f b = quad.vertices.get(1).vertex;
        Vector3f c = quad.vertices.get(2).vertex;
        Vector3f cross = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));

        return cross.dot(quad.normal) < 0F ? 1 : 0;
    }

    private static Map<Integer, Float2> uvByBits(ModelQuad quad, Vector3f center)
    {
        Map<Integer, Float2> map = new HashMap<>();

        for (ModelVertex vertex : quad.vertices)
        {
            map.put(bitsOf(vertex.vertex, center), new Float2(vertex.uv.x, vertex.uv.y));
        }

        return map;
    }

    private static Vector3f boxMin(ModelCube cube)
    {
        return new Vector3f(
            (cube.origin.x - cube.inflate) * ModelMorphAdapter.MODEL_UNIT,
            (cube.origin.y - cube.inflate) * ModelMorphAdapter.MODEL_UNIT,
            (cube.origin.z - cube.inflate) * ModelMorphAdapter.MODEL_UNIT);
    }

    private static Vector3f boxMax(ModelCube cube)
    {
        return new Vector3f(
            (cube.origin.x + cube.size.x + cube.inflate) * ModelMorphAdapter.MODEL_UNIT,
            (cube.origin.y + cube.size.y + cube.inflate) * ModelMorphAdapter.MODEL_UNIT,
            (cube.origin.z + cube.size.z + cube.inflate) * ModelMorphAdapter.MODEL_UNIT);
    }

    private static float uvMin(ModelQuad quad, int axis)
    {
        float value = Float.MAX_VALUE;

        for (ModelVertex vertex : quad.vertices)
        {
            value = Math.min(value, axis == 0 ? vertex.uv.x : vertex.uv.y);
        }

        return value;
    }

    private static float uvMax(ModelQuad quad, int axis)
    {
        float value = -Float.MAX_VALUE;

        for (ModelVertex vertex : quad.vertices)
        {
            value = Math.max(value, axis == 0 ? vertex.uv.x : vertex.uv.y);
        }

        return value;
    }

    private record Float2(float u, float v)
    {
    }

    /**
     * 一面在源图集与目标图集中的归一化 UV 矩形。
     *
     * <p>贴图侧据此把源图集里该面的内容映射进目标图集的对应矩形，
     * 再与目标内容交叉淡化。这样<b>目标内容始终是原生像素（清晰）</b>，
     * 只有正在淡出的源内容会被拉伸。</p>
     */
    public record UvRegion(float su0, float sv0, float su1, float sv1,
                           float tu0, float tv0, float tu1, float tv1)
    {
    }
}
