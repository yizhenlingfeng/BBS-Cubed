package gbeic.bbsplusplus.client.morph;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ModelMorphSkeleton} 与 {@link ModelMorphAdapter#signature} 的纯 JVM 测试：
 * 命名回退、几何指纹的稳定性/格式，以及 key 索引。
 *
 * <p>签名只做 JOML 数值格式化，不读取任何 BBS 的 {@code Model} 数据。</p>
 */
class ModelMorphSkeletonTest
{
    @Test
    void displayNameFallsBackToTheCubeOrdinal()
    {
        ModelMorphSkeleton.CubeSpec named = cube("body:core", "body", "core", 3);
        ModelMorphSkeleton.CubeSpec unnamed = cube("body:#5", "body", "", 5);

        assertEquals("core", named.displayName());
        assertEquals("cube_5", unnamed.displayName());
    }

    @Test
    void signatureIsStableAndFormatsEveryGeometryComponent()
    {
        ModelMorphSkeleton.CubeSpec cube = new ModelMorphSkeleton.CubeSpec(
            "body:core", "body", "core", 0,
            new Vector3f(1F, 2F, 3F), new Vector3f(16F, 8F, 4F),
            new Vector3f(0F, 4F, 0F), new Vector3f(0F, 90F, 0F),
            0.5F, "skin", new Vector3f());

        String signature = cube.signature();

        assertEquals("1.0000,2.0000,3.0000|16.0000,8.0000,4.0000|0.0000,4.0000,0.0000"
            + "|0.0000,90.0000,0.0000|0.5000|skin", signature);
        assertEquals(signature, cube.signature(), "signature must be deterministic");
        assertEquals(signature, ModelMorphAdapter.signature(cube), "record and adapter must agree");
    }

    @Test
    void signatureDependsOnGeometryButNotOnTheCubeKey()
    {
        ModelMorphSkeleton.CubeSpec cube = new ModelMorphSkeleton.CubeSpec(
            "body:core", "body", "core", 0,
            new Vector3f(1F, 2F, 3F), new Vector3f(16F, 8F, 4F),
            new Vector3f(), new Vector3f(), 0F, "", new Vector3f());
        ModelMorphSkeleton.CubeSpec sameGeometry = new ModelMorphSkeleton.CubeSpec(
            "body:other", "body", "other", 1,
            new Vector3f(1F, 2F, 3F), new Vector3f(16F, 8F, 4F),
            new Vector3f(), new Vector3f(), 0F, "", new Vector3f());
        ModelMorphSkeleton.CubeSpec moved = new ModelMorphSkeleton.CubeSpec(
            "body:core", "body", "core", 0,
            new Vector3f(2F, 2F, 3F), new Vector3f(16F, 8F, 4F),
            new Vector3f(), new Vector3f(), 0F, "", new Vector3f());

        assertEquals(cube.signature(), sameGeometry.signature());
        assertNotEquals(cube.signature(), moved.signature());
    }

    @Test
    void skeletonIndexesCubesByKeyAndGroupsByPath()
    {
        ModelMorphSkeleton skeleton = skeleton();

        assertFalse(skeleton.isEmpty());
        assertEquals(3, skeleton.cubeCount());
        assertEquals(List.of("body", "arm"), List.copyOf(skeleton.byPath().keySet()));
        assertEquals(List.of("body:core", "body:extra", "arm:hand"), List.copyOf(skeleton.cubesByKey().keySet()));

        ModelMorphSkeleton empty = new ModelMorphSkeleton(List.of());

        assertTrue(empty.isEmpty());
        assertEquals(0, empty.cubeCount());
        assertTrue(empty.byPath().isEmpty());
        assertTrue(empty.cubesByKey().isEmpty());
    }

    /**
     * 与其它测试相同的骨架构造方式：{组路径, Cube key, originX, originY, originZ, sizeX, sizeY, sizeZ}。
     */
    private static ModelMorphSkeleton skeleton()
    {
        ModelMorphSkeleton.CubeSpec core = new ModelMorphSkeleton.CubeSpec(
            "body:core", "body", "core", 0,
            new Vector3f(), new Vector3f(16F, 16F, 16F),
            new Vector3f(), new Vector3f(), 0F, "", new Vector3f(0.5F, 0.5F, 0.5F));
        ModelMorphSkeleton.CubeSpec extra = new ModelMorphSkeleton.CubeSpec(
            "body:extra", "body", "extra", 1,
            new Vector3f(0F, 16F, 0F), new Vector3f(8F, 8F, 8F),
            new Vector3f(), new Vector3f(), 0F, "", new Vector3f(0.5F, 1.5F, 0.5F));
        ModelMorphSkeleton.CubeSpec hand = new ModelMorphSkeleton.CubeSpec(
            "arm:hand", "arm", "hand", 0,
            new Vector3f(0F, 0F, 8F), new Vector3f(4F, 4F, 4F),
            new Vector3f(), new Vector3f(), 0F, "", new Vector3f(0.25F, 0.25F, 0.75F));

        ModelMorphSkeleton.GroupSpec body = new ModelMorphSkeleton.GroupSpec(
            "body", "body", "", List.of(core, extra), new Matrix4f());
        ModelMorphSkeleton.GroupSpec arm = new ModelMorphSkeleton.GroupSpec(
            "arm", "arm", "", List.of(hand), new Matrix4f());

        return new ModelMorphSkeleton(List.of(body, arm));
    }

    private static ModelMorphSkeleton.CubeSpec cube(String key, String groupPath, String name, int ordinal)
    {
        return new ModelMorphSkeleton.CubeSpec(
            key, groupPath, name, ordinal,
            new Vector3f(), new Vector3f(1F), new Vector3f(), new Vector3f(),
            0F, "", new Vector3f());
    }
}
