package gbeic.bbsplusplus.client.morph;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 把 BBS 的 {@link Model} 骨骼/Cube 数据整理成与渲染资源解耦的只读骨架快照。
 *
 * <p>映射编辑器、自动匹配、有效性检查和几何求解器都使用同一套快照，
 * 避免直接修改共享的 {@link ModelCube}。</p>
 */
public final class ModelMorphAdapter
{
    public static final float MODEL_UNIT = 1F / 16F;

    private ModelMorphAdapter()
    {
    }

    public static boolean isCubeOnly(Model model)
    {
        if (model == null)
        {
            return false;
        }

        for (ModelGroup group : model.topGroups)
        {
            if (!isCubeOnly(group))
            {
                return false;
            }
        }

        return true;
    }

    private static boolean isCubeOnly(ModelGroup group)
    {
        if (!group.meshes.isEmpty())
        {
            return false;
        }

        for (ModelGroup child : group.children)
        {
            if (!isCubeOnly(child))
            {
                return false;
            }
        }

        return true;
    }

    public static ModelMorphSkeleton skeleton(Model model)
    {
        List<ModelMorphSkeleton.GroupSpec> groups = new ArrayList<>();

        if (model != null)
        {
            /* BBS 模型空间与渲染空间相差一个 Y 轴半转；骨骼帧沿用 BBS 的约定。 */
            Matrix4f root = new Matrix4f().rotateY((float) Math.PI);

            for (ModelGroup group : model.topGroups)
            {
                collect(groups, group, "", root);
            }
        }

        return new ModelMorphSkeleton(groups);
    }

    private static void collect(List<ModelMorphSkeleton.GroupSpec> out, ModelGroup group, String parentPath, Matrix4f parentFrame)
    {
        String path = parentPath.isEmpty() ? group.id : parentPath + "/" + group.id;
        Matrix4f restFrame = restFrame(parentFrame, group);
        List<ModelMorphSkeleton.CubeSpec> cubes = new ArrayList<>();
        Map<String, Integer> nameCounts = new LinkedHashMap<>();

        for (ModelCube cube : group.cubes)
        {
            if (cube.name != null && !cube.name.isEmpty())
            {
                nameCounts.merge(cube.name, 1, Integer::sum);
            }
        }

        for (int i = 0; i < group.cubes.size(); i++)
        {
            ModelCube cube = group.cubes.get(i);
            String key = cubeKey(path, cube, i, nameCounts);
            Vector3f center = new Vector3f(cube.origin).add(cube.size.x * 0.5F, cube.size.y * 0.5F, cube.size.z * 0.5F);

            restFrame.transformPosition(center.mul(MODEL_UNIT));

            cubes.add(new ModelMorphSkeleton.CubeSpec(
                key, path, cube.name == null ? "" : cube.name, i,
                new Vector3f(cube.origin), new Vector3f(cube.size),
                new Vector3f(cube.pivot), new Vector3f(cube.rotate),
                cube.inflate, cube.material == null ? "" : cube.material, center
            ));
        }

        out.add(new ModelMorphSkeleton.GroupSpec(path, group.id, parentPath, cubes, restFrame));

        for (ModelGroup child : group.children)
        {
            collect(out, child, path, restFrame);
        }
    }

    /**
     * 骨骼绑定姿态帧：以初始 Pivot 为旋转中心，顺序与 BBS 渲染器一致。
     */
    private static Matrix4f restFrame(Matrix4f parent, ModelGroup group)
    {
        return MorphMath.boneFrame(parent, group.initial.translate, group.initial.rotate, group.initial.scale);
    }

    public static String cubeKey(String groupPath, ModelCube cube, int index, Map<String, Integer> nameCounts)
    {
        String name = cube.name == null ? "" : cube.name;

        if (name.isEmpty())
        {
            return groupPath + ":#" + index;
        }

        boolean duplicated = nameCounts.getOrDefault(name, 0) > 1;

        return groupPath + ":" + name + (duplicated ? "#" + index : "");
    }

    public static Map<String, ModelCube> cubeIndex(Model model)
    {
        Map<String, ModelCube> index = new LinkedHashMap<>();

        if (model != null)
        {
            for (ModelGroup group : model.topGroups)
            {
                collectCubeIndex(group, "", index);
            }
        }

        return index;
    }

    private static void collectCubeIndex(ModelGroup group, String parentPath, Map<String, ModelCube> index)
    {
        String path = parentPath.isEmpty() ? group.id : parentPath + "/" + group.id;
        Map<String, Integer> nameCounts = new LinkedHashMap<>();

        for (ModelCube cube : group.cubes)
        {
            if (cube.name != null && !cube.name.isEmpty())
            {
                nameCounts.merge(cube.name, 1, Integer::sum);
            }
        }

        for (int i = 0; i < group.cubes.size(); i++)
        {
            index.put(cubeKey(path, group.cubes.get(i), i, nameCounts), group.cubes.get(i));
        }

        for (ModelGroup child : group.children)
        {
            collectCubeIndex(child, path, index);
        }
    }

    /**
     * 几何指纹：用于模型资源变化后判断引用是否仍然指向同一个 Cube。
     */
    public static String signature(ModelMorphSkeleton.CubeSpec cube)
    {
        return String.format(Locale.ROOT,
            "%.4f,%.4f,%.4f|%.4f,%.4f,%.4f|%.4f,%.4f,%.4f|%.4f,%.4f,%.4f|%.4f|%s",
            cube.origin().x, cube.origin().y, cube.origin().z,
            cube.size().x, cube.size().y, cube.size().z,
            cube.pivot().x, cube.pivot().y, cube.pivot().z,
            cube.rotate().x, cube.rotate().y, cube.rotate().z,
            cube.inflate(), cube.material());
    }
}
