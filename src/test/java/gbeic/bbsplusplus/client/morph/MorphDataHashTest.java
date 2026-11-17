package gbeic.bbsplusplus.client.morph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * {@link ModelMorphData#hashCode()} 的缓存正确性。
 *
 * <p>该方法在渲染热路径上被每帧调用（判断形变几何能否复用），而它需要遍历全部
 * 映射组与其中的 Cube 引用。缓存能消除这份每帧开销——但必须保证任何结构变更
 * 都会让缓存失效，否则几何会用旧数据、形变不更新。</p>
 */
public class MorphDataHashTest
{
    private static MappingGroup group(String id, String sourceKey, String targetKey)
    {
        MappingGroup group = new MappingGroup(id);

        group.setSources(java.util.List.of(new MappingGroup.CubeRef(sourceKey, "")));
        group.setTargets(java.util.List.of(new MappingGroup.CubeRef(targetKey, "")));

        return group;
    }

    /**
     * 同一份数据重复取哈希必须稳定（这正是缓存的前提）。
     */
    @Test
    void hashIsStableForUnchangedData()
    {
        ModelMorphData data = new ModelMorphData();

        data.setSourceModel("a");
        data.setTargetModel("b");
        data.setGroups(java.util.List.of(group("g1", "a:cube_0", "b:cube_0")));

        assertEquals(data.hashCode(), data.hashCode(), "未修改时哈希必须稳定");
    }

    /**
     * 关键：改映射组后哈希必须变，否则几何不会重建。
     */
    @Test
    void hashChangesWhenGroupsChange()
    {
        ModelMorphData data = new ModelMorphData();

        data.setGroups(java.util.List.of(group("g1", "a:cube_0", "b:cube_0")));
        int before = data.hashCode();

        data.setGroups(java.util.List.of(group("g2", "a:cube_1", "b:cube_1")));

        assertNotEquals(before, data.hashCode(), "映射组变化后哈希必须改变");
    }

    /**
     * 改模型 id 后哈希必须变。
     */
    @Test
    void hashChangesWhenModelsChange()
    {
        ModelMorphData data = new ModelMorphData();

        data.setSourceModel("a");
        int before = data.hashCode();

        data.setSourceModel("c");

        assertNotEquals(before, data.hashCode(), "源模型变化后哈希必须改变");
    }

    /**
     * 改开关后哈希必须变。
     */
    @Test
    void hashChangesWhenFlagsChange()
    {
        ModelMorphData data = new ModelMorphData();

        int before = data.hashCode();

        data.setEnabled(!data.enabled());

        assertNotEquals(before, data.hashCode(), "开关变化后哈希必须改变");
    }

    /**
     * addGroup 也要让缓存失效。
     */
    @Test
    void hashChangesAfterAddGroup()
    {
        ModelMorphData data = new ModelMorphData();

        int before = data.hashCode();

        data.addGroup(group("g1", "a:cube_0", "b:cube_0"));

        assertNotEquals(before, data.hashCode(), "新增映射组后哈希必须改变");
    }

    @Test
    void hashChangesWhenAStoredMappingGroupIsEdited()
    {
        ModelMorphData data = new ModelMorphData();
        data.setGroups(java.util.List.of(group("g1", "a:cube_0", "b:cube_0")));
        int before = data.hashCode();

        data.groups().get(0).setSources(java.util.List.of(new MappingGroup.CubeRef("a:cube_1", "")));

        assertNotEquals(before, data.hashCode(), "通过 groups() 取得的组被修改时必须使父对象哈希失效");
    }
}
