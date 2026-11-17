package gbeic.bbsplusplus.client.morph;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Model Morph 的几何数学工具。
 *
 * <p>这里的「安全反求 / 安全分解」沿用 Bone Anchor（MIT）{@code AnchorMath} 的防护思路：
 * 只在矩阵可逆且非退化时求逆，分解时明确拒绝零缩放、剪切与非有限值，
 * 绝不为了使用逆矩阵而强行对不可逆矩阵求逆。Bone Anchor 的 MIT 许可全文见
 * {@code LICENSES/MIT-bone-anchor.txt}。</p>
 *
 * <p>旋转插值一律使用四元数最短路径 slerp，绝不对完整矩阵逐元素插值。</p>
 */
public final class MorphMath
{
    /** 判定矩阵不可逆的行列式阈值。 */
    public static final float SINGULAR_EPSILON = 1.0E-8F;

    /** 判定缩放退化的分量阈值。 */
    public static final float SCALE_EPSILON = 1.0E-6F;

    /** 判定剪切的分量点积阈值。 */
    public static final float SHEAR_EPSILON = 1.0E-4F;

    private MorphMath()
    {
    }

    public static float clamp01(float value)
    {
        return Float.isFinite(value) ? Math.max(0F, Math.min(1F, value)) : 0F;
    }

    public static boolean isFinite(Vector3f value)
    {
        return value != null && Float.isFinite(value.x) && Float.isFinite(value.y) && Float.isFinite(value.z);
    }

    public static boolean isFinite(Quaternionf value)
    {
        return value != null
            && Float.isFinite(value.x) && Float.isFinite(value.y)
            && Float.isFinite(value.z) && Float.isFinite(value.w);
    }

    public static boolean isFinite(Matrix4f matrix)
    {
        if (matrix == null)
        {
            return false;
        }

        for (int column = 0; column < 4; column++)
        {
            for (int row = 0; row < 4; row++)
            {
                if (!Float.isFinite(matrix.get(column, row)))
                {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 安全求逆：非有限或行列式接近零时抛异常，由调用方回退到「不做形变」的安全路径。
     */
    public static Matrix4f inverse(Matrix4f matrix)
    {
        float determinant = matrix != null && isFinite(matrix) ? matrix.determinant() : Float.NaN;

        if (!Float.isFinite(determinant) || Math.abs(determinant) < SINGULAR_EPSILON)
        {
            throw new IllegalArgumentException("Model Morph frame is non-finite or singular");
        }

        return new Matrix4f(matrix).invert();
    }

    public static Matrix4f inverseOrNull(Matrix4f matrix)
    {
        try
        {
            return inverse(matrix);
        }
        catch (IllegalArgumentException exception)
        {
            return null;
        }
    }

    /**
     * 把仿射矩阵分解为平移 / 旋转 / 缩放。
     *
     * <p>镜像（负行列式）按 BBS {@code Transform.fromMatrix} 的约定保留在 X 轴上；
     * 剪切与零缩放直接拒绝，交给调用方回退到逐顶点线性插值。</p>
     */
    public static Trs decompose(Matrix4f matrix)
    {
        if (!isFinite(matrix))
        {
            throw new IllegalArgumentException("Model Morph transform is non-finite");
        }

        Vector3f x = matrix.getColumn(0, new Vector3f());
        Vector3f y = matrix.getColumn(1, new Vector3f());
        Vector3f z = matrix.getColumn(2, new Vector3f());
        Vector3f scale = new Vector3f(x.length(), y.length(), z.length());
        float determinant = matrix.determinant();

        boolean degenerate = !isFinite(scale)
            || scale.x < SCALE_EPSILON || scale.y < SCALE_EPSILON || scale.z < SCALE_EPSILON
            || Math.abs(determinant) < SINGULAR_EPSILON
            || Math.abs(matrix.m03()) > SCALE_EPSILON
            || Math.abs(matrix.m13()) > SCALE_EPSILON
            || Math.abs(matrix.m23()) > SCALE_EPSILON
            || Math.abs(matrix.m33() - 1F) > SCALE_EPSILON;

        if (degenerate)
        {
            throw new IllegalArgumentException("Model Morph transform has zero scale or invalid matrix");
        }

        if (determinant < 0F)
        {
            scale.x = -scale.x;
        }

        x.div(scale.x);
        y.div(scale.y);
        z.div(scale.z);

        if (Math.abs(x.dot(y)) > SHEAR_EPSILON || Math.abs(x.dot(z)) > SHEAR_EPSILON || Math.abs(y.dot(z)) > SHEAR_EPSILON)
        {
            throw new IllegalArgumentException("Model Morph transform contains unsupported shear");
        }

        Matrix3f basis = new Matrix3f().setColumn(0, x).setColumn(1, y).setColumn(2, z);
        Quaternionf rotation = new Quaternionf().setFromNormalized(basis);

        if (!isFinite(rotation))
        {
            throw new IllegalArgumentException("Model Morph rotation is non-finite");
        }

        return new Trs(matrix.getTranslation(new Vector3f()), rotation, scale);
    }

    public static Matrix4f compose(Trs pose)
    {
        return new Matrix4f().translation(pose.translation()).rotate(pose.rotation()).scale(pose.scale());
    }

    /**
     * 四元数最短路径插值：避免跨过 180° 时绕远路旋转。
     */
    public static Quaternionf slerp(Quaternionf from, Quaternionf to, float progress)
    {
        Quaternionf start = new Quaternionf(from).normalize();
        Quaternionf end = new Quaternionf(to).normalize();

        if (start.dot(end) < 0F)
        {
            end.set(-end.x, -end.y, -end.z, -end.w);
        }

        return start.slerp(end, clamp01(progress));
    }

    /**
     * 法线矩阵：旋转缩放部分的逆转置；退化时退回单位矩阵并保留原始面法线，
     * 避免产生 NaN 法线导致光照崩溃。
     */
    public static Matrix3f normalMatrix(Matrix3f rotationScale)
    {
        Matrix3f result = new Matrix3f(rotationScale);

        if (Math.abs(result.determinant()) < SINGULAR_EPSILON)
        {
            return new Matrix3f();
        }

        return result.invert().transpose();
    }

    public static Vector3f safeNormalize(Vector3f value, Vector3f fallback)
    {
        float lengthSquared = value.lengthSquared();

        if (!Float.isFinite(lengthSquared) || lengthSquared < 1.0E-12F)
        {
            return new Vector3f(fallback);
        }

        return value.normalize();
    }

    public static Vector3f lerp(Vector3f from, Vector3f to, float progress)
    {
        return new Vector3f(from).lerp(to, clamp01(progress));
    }

    /**
     * 骨骼绑定帧：以骨骼初始 Pivot 为旋转中心，顺序与 BBS 渲染器保持一致。
     *
     * <p>Bone Anchor 的 {@code parentLocal} 中「附件帧带有半转」的约定在这里同样适用，
     * 因此模型根帧带一个 Y 轴 180°。</p>
     */
    public static Matrix4f boneFrame(Matrix4f parent, Vector3f pivotModelUnits, Vector3f rotateDegrees, Vector3f scale)
    {
        Vector3f pivot = new Vector3f(pivotModelUnits).mul(ModelMorphAdapter.MODEL_UNIT);

        Matrix4f local = new Matrix4f()
            .translate(pivot)
            .rotate(new Quaternionf().rotateZYX(
                (float) Math.toRadians(rotateDegrees.z),
                (float) Math.toRadians(rotateDegrees.y),
                (float) Math.toRadians(rotateDegrees.x)))
            .scale(scale)
            .translate(-pivot.x, -pivot.y, -pivot.z);

        return new Matrix4f(parent).mul(local);
    }

    /**
     * Cube 的 Pivot 旋转帧（模型单位转方块单位），与 BBS {@code renderCube} 一致：
     * translate(pivot) → rotate → translate(-pivot)。
     */
    public static Matrix4f cubePivotFrame(Vector3f pivotModelUnits, Vector3f rotateDegrees)
    {
        float pivotX = pivotModelUnits.x * ModelMorphAdapter.MODEL_UNIT;
        float pivotY = pivotModelUnits.y * ModelMorphAdapter.MODEL_UNIT;
        float pivotZ = pivotModelUnits.z * ModelMorphAdapter.MODEL_UNIT;

        return new Matrix4f()
            .translate(pivotX, pivotY, pivotZ)
            .rotate(new Quaternionf().rotateZYX(
                (float) Math.toRadians(rotateDegrees.z),
                (float) Math.toRadians(rotateDegrees.y),
                (float) Math.toRadians(rotateDegrees.x)))
            .translate(-pivotX, -pivotY, -pivotZ);
    }

    public record Trs(Vector3f translation, Quaternionf rotation, Vector3f scale)
    {
        public Trs
        {
            translation = new Vector3f(translation);
            rotation = new Quaternionf(rotation);
            scale = new Vector3f(scale);
        }

        public Trs lerp(Trs other, float progress)
        {
            return new Trs(
                new Vector3f(this.translation).lerp(other.translation, progress),
                slerp(this.rotation, other.rotation, progress),
                new Vector3f(this.scale).lerp(other.scale, progress)
            );
        }

        public Matrix3f rotationScale()
        {
            return new Matrix3f().rotation(this.rotation).scale(this.scale);
        }
    }
}
