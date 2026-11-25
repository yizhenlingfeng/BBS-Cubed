package gbeic.bbsplusplus.client.ui.morph;

import gbeic.bbsplusplus.client.morph.ModelMorphSkeleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Cube 树中的一个可见行。
 *
 * <p>骨骼行是分支，Cube 行是叶子；两者共用同一个列表，由 {@link CubeList} 的缩进与分支
 * 表现层级。该类型是纯数据载体：除了与骨架内的组名/块名做字符串匹配外不持有任何外部状态。</p>
 */
final class CubeRow
{
    private final boolean bone;
    private final int depth;
    private final String path;
    private final String text;
    private final String key;
    private final List<String> cubes;
    private final boolean open;

    private CubeRow(boolean bone, int depth, String path, String text, String key, List<String> cubes, boolean open)
    {
        this.bone = bone;
        this.depth = depth;
        this.path = path;
        this.text = text;
        this.key = key;
        this.cubes = cubes;
        this.open = open;
    }

    static CubeRow bone(ModelMorphSkeleton.GroupSpec group, int depth, boolean open)
    {
        List<String> keys = new ArrayList<>();

        for (ModelMorphSkeleton.CubeSpec cube : group.cubes())
        {
            keys.add(cube.key());
        }

        /* 只显示<b>直接父级组名</b>，不显示完整嵌套路径。
           形如 anchor/anchor_low/low_body/body/left_arm 的路径会把行撑爆、且绝大部分
           前缀对用户没有意义；组名在同一模型内唯一，因此短名足以识别。
           不再附加 Cube 数量后缀：展开后就能直接数到，后缀只会让行更挤。 */
        String text = MorphMappingSelection.shortGroupName(group.path());

        return new CubeRow(true, depth, group.path(), text, group.path(), keys, open);
    }

    static CubeRow cube(ModelMorphSkeleton.GroupSpec group, ModelMorphSkeleton.CubeSpec cube, int depth)
    {
        return new CubeRow(false, depth, group.path(), MorphMappingSelection.cubeText(group, cube), cube.key(), List.of(), false);
    }

    /**
     * 按身份字段比较。
     *
     * <p>必须实现：{@code UIList.setCurrent} 内部用 {@code list.indexOf(element)}，
     * 而它依赖 {@code equals}。不实现时永远返回 -1，选中静默失败——
     * 这正是「3D 里点中了块，列表却毫无反应」的原因。</p>
     */
    @Override
    public boolean equals(Object object)
    {
        if (this == object)
        {
            return true;
        }

        if (!(object instanceof CubeRow other))
        {
            return false;
        }

        return this.bone == other.bone
            && Objects.equals(this.key, other.key)
            && Objects.equals(this.path, other.path);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.bone, this.key, this.path);
    }

    boolean bone()
    {
        return this.bone;
    }

    int depth()
    {
        return this.depth;
    }

    String text()
    {
        return this.text;
    }

    String key()
    {
        return this.key;
    }

    /** 行的骨骼路径；骨骼行用它把子 Cube 归组，Cube 行用它判断属于哪块骨骼。 */
    String path()
    {
        return this.path;
    }

    List<String> cubes()
    {
        return this.cubes;
    }

    boolean open()
    {
        return this.open;
    }
}
