package gbeic.bbsplusplus.client.morph;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 映射树只列出可操作的 Cube。
 *
 * <p>不含 Cube 的组只承载层级关系，用户无法对它们做任何映射操作。大型模型里这类空组
 * 往往占绝大多数（实测 BBS 的 steve 模型 37 个组里有 30 个是空组），列出来只会淹没
 * 真正可选的 Cube。</p>
 */
public class MorphTreeFilterTest
{
    private static ModelMorphSkeleton.CubeSpec cube(String key, String group)
    {
        return new ModelMorphSkeleton.CubeSpec(
            key, group, "c", 0,
            new Vector3f(0F, 0F, 0F), new Vector3f(1F, 1F, 1F),
            new Vector3f(0F, 0F, 0F), new Vector3f(0F, 0F, 0F),
            0F, "", new Vector3f(0F, 0F, 0F));
    }

    private static ModelMorphSkeleton.GroupSpec group(String path, ModelMorphSkeleton.CubeSpec... cubes)
    {
        return new ModelMorphSkeleton.GroupSpec(path, path, "", List.of(cubes), new Matrix4f());
    }

    /**
     * 空组必须被识别出来（供 UI 跳过），有 Cube 的组保留。
     */
    @Test
    void skeletonExposesWhichGroupsHoldCubes()
    {
        ModelMorphSkeleton skeleton = new ModelMorphSkeleton(List.of(
            group("root"),
            group("root/body", cube("root/body:cube_0", "root/body")),
            group("root/body/arm"),
            group("root/body/arm/hand", cube("root/body/arm/hand:cube_0", "root/body/arm/hand"))));

        int empty = 0;
        int withCubes = 0;

        for (ModelMorphSkeleton.GroupSpec spec : skeleton.groups())
        {
            if (spec.cubes().isEmpty())
            {
                empty++;
            }
            else
            {
                withCubes++;
            }
        }

        assertEquals(2, empty, "应识别出 2 个空组");
        assertEquals(2, withCubes, "应识别出 2 个含 Cube 的组");
    }

    /**
     * 关键：Cube 总数不受过滤影响——过滤只隐藏空组，不能漏掉任何可操作对象。
     */
    @Test
    void filteringKeepsEveryCube()
    {
        ModelMorphSkeleton skeleton = new ModelMorphSkeleton(List.of(
            group("root"),
            group("root/a", cube("root/a:0", "root/a"), cube("root/a:1", "root/a")),
            group("root/b", cube("root/b:0", "root/b"))));

        int cubes = 0;
        int rows = 0;

        for (ModelMorphSkeleton.GroupSpec spec : skeleton.groups())
        {
            if (spec.cubes().isEmpty())
            {
                continue;
            }

            rows++;
            cubes += spec.cubes().size();
        }

        assertEquals(3, cubes, "过滤后仍应保留全部 Cube");
        assertEquals(2, rows, "只剩含 Cube 的组作为分组行");
        assertEquals(3, skeleton.cubeCount(), "骨架自身的 Cube 计数不应受 UI 过滤影响");
    }

    /**
     * 全是空组时不能产生任何可选行，也不能抛异常。
     */
    @Test
    void allEmptyGroupsProduceNoRows()
    {
        ModelMorphSkeleton skeleton = new ModelMorphSkeleton(List.of(
            group("root"), group("root/a"), group("root/a/b")));

        int rows = 0;

        for (ModelMorphSkeleton.GroupSpec spec : skeleton.groups())
        {
            if (!spec.cubes().isEmpty())
            {
                rows++;
            }
        }

        assertEquals(0, rows);
        assertTrue(skeleton.isEmpty(), "没有 Cube 的骨架应被视为空");
    }
}
