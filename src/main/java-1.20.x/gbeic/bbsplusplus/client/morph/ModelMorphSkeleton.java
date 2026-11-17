package gbeic.bbsplusplus.client.morph;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个模型的骨骼/Cube 只读快照，包含用于自动匹配与几何反求的绑定姿态帧。
 */
public final class ModelMorphSkeleton
{
    private final List<GroupSpec> groups;

    public ModelMorphSkeleton(List<GroupSpec> groups)
    {
        this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
    }

    public List<GroupSpec> groups()
    {
        return this.groups;
    }

    public Map<String, GroupSpec> byPath()
    {
        Map<String, GroupSpec> map = new LinkedHashMap<>();

        for (GroupSpec group : this.groups)
        {
            map.put(group.path(), group);
        }

        return map;
    }

    public Map<String, CubeSpec> cubesByKey()
    {
        Map<String, CubeSpec> map = new LinkedHashMap<>();

        for (GroupSpec group : this.groups)
        {
            for (CubeSpec cube : group.cubes())
            {
                map.put(cube.key(), cube);
            }
        }

        return map;
    }

    public int cubeCount()
    {
        int count = 0;

        for (GroupSpec group : this.groups)
        {
            count += group.cubes().size();
        }

        return count;
    }

    public boolean isEmpty()
    {
        return this.cubeCount() == 0;
    }

    public record CubeSpec(
        String key, String groupPath, String name, int ordinal,
        Vector3f origin, Vector3f size, Vector3f pivot, Vector3f rotate,
        float inflate, String material, Vector3f worldCenter
    )
    {
        public CubeSpec
        {
            key = key == null ? "" : key;
            groupPath = groupPath == null ? "" : groupPath;
            name = name == null ? "" : name;
            origin = new Vector3f(origin);
            size = new Vector3f(size);
            pivot = new Vector3f(pivot);
            rotate = new Vector3f(rotate);
            material = material == null ? "" : material;
            worldCenter = new Vector3f(worldCenter);
        }

        public String signature()
        {
            return ModelMorphAdapter.signature(this);
        }

        public String displayName()
        {
            return this.name.isEmpty() ? "cube_" + this.ordinal : this.name;
        }
    }

    public record GroupSpec(String path, String bone, String parentPath, List<CubeSpec> cubes, Matrix4f restFrame)
    {
        public GroupSpec
        {
            path = path == null ? "" : path;
            bone = bone == null ? "" : bone;
            parentPath = parentPath == null ? "" : parentPath;
            cubes = Collections.unmodifiableList(new ArrayList<>(cubes));
            restFrame = new Matrix4f(restFrame);
        }
    }
}
