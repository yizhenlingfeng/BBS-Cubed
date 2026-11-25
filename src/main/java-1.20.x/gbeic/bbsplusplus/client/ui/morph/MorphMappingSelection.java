package gbeic.bbsplusplus.client.ui.morph;

import gbeic.bbsplusplus.client.morph.MappingGroup;
import gbeic.bbsplusplus.client.morph.ModelMorphAdapter;
import gbeic.bbsplusplus.client.morph.ModelMorphRuntime;
import gbeic.bbsplusplus.client.morph.ModelMorphSkeleton;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 映射编辑器使用的纯函数辅助。
 *
 * <p>集中放置三类与实例状态无关的逻辑：Cube/骨骼名的可读化文本、{@link MappingGroup.CubeRef}
 * 的构造与判重、以及从激活的 Cube 集合推导行内状态。把它们抽成静态方法后，
 * 列表、预览等子组件都能直接复用，主面板也不必再承载这些细节。</p>
 */
final class MorphMappingSelection
{
    private MorphMappingSelection()
    {
    }

    /** 把一组被选中的 Cube key 拼成一行摘要，超过两个时截断，用于草稿卡片的目标详情。 */
    static String selectionDetail(Set<String> keys)
    {
        if (keys == null || keys.isEmpty())
        {
            return "-";
        }

        StringBuilder builder = new StringBuilder();
        int index = 0;

        for (String key : keys)
        {
            if (index++ > 0)
            {
                builder.append(", ");
            }

            builder.append(readableRef(key));

            if (index >= 2 && keys.size() > index)
            {
                builder.append(" …");

                break;
            }
        }

        return builder.toString();
    }

    /** 用骨架里的 Cube 规格把 key 转成带签名的引用，便于后续校验签名是否漂移。 */
    static List<MappingGroup.CubeRef> refs(ModelMorphSkeleton skeleton, Set<String> keys)
    {
        List<MappingGroup.CubeRef> out = new ArrayList<>();
        Map<String, ModelMorphSkeleton.CubeSpec> cubes = skeleton.cubesByKey();

        for (String key : keys)
        {
            ModelMorphSkeleton.CubeSpec cube = cubes.get(key);

            if (cube != null)
            {
                out.add(new MappingGroup.CubeRef(cube.key(), cube.signature()));
            }
        }

        return out;
    }

    /** 判断已有映射组是否复用了给定的源或目标 Cube——一个 Cube 只能属于一个组。 */
    static boolean reuses(MappingGroup group, Set<String> sources, Set<String> targets)
    {
        for (MappingGroup.CubeRef ref : group.sources())
        {
            if (sources.contains(ref.key()))
            {
                return true;
            }
        }

        for (MappingGroup.CubeRef ref : group.targets())
        {
            if (targets.contains(ref.key()))
            {
                return true;
            }
        }

        return false;
    }

    /** 把引用列表的 key 全部并入集合。 */
    static void addKeys(Set<String> out, List<MappingGroup.CubeRef> refs)
    {
        for (MappingGroup.CubeRef ref : refs)
        {
            out.add(ref.key());
        }
    }

    /** 判断给定 key 列表里是否有任意一个落在集合中（用于行底色判定）。 */
    static boolean containsAny(Set<String> set, List<String> keys)
    {
        for (String key : keys)
        {
            if (set.contains(key))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * 从骨架里的完整 Cube key 提取渲染侧使用的「短 key」。
     *
     * <p>完整 key 形如 {@code anchor/low_body/body/left_arm:cube_0}，
     * 渲染侧只生成 {@code cube_0}（重名时 {@code cube_0#1}）。</p>
     */
    static String shortCubeKey(ModelMorphSkeleton.CubeSpec cube, ModelMorphSkeleton.GroupSpec group)
    {
        String name = cube.name();
        int ordinal = cube.ordinal();

        if (name == null || name.isEmpty())
        {
            return "#" + ordinal;
        }

        /* 组内重名时必须带序号，否则与渲染侧生成的 key 对不上。 */
        int sameName = 0;

        for (ModelMorphSkeleton.CubeSpec other : group.cubes())
        {
            if (name.equals(other.name()))
            {
                sameName++;
            }
        }

        return sameName > 1 ? name + "#" + ordinal : name;
    }

    /** 取组路径的最后一段（直接父级组名）；组名在同一模型内唯一，短名足以识别。 */
    static String shortGroupName(String path)
    {
        if (path == null || path.isEmpty())
        {
            return "";
        }

        int slash = path.lastIndexOf('/');

        return slash < 0 ? path : path.substring(slash + 1);
    }

    /**
     * 把 Cube 的完整 key 转成可读名称。
     *
     * <p>只取冒号后部分是 {@code cube_0}，所有块同名用户无法区分；
     * 因此保留「直接父级组名 + 块名」，例如 {@code left_arm:cube_0}。</p>
     */
    static String readableRef(String key)
    {
        if (key == null || key.isEmpty())
        {
            return "-";
        }

        int colon = key.lastIndexOf(':');

        if (colon < 0)
        {
            return shortGroupName(key);
        }

        String group = shortGroupName(key.substring(0, colon));
        String cube = key.substring(colon + 1);

        return group.isEmpty() ? cube : group + ":" + cube;
    }

    /** Cube 名重复时补上序号，否则列表里会出现两个一模一样的行，用户无法区分该点哪个。 */
    static String cubeText(ModelMorphSkeleton.GroupSpec group, ModelMorphSkeleton.CubeSpec cube)
    {
        String name = cube.name();

        if (name.isEmpty())
        {
            return cube.displayName();
        }

        int count = 0;

        for (ModelMorphSkeleton.CubeSpec other : group.cubes())
        {
            if (other.name().equals(name))
            {
                count++;
            }
        }

        return count > 1 ? name + "#" + cube.ordinal() : name;
    }

    /** 骨骼路径的深度（斜杠数量），用于列表缩进。 */
    static int depthOf(String path)
    {
        int depth = 0;

        for (int i = 0; i < path.length(); i++)
        {
            if (path.charAt(i) == '/')
            {
                depth++;
            }
        }

        return depth;
    }

    static ModelMorphSkeleton skeletonOf(String id)
    {
        ModelInstance instance = ModelMorphRuntime.model(id);

        if (instance == null || !(instance.model instanceof Model model))
        {
            return null;
        }

        ModelMorphSkeleton skeleton = ModelMorphAdapter.skeleton(model);

        return skeleton.isEmpty() ? null : skeleton;
    }
}
