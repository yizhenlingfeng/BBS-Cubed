package gbeic.bbsplusplus.client.morph;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MappingGroup} 的纯 JVM 测试：类型推导、去重、拷贝独立性与 CubeRef 归一化。
 * 不触碰 {@code toData()} / {@code fromData()}（它们需要 BBS 的 MapType/ListType）。
 */
class MorphMappingGroupTest
{
    @Test
    void typeIsInferredFromBothSides()
    {
        MappingGroup group = new MappingGroup();

        assertEquals(MappingGroup.Type.EMPTY, group.type());

        group.setSources(List.of(ref("s1")));
        assertEquals(MappingGroup.Type.EMPTY, group.type(), "one side empty is still EMPTY");

        group.setTargets(List.of(ref("t1")));
        assertEquals(MappingGroup.Type.ONE_TO_ONE, group.type());

        group.setTargets(List.of(ref("t1"), ref("t2")));
        assertEquals(MappingGroup.Type.ONE_TO_MANY, group.type());

        group.setSources(List.of(ref("s1"), ref("s2")));
        assertEquals(MappingGroup.Type.MANY_TO_MANY, group.type());

        group.setTargets(List.of(ref("t1")));
        assertEquals(MappingGroup.Type.MANY_TO_ONE, group.type());
    }

    @Test
    void typeLabelsMatchTheContract()
    {
        assertEquals("1:1", MappingGroup.Type.ONE_TO_ONE.label());
        assertEquals("1:N", MappingGroup.Type.ONE_TO_MANY.label());
        assertEquals("N:1", MappingGroup.Type.MANY_TO_ONE.label());
        assertEquals("N:N", MappingGroup.Type.MANY_TO_MANY.label());
        assertEquals("-", MappingGroup.Type.EMPTY.label());
    }

    @Test
    void onlyManyToManyAndEmptyAreUnsupported()
    {
        for (MappingGroup.Type type : MappingGroup.Type.values())
        {
            boolean expected = type == MappingGroup.Type.ONE_TO_ONE
                || type == MappingGroup.Type.ONE_TO_MANY
                || type == MappingGroup.Type.MANY_TO_ONE;

            assertEquals(expected, type.isSupported(), "isSupported for " + type);
        }

        MappingGroup manyToMany = new MappingGroup();

        manyToMany.setSources(List.of(ref("s1"), ref("s2")));
        manyToMany.setTargets(List.of(ref("t1"), ref("t2")));

        assertFalse(manyToMany.isSupported());
    }

    @Test
    void duplicateKeysAreDroppedAndTheFirstEntryWins()
    {
        MappingGroup group = new MappingGroup("group-1");

        group.setSources(List.of(
            new MappingGroup.CubeRef("a", "first"),
            new MappingGroup.CubeRef("a", "second"),
            new MappingGroup.CubeRef("b", "third")));
        group.setTargets(List.of(
            new MappingGroup.CubeRef("x", ""),
            new MappingGroup.CubeRef("x", "")));

        assertEquals(2, group.sources().size());
        assertEquals("a", group.sources().get(0).key());
        assertEquals("first", group.sources().get(0).signature());
        assertEquals("b", group.sources().get(1).key());
        assertEquals(1, group.targets().size());
        assertEquals("x", group.targets().get(0).key());
    }

    @Test
    void nullAndEmptyCubeRefsAreRejectedBySetSources()
    {
        MappingGroup group = new MappingGroup();

        group.setSources(Arrays.asList(
            null,
            new MappingGroup.CubeRef(null, null),
            new MappingGroup.CubeRef("", "sig"),
            new MappingGroup.CubeRef("kept", "")));

        assertEquals(1, group.sources().size());
        assertEquals("kept", group.sources().get(0).key());
    }

    @Test
    void settingNullRefListClearsTheSide()
    {
        MappingGroup group = new MappingGroup();

        group.setSources(List.of(ref("s1")));
        group.setTargets(List.of(ref("t1")));
        assertEquals(MappingGroup.Type.ONE_TO_ONE, group.type());

        group.setSources(null);
        group.setTargets(null);

        assertTrue(group.sources().isEmpty());
        assertTrue(group.targets().isEmpty());
        assertEquals(MappingGroup.Type.EMPTY, group.type());
    }

    @Test
    void cubeRefNormalizesNullFieldsToEmptyStrings()
    {
        MappingGroup.CubeRef ref = new MappingGroup.CubeRef(null, null);

        assertEquals("", ref.key());
        assertEquals("", ref.signature());
        assertEquals(new MappingGroup.CubeRef("", ""), ref);
    }

    @Test
    void copyIsEqualButIndependent()
    {
        MappingGroup original = new MappingGroup("group-1");

        original.setSources(List.of(new MappingGroup.CubeRef("src", "sig-src")));
        original.setTargets(List.of(new MappingGroup.CubeRef("dst", "sig-dst")));

        MappingGroup copy = original.copy();

        assertNotSame(original, copy);
        assertEquals(original, copy);
        assertEquals(original.hashCode(), copy.hashCode());
        assertEquals("group-1", copy.id());

        copy.setSources(List.of(new MappingGroup.CubeRef("other", "")));
        copy.setTargets(List.of());

        assertEquals(1, original.sources().size());
        assertEquals("src", original.sources().get(0).key());
        assertEquals(1, original.targets().size());
        assertEquals("dst", original.targets().get(0).key());
        assertNotEquals(original, copy);
    }

    @Test
    void blankIdFallsBackToAGeneratedUniqueId()
    {
        MappingGroup first = new MappingGroup(null);
        MappingGroup second = new MappingGroup("");

        assertTrue(first.id() != null && !first.id().isEmpty());
        assertTrue(second.id() != null && !second.id().isEmpty());
        assertNotEquals(first.id(), second.id());
        assertEquals("explicit", new MappingGroup("explicit").id());
    }

    @Test
    void sourceAndTargetViewsAreUnmodifiable()
    {
        MappingGroup group = new MappingGroup();

        group.setSources(List.of(ref("s1")));

        assertThrows(UnsupportedOperationException.class, () -> group.sources().add(ref("s2")));
        assertEquals(1, group.sources().size());
    }

    private static MappingGroup.CubeRef ref(String key)
    {
        return new MappingGroup.CubeRef(key, "");
    }
}
