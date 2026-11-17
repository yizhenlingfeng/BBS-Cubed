package gbeic.bbsplusplus.client.morph;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MorphMath} 的纯 JVM 测试：只依赖 JOML 与 java.util，不需要 Minecraft/BBS 运行时。
 */
class MorphMathTest
{
    private static final float DELTA = 1.0E-4F;

    @Test
    void inverseRejectsNullNonFiniteAndSingularMatrices()
    {
        assertThrows(IllegalArgumentException.class, () -> MorphMath.inverse(null));
        assertThrows(IllegalArgumentException.class, () -> MorphMath.inverse(new Matrix4f().m00(Float.NaN)));
        assertThrows(IllegalArgumentException.class, () -> MorphMath.inverse(new Matrix4f().m30(Float.POSITIVE_INFINITY)));
        assertThrows(IllegalArgumentException.class, () -> MorphMath.inverse(new Matrix4f().scaling(1F, 1F, 0F)));
        assertThrows(IllegalArgumentException.class, () -> MorphMath.inverse(new Matrix4f().zero()));
    }

    @Test
    void inverseOrNullReturnsNullForUnusableMatrices()
    {
        assertNull(MorphMath.inverseOrNull(null));
        assertNull(MorphMath.inverseOrNull(new Matrix4f().scaling(1F, 0F, 1F)));
        assertNull(MorphMath.inverseOrNull(new Matrix4f().m11(Float.NaN)));

        Matrix4f inverse = MorphMath.inverseOrNull(new Matrix4f().translation(1F, 2F, 3F).rotateY(0.4F));

        assertNotNull(inverse);
        assertMatrixEquals(new Matrix4f(), new Matrix4f().translation(1F, 2F, 3F).rotateY(0.4F).mul(inverse), DELTA);
    }

    @Test
    void inverseRoundTripsAnAffineMatrix()
    {
        Matrix4f matrix = new Matrix4f().translation(1F, 2F, 3F).rotateY(0.4F).scale(2F, 3F, 4F);
        Matrix4f inverse = MorphMath.inverse(matrix);

        assertNotSame(matrix, inverse);
        assertMatrixEquals(new Matrix4f(), new Matrix4f(matrix).mul(inverse), DELTA);
        assertMatrixEquals(new Matrix4f(), new Matrix4f(inverse).mul(matrix), DELTA);
    }

    @Test
    void decomposeAndComposeRoundTripTranslationRotationAndUniformScale()
    {
        Matrix4f original = new Matrix4f()
            .translation(1F, -2F, 3F)
            .rotateY(0.6F)
            .rotateX(-0.3F)
            .scale(2F);
        MorphMath.Trs pose = MorphMath.decompose(original);

        assertEquals(1F, pose.translation().x, DELTA);
        assertEquals(-2F, pose.translation().y, DELTA);
        assertEquals(3F, pose.translation().z, DELTA);
        assertEquals(2F, pose.scale().x, DELTA);
        assertEquals(2F, pose.scale().y, DELTA);
        assertEquals(2F, pose.scale().z, DELTA);
        assertMatrixEquals(original, MorphMath.compose(pose), DELTA);
    }

    @Test
    void decomposeFlipsMirrorSignToTheXAxisAndRoundTrips()
    {
        Matrix4f original = new Matrix4f()
            .translation(-1F, 0.5F, 2F)
            .rotateY(0.7F)
            .scale(-2F, 1.5F, 3F);
        MorphMath.Trs pose = MorphMath.decompose(original);

        /* 负行列式约定保留在 X 轴上，旋转本身保持右手系。 */
        assertEquals(-2F, pose.scale().x, DELTA);
        assertEquals(1.5F, pose.scale().y, DELTA);
        assertEquals(3F, pose.scale().z, DELTA);
        assertMatrixEquals(original, MorphMath.compose(pose), DELTA);
        assertEquals(1F, (float) Math.sqrt(new Quaternionf(pose.rotation()).lengthSquared()), DELTA);
    }

    @Test
    void decomposeRejectsZeroScaleShearAndNonFiniteInput()
    {
        assertThrows(IllegalArgumentException.class, () -> MorphMath.decompose(null));
        assertThrows(IllegalArgumentException.class, () -> MorphMath.decompose(new Matrix4f().m00(Float.NaN)));
        assertThrows(IllegalArgumentException.class, () -> MorphMath.decompose(new Matrix4f().scaling(1F, 1F, 0F)));
        assertThrows(IllegalArgumentException.class, () -> MorphMath.decompose(new Matrix4f().scaling(0F, 1F, 1F)));
        assertThrows(IllegalArgumentException.class, () -> MorphMath.decompose(new Matrix4f().scaling(1F, 1F, 1F).m10(0.5F)));
    }

    @Test
    void normalMatrixOfPureRotationEqualsTheRotationItself()
    {
        Matrix3f rotation = new Matrix3f().rotateY(0.7F).rotateX(0.2F);
        Matrix3f inverseTranspose = new Matrix3f(rotation).invert().transpose();

        /* 纯旋转的逆转置就是旋转自身（也等于对逆矩阵转置），输入矩阵不能被改写。 */
        assertMatrix3Equals(rotation, MorphMath.normalMatrix(rotation), DELTA);
        assertMatrix3Equals(inverseTranspose, MorphMath.normalMatrix(rotation), DELTA);
        assertMatrix3Equals(new Matrix3f().rotateY(0.7F).rotateX(0.2F), rotation, DELTA);
    }

    @Test
    void normalMatrixFallsBackToIdentityForSingularInput()
    {
        assertMatrix3Equals(new Matrix3f(), MorphMath.normalMatrix(new Matrix3f().zero()), DELTA);
    }

    @Test
    void safeNormalizeFallsBackOnUnusableInput()
    {
        Vector3f fallback = new Vector3f(0F, 1F, 0F);

        assertVectorEquals(fallback, MorphMath.safeNormalize(new Vector3f(), fallback), DELTA);
        assertVectorEquals(fallback, MorphMath.safeNormalize(new Vector3f(Float.NaN, 0F, 0F), fallback), DELTA);
        assertVectorEquals(new Vector3f(0.6F, 0F, 0.8F), MorphMath.safeNormalize(new Vector3f(3F, 0F, 4F), fallback), DELTA);
    }

    @Test
    void slerpTakesTheShortestPathForOppositeHemispheres()
    {
        Quaternionf from = new Quaternionf();
        Quaternionf to = new Quaternionf().rotateY((float) Math.toRadians(315D));

        assertTrue(from.dot(to) < 0F, "precondition: the two quaternions live in opposite hemispheres");

        Quaternionf middle = MorphMath.slerp(from, to, 0.5F);
        Vector3f direction = middle.transform(new Vector3f(0F, 0F, 1F));

        /* 短路径是一 -45° 的旋转，中点是 -22.5°；走远路会得到 +22.5°。 */
        assertEquals(-0.38268F, direction.x, DELTA, "slerp must rotate through -22.5 degrees, not +22.5");
        assertEquals(0.92388F, direction.z, DELTA);
        assertEquals(1F, (float) Math.sqrt(middle.lengthSquared()), DELTA);
    }

    @Test
    void slerpEndpointsReturnTheInputRotations()
    {
        Quaternionf from = new Quaternionf().rotateY((float) Math.toRadians(30D));
        Quaternionf to = new Quaternionf().rotateY((float) Math.toRadians(60D));

        assertQuaternionEquals(from, MorphMath.slerp(from, to, 0F), DELTA);
        assertQuaternionEquals(to, MorphMath.slerp(from, to, 1F), DELTA);
        /* progress 会被 clamp01 限制到 [0, 1]。 */
        assertQuaternionEquals(to, MorphMath.slerp(from, to, 2F), DELTA);
    }

    @Test
    void trsLerpHitsBothEndpointsExactly()
    {
        MorphMath.Trs from = new MorphMath.Trs(new Vector3f(), new Quaternionf(), new Vector3f(1F));
        MorphMath.Trs to = new MorphMath.Trs(
            new Vector3f(2F, 4F, 6F),
            new Quaternionf().rotateY((float) Math.toRadians(90D)),
            new Vector3f(2F));

        MorphMath.Trs start = from.lerp(to, 0F);

        assertVectorEquals(from.translation(), start.translation(), DELTA);
        assertQuaternionEquals(from.rotation(), start.rotation(), DELTA);
        assertVectorEquals(from.scale(), start.scale(), DELTA);

        MorphMath.Trs end = from.lerp(to, 1F);

        assertVectorEquals(to.translation(), end.translation(), DELTA);
        assertQuaternionEquals(to.rotation(), end.rotation(), DELTA);
        assertVectorEquals(to.scale(), end.scale(), DELTA);
    }

    @Test
    void trsLerpMidpointInterpolatesAllThreeComponents()
    {
        MorphMath.Trs from = new MorphMath.Trs(new Vector3f(), new Quaternionf(), new Vector3f(1F));
        MorphMath.Trs to = new MorphMath.Trs(
            new Vector3f(2F, 4F, 6F),
            new Quaternionf().rotateY((float) Math.toRadians(90D)),
            new Vector3f(2F));
        MorphMath.Trs middle = from.lerp(to, 0.5F);

        assertVectorEquals(new Vector3f(1F, 2F, 3F), middle.translation(), DELTA);
        assertVectorEquals(new Vector3f(1.5F), middle.scale(), DELTA);

        Vector3f direction = middle.rotation().transform(new Vector3f(0F, 0F, 1F));

        assertEquals(0.70711F, direction.x, DELTA);
        assertEquals(0.70711F, direction.z, DELTA);
    }

    @Test
    void boneFrameWithNeutralPoseAndZeroPivotIsIdentity()
    {
        Matrix4f frame = MorphMath.boneFrame(new Matrix4f(), new Vector3f(), new Vector3f(), new Vector3f(1F));

        assertMatrixEquals(new Matrix4f(), frame, DELTA);
    }

    @Test
    void boneFrameKeepsThePivotFixedAndAppliesRotationThenScale()
    {
        Vector3f pivotModelUnits = new Vector3f(16F, 8F, -4F);
        Vector3f pivotBlockUnits = new Vector3f(1F, 0.5F, -0.25F);
        Matrix4f frame = MorphMath.boneFrame(new Matrix4f(), pivotModelUnits, new Vector3f(0F, 0F, 90F), new Vector3f(1F));

        assertVectorEquals(pivotBlockUnits, frame.transformPosition(new Vector3f(pivotBlockUnits)), DELTA);

        Matrix4f rotatedAndScaled = MorphMath.boneFrame(
            new Matrix4f(), new Vector3f(), new Vector3f(0F, 0F, 90F), new Vector3f(2F));

        assertVectorEquals(new Vector3f(0F, 2F, 0F),
            rotatedAndScaled.transformPosition(new Vector3f(1F, 0F, 0F)), DELTA);
    }

    @Test
    void boneFrameComposesWithTheParentFrame()
    {
        Matrix4f parent = new Matrix4f().translation(0F, 10F, 0F);
        Matrix4f frame = MorphMath.boneFrame(parent, new Vector3f(), new Vector3f(), new Vector3f(1F));

        assertVectorEquals(new Vector3f(0F, 10F, 0F), frame.transformPosition(new Vector3f()), DELTA);
    }

    @Test
    void cubePivotFrameRotatesAroundThePivotOnly()
    {
        Matrix4f neutral = MorphMath.cubePivotFrame(new Vector3f(16F, 0F, 0F), new Vector3f());

        assertMatrixEquals(new Matrix4f(), neutral, DELTA);

        Matrix4f halfTurn = MorphMath.cubePivotFrame(new Vector3f(16F, 0F, 0F), new Vector3f(0F, 0F, 180F));

        assertVectorEquals(new Vector3f(1F, 0F, 0F), halfTurn.transformPosition(new Vector3f(1F, 0F, 0F)), DELTA);
        assertVectorEquals(new Vector3f(2F, 0F, 0F), halfTurn.transformPosition(new Vector3f()), DELTA);

        Matrix4f quarterTurn = MorphMath.cubePivotFrame(new Vector3f(), new Vector3f(0F, 0F, 90F));

        assertVectorEquals(new Vector3f(0F, 1F, 0F), quarterTurn.transformPosition(new Vector3f(1F, 0F, 0F)), DELTA);
    }

    @Test
    void clamp01ClampsAndTreatsNonFiniteValuesAsZero()
    {
        assertEquals(0F, MorphMath.clamp01(-1F), DELTA);
        assertEquals(0F, MorphMath.clamp01(0F), DELTA);
        assertEquals(0.5F, MorphMath.clamp01(0.5F), DELTA);
        assertEquals(1F, MorphMath.clamp01(1F), DELTA);
        assertEquals(1F, MorphMath.clamp01(2F), DELTA);
        assertEquals(0F, MorphMath.clamp01(Float.NaN), DELTA);
        assertEquals(0F, MorphMath.clamp01(Float.POSITIVE_INFINITY), DELTA);
        assertEquals(0F, MorphMath.clamp01(Float.NEGATIVE_INFINITY), DELTA);
        assertFalse(Float.isNaN(MorphMath.clamp01(Float.NaN)));
    }

    private static void assertVectorEquals(Vector3f expected, Vector3f actual, float delta)
    {
        assertEquals(expected.x, actual.x, delta, "vector x");
        assertEquals(expected.y, actual.y, delta, "vector y");
        assertEquals(expected.z, actual.z, delta, "vector z");
    }

    private static void assertQuaternionEquals(Quaternionf expected, Quaternionf actual, float delta)
    {
        assertEquals(expected.x, actual.x, delta, "quaternion x");
        assertEquals(expected.y, actual.y, delta, "quaternion y");
        assertEquals(expected.z, actual.z, delta, "quaternion z");
        assertEquals(expected.w, actual.w, delta, "quaternion w");
    }

    private static void assertMatrixEquals(Matrix4f expected, Matrix4f actual, float delta)
    {
        for (int column = 0; column < 4; column++)
        {
            for (int row = 0; row < 4; row++)
            {
                assertEquals(expected.get(column, row), actual.get(column, row), delta,
                    "matrix4 element at column " + column + ", row " + row);
            }
        }
    }

    private static void assertMatrix3Equals(Matrix3f expected, Matrix3f actual, float delta)
    {
        for (int column = 0; column < 3; column++)
        {
            for (int row = 0; row < 3; row++)
            {
                assertEquals(expected.get(column, row), actual.get(column, row), delta,
                    "matrix3 element at column " + column + ", row " + row);
            }
        }
    }
}
