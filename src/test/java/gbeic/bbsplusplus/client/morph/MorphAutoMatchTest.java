package gbeic.bbsplusplus.client.morph;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MorphAutoMatch} 的纯 JVM 测试：只在手工构造的骨架上做 key / 骨骼名 / 位置匹配。
 */
class MorphAutoMatchTest
{
    @Test
    void identicalCubeKeysProduceOneToOneSuggestions()
    {
        ModelMorphSkeleton source = skeleton(new Object[][] {
            {"body", "body:core", 0, 0, 0, 16, 16, 16},
            {"arm", "arm:left", 0, 0, 0, 4, 4, 4}
        });
        ModelMorphSkeleton target = skeleton(new Object[][] {
            {"body", "body:core", 0, 0, 0, 8, 8, 8},
            {"arm", "arm:left", 4, 0, 0, 4, 4, 4}
        });

        List<MappingGroup> groups = MorphAutoMatch.suggest(source, target);

        assertEquals(2, groups.size());

        Map<String, String> pairs = new LinkedHashMap<>();

        for (MappingGroup group : groups)
        {
            assertEquals(MappingGroup.Type.ONE_TO_ONE, group.type());
            assertTrue(group.isSupported());
            assertEquals(1, group.sources().size());
            assertEquals(1, group.targets().size());
            pairs.put(group.sources().get(0).key(), group.targets().get(0).key());
        }

        assertEquals("body:core", pairs.get("body:core"));
        assertEquals("arm:left", pairs.get("arm:left"));
    }

    @Test
    void unrelatedSingleCubesStillGetBestEffortSuggestion()
    {
        ModelMorphSkeleton source = skeleton(new Object[][] {
            {"arm", "arm:cubeA", 0, 0, 0, 16, 16, 16}
        });
        ModelMorphSkeleton target = skeleton(new Object[][] {
            {"leg", "leg:cubeB", 320, 0, 0, 16, 16, 16}
        });

        List<MappingGroup> groups = MorphAutoMatch.suggest(source, target);

        assertEquals(1, groups.size());
        assertEquals("arm:cubeA", groups.get(0).sources().get(0).key());
        assertEquals("leg:cubeB", groups.get(0).targets().get(0).key());
        assertTrue(MorphAutoMatch.suggest(null, target).isEmpty());
        assertTrue(MorphAutoMatch.suggest(source, null).isEmpty());
    }

    @Test
    void differentBoneNamesAndRootTranslationsAreMatchedByRelativePosition()
    {
        ModelMorphSkeleton source = skeleton(new Object[][] {
            {"bed/head", "bed:head", 0, 0, 0, 8, 8, 8},
            {"bed/foot", "bed:foot", 16, 0, 0, 8, 8, 8}
        });
        ModelMorphSkeleton target = skeleton(new Object[][] {
            {"boat/front", "boat:front", 160, 0, 0, 16, 16, 16},
            {"boat/back", "boat:back", 192, 0, 0, 16, 16, 16}
        });

        List<MappingGroup> groups = MorphAutoMatch.suggest(source, target);
        Map<String, String> pairs = new LinkedHashMap<>();

        for (MappingGroup group : groups)
        {
            pairs.put(group.sources().get(0).key(), group.targets().get(0).key());
        }

        assertEquals(2, groups.size());
        assertEquals("boat:front", pairs.get("bed:head"));
        assertEquals("boat:back", pairs.get("bed:foot"));
        assertCompleteCoverage(source, target, groups);
    }

    @Test
    void matchingBoneNameWithCloseGeometryProducesSuggestion()
    {
        ModelMorphSkeleton source = skeleton(new Object[][] {
            {"arm", "arm:cubeA", 0, 0, 0, 16, 16, 16}
        });
        ModelMorphSkeleton target = skeleton(new Object[][] {
            {"arm", "arm:cubeB", 8, 0, 0, 16, 16, 16}
        });

        List<MappingGroup> groups = MorphAutoMatch.suggest(source, target);

        assertEquals(1, groups.size());
        assertEquals(MappingGroup.Type.ONE_TO_ONE, groups.get(0).type());
        assertEquals("arm:cubeA", groups.get(0).sources().get(0).key());
        assertEquals("arm:cubeB", groups.get(0).targets().get(0).key());
    }

    @Test
    void surplusTargetsAreGroupedAsOneToMany()
    {
        ModelMorphSkeleton source = skeleton(new Object[][] {
            {"old/body", "old:body", 0, 0, 0, 16, 16, 16}
        });
        ModelMorphSkeleton target = skeleton(new Object[][] {
            {"new/front", "new:front", 128, 0, 0, 8, 8, 8},
            {"new/back", "new:back", 136, 0, 0, 8, 8, 8}
        });

        List<MappingGroup> groups = MorphAutoMatch.suggest(source, target);

        assertEquals(1, groups.size());
        assertEquals(MappingGroup.Type.ONE_TO_MANY, groups.get(0).type());
        assertEquals(1, groups.get(0).sources().size());
        assertEquals(2, groups.get(0).targets().size());
        assertCompleteCoverage(source, target, groups);
    }

    @Test
    void surplusSourcesAreGroupedAsManyToOne()
    {
        ModelMorphSkeleton source = skeleton(new Object[][] {
            {"old/front", "old:front", 0, 0, 0, 8, 8, 8},
            {"old/back", "old:back", 8, 0, 0, 8, 8, 8}
        });
        ModelMorphSkeleton target = skeleton(new Object[][] {
            {"new/body", "new:body", 160, 0, 0, 16, 16, 16}
        });

        List<MappingGroup> groups = MorphAutoMatch.suggest(source, target);

        assertEquals(1, groups.size());
        assertEquals(MappingGroup.Type.MANY_TO_ONE, groups.get(0).type());
        assertEquals(2, groups.get(0).sources().size());
        assertEquals(1, groups.get(0).targets().size());
        assertCompleteCoverage(source, target, groups);
    }

    @Test
    void assignmentMinimizesTotalCostInsteadOfTakingEachNearestGreedily()
    {
        int[] assignment = MorphAutoMatch.minimumCostAssignment(new double[][] {
            {0.10D, 0.15D},
            {0.11D, 0.80D}
        });

        assertEquals(1, assignment[0]);
        assertEquals(0, assignment[1]);
    }

    private static void assertCompleteCoverage(ModelMorphSkeleton source, ModelMorphSkeleton target,
                                               List<MappingGroup> groups)
    {
        ModelMorphData data = new ModelMorphData();

        data.setEnabled(true);
        data.setAllowIncomplete(false);
        data.setGroups(groups);

        ModelMorphValidation.Result result = ModelMorphValidation.validate(data, source, target, "source", "target");

        assertTrue(result.errors().isEmpty(), result.errors().toString());
        assertTrue(result.complete(), "automatic suggestions should cover every cube on both sides");
        assertEquals(source.cubeCount(), result.usedSources().size());
        assertEquals(target.cubeCount(), result.usedTargets().size());
    }

    /**
     * 按行构造骨架：{组路径, Cube key, originX, originY, originZ, sizeX, sizeY, sizeZ}。
     * bone 取路径最后一段，worldCenter 使用 origin + size/2 除以 16（方块单位）。
     */
    private static ModelMorphSkeleton skeleton(Object[][] rows)
    {
        Map<String, List<ModelMorphSkeleton.CubeSpec>> byPath = new LinkedHashMap<>();

        for (Object[] row : rows)
        {
            String path = (String) row[0];
            String key = (String) row[1];
            Vector3f origin = new Vector3f(
                ((Number) row[2]).floatValue(),
                ((Number) row[3]).floatValue(),
                ((Number) row[4]).floatValue());
            Vector3f size = new Vector3f(
                ((Number) row[5]).floatValue(),
                ((Number) row[6]).floatValue(),
                ((Number) row[7]).floatValue());
            List<ModelMorphSkeleton.CubeSpec> cubes = byPath.computeIfAbsent(path, ignored -> new ArrayList<>());
            Vector3f worldCenter = new Vector3f(origin).add(new Vector3f(size).mul(0.5F)).mul(1F / 16F);

            cubes.add(new ModelMorphSkeleton.CubeSpec(
                key, path, key, cubes.size(),
                origin, size, new Vector3f(), new Vector3f(),
                0F, "", worldCenter));
        }

        List<ModelMorphSkeleton.GroupSpec> groups = new ArrayList<>();

        for (Map.Entry<String, List<ModelMorphSkeleton.CubeSpec>> entry : byPath.entrySet())
        {
            String path = entry.getKey();
            String bone = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            String parentPath = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";

            groups.add(new ModelMorphSkeleton.GroupSpec(path, bone, parentPath, entry.getValue(), new Matrix4f()));
        }

        return new ModelMorphSkeleton(groups);
    }
}
