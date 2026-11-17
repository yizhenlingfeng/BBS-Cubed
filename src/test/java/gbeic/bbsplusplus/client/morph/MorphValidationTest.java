package gbeic.bbsplusplus.client.morph;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ModelMorphValidation} 的纯 JVM 测试：有效性检查与 1:N / N:1 展开。
 *
 * <p>骨架全部手工构造（{@link ModelMorphSkeleton} + JOML），不依赖 BBS 的 {@code Model}。</p>
 */
class MorphValidationTest
{
    @Test
    void completeMappingIsPlayableWithoutIncompleteFallback()
    {
        ModelMorphSkeleton source = skeleton(new Object[][] {
            {"body", "body:core", 0, 0, 0, 16, 16, 16}
        });
        ModelMorphSkeleton target = skeleton(new Object[][] {
            {"body", "body:core", 0, 0, 0, 8, 8, 8}
        });
        ModelMorphData data = data("src", "tgt", false,
            group(new String[] {"body:core"}, new String[] {"body:core"}));

        ModelMorphValidation.Result result = ModelMorphValidation.validate(data, source, target, "src", "tgt");

        assertTrue(result.enabled());
        assertTrue(result.complete());
        assertTrue(result.playable());
        assertTrue(result.errors().isEmpty());
        assertEquals(1, result.mappedSourceCount());
        assertEquals(1, result.mappedTargetCount());
        assertTrue(result.unmappedSources().isEmpty());
        assertTrue(result.unmappedTargets().isEmpty());
        assertEquals(1, result.groups().size());
        assertEquals(1, ModelMorphValidation.expand(result).size());
    }

    @Test
    void disabledDataIsNeitherEnabledNorPlayable()
    {
        ModelMorphSkeleton source = skeleton(new Object[][] {
            {"body", "body:core", 0, 0, 0, 16, 16, 16}
        });
        ModelMorphSkeleton target = skeleton(new Object[][] {
            {"body", "body:core", 0, 0, 0, 16, 16, 16}
        });
        ModelMorphData data = data("src", "tgt", true,
            group(new String[] {"body:core"}, new String[] {"body:core"}));

        data.setEnabled(false);

        ModelMorphValidation.Result result = ModelMorphValidation.validate(data, source, target, "src", "tgt");

        assertFalse(result.enabled());
        assertFalse(result.playable());
        assertFalse(result.complete());
        assertTrue(result.groups().isEmpty());
        assertTrue(ModelMorphValidation.expand(result).isEmpty());

        ModelMorphValidation.Result nullData = ModelMorphValidation.validate(null, source, target, "src", "tgt");

        assertFalse(nullData.enabled());
        assertFalse(nullData.playable());
    }

    @Test
    void incompleteMappingNeedsAllowIncompleteToBecomePlayable()
    {
        ModelMorphSkeleton source = skeleton(new Object[][] {
            {"body", "body:core", 0, 0, 0, 16, 16, 16},
            {"body", "body:extra", 0, 16, 0, 8, 8, 8}
        });
        ModelMorphSkeleton target = skeleton(new Object[][] {
            {"body", "body:core", 0, 0, 0, 16, 16, 16}
        });
        MappingGroup group = group(new String[] {"body:core"}, new String[] {"body:core"});

        ModelMorphValidation.Result strict =
            ModelMorphValidation.validate(data("src", "tgt", false, group), source, target, "src", "tgt");
        ModelMorphValidation.Result lenient =
            ModelMorphValidation.validate(data("src", "tgt", true, group), source, target, "src", "tgt");

        assertFalse(strict.complete());
        assertTrue(strict.errors().isEmpty());
        assertEquals(1, strict.unmappedSources().size());
        assertEquals("body:extra", strict.unmappedSources().get(0).key());
        assertFalse(strict.playable(), "incomplete mapping must not be playable when allowIncomplete is false");

        assertFalse(lenient.complete());
        assertEquals(1, lenient.unmappedSources().size());
        assertTrue(lenient.playable(), "incomplete mapping stays playable when allowIncomplete is true");
    }

    @Test
    void cubeReusedByTwoGroupsIsReportedAsConflict()
    {
        ModelMorphSkeleton source = skeleton(new Object[][] {
            {"arm", "arm:one", 0, 0, 0, 4, 4, 4},
            {"arm", "arm:two", 0, 4, 0, 4, 4, 4}
        });
        ModelMorphSkeleton target = skeleton(new Object[][] {
            {"leg", "leg:one", 0, 0, 0, 4, 4, 4},
            {"leg", "leg:two", 0, 4, 0, 4, 4, 4}
        });
        MappingGroup first = group(new String[] {"arm:one"}, new String[] {"leg:one"});
        MappingGroup second = group(new String[] {"arm:one"}, new String[] {"leg:two"});

        ModelMorphValidation.Result result = ModelMorphValidation.validate(
            data("src", "tgt", true, first, second), source, target, "src", "tgt");

        assertFalse(result.errors().isEmpty());
        assertTrue(result.errors().stream().anyMatch(message -> message.contains("conflicting mapping groups")),
            "expected a conflict error, got " + result.errors());
        assertFalse(result.complete());
        assertFalse(result.playable());
    }

    @Test
    void manyToManyGroupIsRejectedAsUnsupported()
    {
        ModelMorphSkeleton source = skeleton(new Object[][] {
            {"body", "body:a1", 0, 0, 0, 4, 4, 4},
            {"body", "body:a2", 0, 4, 0, 4, 4, 4}
        });
        ModelMorphSkeleton target = skeleton(new Object[][] {
            {"body", "body:b1", 0, 0, 0, 4, 4, 4},
            {"body", "body:b2", 0, 4, 0, 4, 4, 4}
        });
        MappingGroup group = group(
            new String[] {"body:a1", "body:a2"},
            new String[] {"body:b1", "body:b2"});

        assertEquals(MappingGroup.Type.MANY_TO_MANY, group.type());

        ModelMorphValidation.Result result = ModelMorphValidation.validate(
            data("src", "tgt", true, group), source, target, "src", "tgt");

        assertTrue(result.errors().stream()
                .anyMatch(message -> message.contains("unsupported mapping type") && message.contains("N:N")),
            "expected an unsupported N:N error, got " + result.errors());
        assertTrue(result.groups().isEmpty());
        assertFalse(result.complete());
        assertFalse(result.playable());
    }

    @Test
    void unmappedSourceAndTargetCubesAreCountedAndReported()
    {
        ModelMorphSkeleton source = skeleton(new Object[][] {
            {"arm", "arm:one", 0, 0, 0, 4, 4, 4},
            {"arm", "arm:two", 0, 4, 0, 4, 4, 4}
        });
        ModelMorphSkeleton target = skeleton(new Object[][] {
            {"leg", "leg:one", 0, 0, 0, 4, 4, 4},
            {"leg", "leg:two", 0, 4, 0, 4, 4, 4}
        });

        ModelMorphValidation.Result result = ModelMorphValidation.validate(
            data("src", "tgt", false, group(new String[] {"arm:one"}, new String[] {"leg:one"})),
            source, target, "src", "tgt");

        assertEquals(1, result.mappedSourceCount());
        assertEquals(1, result.mappedTargetCount());
        assertEquals(1, result.unmappedSources().size());
        assertEquals("arm:two", result.unmappedSources().get(0).key());
        assertEquals(1, result.unmappedTargets().size());
        assertEquals("leg:two", result.unmappedTargets().get(0).key());
        assertEquals(2, result.warnings().size());
        assertTrue(result.warnings().contains("1 source cube(s) are unmapped"));
        assertTrue(result.warnings().contains("1 target cube(s) are unmapped"));
        assertFalse(result.complete());
        assertFalse(result.playable());
    }

    /**
     * 不可播放时必须给出可读原因。
     *
     * <p>回归背景：映射合法但不完整、且用户关掉「允许不完整映射」时，errors 为空，
     * 早期日志会打印「不可播放：」后面什么都没有，现场无法判断原因。</p>
     */
    @Test
    void unplayableIncompleteMappingExplainsWhy()
    {
        ModelMorphSkeleton source = skeleton(new Object[][] {
            {"arm", "arm:one", 0, 0, 0, 4, 4, 4},
            {"arm", "arm:two", 0, 4, 0, 4, 4, 4}
        });
        ModelMorphSkeleton target = skeleton(new Object[][] {
            {"leg", "leg:one", 0, 0, 0, 4, 4, 4}
        });

        ModelMorphValidation.Result result = ModelMorphValidation.validate(
            data("src", "tgt", false, group(new String[] {"arm:one"}, new String[] {"leg:one"})),
            source, target, "src", "tgt");

        assertTrue(result.errors().isEmpty());
        assertFalse(result.playable());
        assertTrue(result.fallbackReason().contains("incomplete"), result.fallbackReason());
        assertTrue(result.fallbackReason().contains("allow incomplete"), result.fallbackReason());
    }

    /**
     * 没有任何映射组时，原因应明确说「没有映射组」，而不是留空。
     */
    @Test
    void unplayableWithoutGroupsExplainsWhy()
    {
        ModelMorphSkeleton source = skeleton(new Object[][] {{"arm", "arm:one", 0, 0, 0, 4, 4, 4}});
        ModelMorphSkeleton target = skeleton(new Object[][] {{"leg", "leg:one", 0, 0, 0, 4, 4, 4}});
        ModelMorphValidation.Result result = ModelMorphValidation.validate(
            data("src", "tgt", false), source, target, "src", "tgt");

        assertFalse(result.playable());
        assertEquals("no mapping groups", result.fallbackReason());
    }

    @Test
    void sourceModelIdMismatchIsOnlyAWarning()
    {
        ModelMorphSkeleton source = skeleton(new Object[][] {
            {"body", "body:core", 0, 0, 0, 16, 16, 16}
        });
        ModelMorphSkeleton target = skeleton(new Object[][] {
            {"body", "body:core", 0, 0, 0, 16, 16, 16}
        });
        ModelMorphData data = data("Z", "tgt", false,
            group(new String[] {"body:core"}, new String[] {"body:core"}));

        ModelMorphValidation.Result result = ModelMorphValidation.validate(data, source, target, "X", "tgt");

        /* 模型 id 漂移不应阻塞播放：映射记录的是 Cube 的稳定 key 与几何指纹，
           只要这些仍能解析，形变就能正常工作。 */
        assertTrue(result.errors().isEmpty(), "模型 id 漂移不应是错误: " + result.errors());
        assertEquals(1, result.warnings().size(), "应产生一条警告: " + result.warnings());
        assertTrue(result.warnings().get(0).contains("source model changed"));
        assertTrue(result.playable(), "模型 id 漂移后仍应可播放");
    }

    @Test
    void oneToManyExpandsToTransitionsSharingOneSource()
    {
        ModelMorphSkeleton source = skeleton(new Object[][] {
            {"body", "body:core", 0, 0, 0, 16, 16, 16}
        });
        ModelMorphSkeleton target = skeleton(new Object[][] {
            {"body", "body:left", 0, 0, 0, 8, 8, 8},
            {"body", "body:right", 8, 0, 0, 8, 8, 8}
        });
        MappingGroup group = group(
            new String[] {"body:core"},
            new String[] {"body:left", "body:right"});

        assertEquals(MappingGroup.Type.ONE_TO_MANY, group.type());

        ModelMorphValidation.Result result = ModelMorphValidation.validate(
            data("src", "tgt", false, group), source, target, "src", "tgt");

        assertTrue(result.playable());
        assertTrue(result.complete());

        Map<String, List<ModelMorphValidation.Transition>> transitions = ModelMorphValidation.expand(result);

        assertEquals(1, transitions.size(), "one source must stay a single key");
        List<ModelMorphValidation.Transition> list = transitions.get("body:core");

        assertNotNull(list);
        assertEquals(2, list.size());
        assertEquals("body:core", list.get(0).source().key());
        assertEquals("body:core", list.get(1).source().key());
        assertEquals("body:left", list.get(0).target().key());
        assertEquals("body:right", list.get(1).target().key());
    }

    @Test
    void manyToOneExpandsToTransitionsSharingOneTarget()
    {
        ModelMorphSkeleton source = skeleton(new Object[][] {
            {"body", "body:left", 0, 0, 0, 8, 8, 8},
            {"body", "body:right", 8, 0, 0, 8, 8, 8}
        });
        ModelMorphSkeleton target = skeleton(new Object[][] {
            {"body", "body:core", 0, 0, 0, 16, 16, 16}
        });
        MappingGroup group = group(
            new String[] {"body:left", "body:right"},
            new String[] {"body:core"});

        assertEquals(MappingGroup.Type.MANY_TO_ONE, group.type());

        ModelMorphValidation.Result result = ModelMorphValidation.validate(
            data("src", "tgt", false, group), source, target, "src", "tgt");

        assertTrue(result.playable());
        assertTrue(result.complete());

        Map<String, List<ModelMorphValidation.Transition>> transitions = ModelMorphValidation.expand(result);

        assertEquals(2, transitions.size());
        assertNotNull(transitions.get("body:left"));
        assertNotNull(transitions.get("body:right"));
        assertEquals(1, transitions.get("body:left").size());
        assertEquals(1, transitions.get("body:right").size());
        assertEquals("body:core", transitions.get("body:left").get(0).target().key());
        assertEquals("body:core", transitions.get("body:right").get(0).target().key());
    }

    @Test
    void emptySkeletonIsNotPlayable()
    {
        ModelMorphSkeleton source = new ModelMorphSkeleton(List.of());
        ModelMorphSkeleton target = skeleton(new Object[][] {
            {"body", "body:core", 0, 0, 0, 16, 16, 16}
        });

        ModelMorphValidation.Result result = ModelMorphValidation.validate(
            data("src", "tgt", true, group(new String[] {"body:core"}, new String[] {"body:core"})),
            source, target, "src", "tgt");

        assertTrue(result.enabled());
        assertTrue(result.errors().contains("source or target model has no cubes"));
        assertFalse(result.complete());
        assertFalse(result.playable());
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

    private static MappingGroup group(String[] sourceKeys, String[] targetKeys)
    {
        MappingGroup group = new MappingGroup();

        group.setSources(refs(sourceKeys));
        group.setTargets(refs(targetKeys));

        return group;
    }

    private static List<MappingGroup.CubeRef> refs(String[] keys)
    {
        List<MappingGroup.CubeRef> refs = new ArrayList<>();

        for (String key : keys)
        {
            refs.add(new MappingGroup.CubeRef(key, ""));
        }

        return refs;
    }

    private static ModelMorphData data(String sourceModel, String targetModel, boolean allowIncomplete,
                                       MappingGroup... groups)
    {
        ModelMorphData data = new ModelMorphData();

        data.setEnabled(true);
        data.setSourceModel(sourceModel);
        data.setTargetModel(targetModel);
        data.setAllowIncomplete(allowIncomplete);
        data.setGroups(List.of(groups));

        return data;
    }

    /**
     * 换了模型导致整组映射都解析不到时，应给出<b>可操作</b>的提示，
     * 而不是一串难以理解的内部 key。
     *
     * <p>回归背景：用户把源模型从 bed/boat 换成别的模型后，旧映射组里的
     * {@code front:#0} 之类的 key 全部失效，界面只显示
     * 「无效: source cube not found: front:#0」，无法判断该怎么办。</p>
     */
    @Test
    void staleMappingGroupGivesActionableMessage()
    {
        ModelMorphSkeleton source = skeleton(new Object[][] {
            {"body", "body:core", 0, 0, 0, 16, 16, 16}
        });
        ModelMorphSkeleton target = skeleton(new Object[][] {
            {"body", "body:core", 0, 0, 0, 16, 16, 16}
        });
        ModelMorphData data = data("src", "tgt", false,
            group(new String[] {"front:#0", "left:#0"}, new String[] {"body:core"}));

        ModelMorphValidation.Result result = ModelMorphValidation.validate(data, source, target, "src", "tgt");

        assertEquals(1, result.errors().size(), "整组失效应折叠成一条提示: " + result.errors());
        String message = result.errors().get(0);
        assertTrue(message.contains("no longer exist"), "应说明 cube 已不存在: " + message);
        assertTrue(message.contains("clear the mapping"), "应给出可操作建议: " + message);
        assertFalse(result.playable());
    }

    /**
     * 个别 key 写错时仍应保留精确报错，便于定位。
     */
    @Test
    void partiallyStaleMappingKeepsPreciseError()
    {
        ModelMorphSkeleton source = skeleton(new Object[][] {
            {"body", "body:core", 0, 0, 0, 16, 16, 16}
        });
        ModelMorphSkeleton target = skeleton(new Object[][] {
            {"body", "body:core", 0, 0, 0, 16, 16, 16}
        });
        ModelMorphData data = data("src", "tgt", false,
            group(new String[] {"body:core", "front:#0"}, new String[] {"body:core"}));

        ModelMorphValidation.Result result = ModelMorphValidation.validate(data, source, target, "src", "tgt");

        assertTrue(result.errors().stream().anyMatch(m -> m.contains("source cube not found: front:#0")),
            "部分失效时应保留精确报错: " + result.errors());
    }

    /**
     * 形变目标必须与区间结束时的实际模型一致。
     *
     * <p>回归背景：影片 dwadwadwa.dat 里 tick 58 的关键帧把 {@code target_model}
     * 记成了 {@code cube}（一个只有 config.json 的空模型），而下一帧实际切换到的是
     * {@code model_morph_test/cube_test}。形变几何长成 A 的形状、纹理采样 A 的贴图，
     * 区间结束却硬切到 B，画面因此突兀。</p>
     */
    @Test
    void morphTargetMustMatchTheIntervalEndpoint()
    {
        ModelMorphSkeleton source = skeleton(new Object[][] {
            {"body", "body:core", 0, 0, 0, 16, 16, 16}
        });
        ModelMorphSkeleton target = skeleton(new Object[][] {
            {"body", "body:core", 0, 0, 0, 16, 16, 16}
        });
        ModelMorphData data = data("src", "recorded-target", false,
            group(new String[] {"body:core"}, new String[] {"body:core"}));

        ModelMorphValidation.Result result = ModelMorphValidation.validate(
            data, source, target, "src", "recorded-target", "actual/endpoint");

        assertTrue(result.playable(), "端点不一致不应阻塞播放");
        assertTrue(result.warnings().stream().anyMatch(m -> m.contains("differs from the model the interval ends on")),
            "应提示形变目标与终点模型不一致: " + result.warnings());
    }

    /**
     * 端点一致时不应产生该警告。
     */
    @Test
    void matchingEndpointProducesNoMismatchWarning()
    {
        ModelMorphSkeleton source = skeleton(new Object[][] {
            {"body", "body:core", 0, 0, 0, 16, 16, 16}
        });
        ModelMorphSkeleton target = skeleton(new Object[][] {
            {"body", "body:core", 0, 0, 0, 16, 16, 16}
        });
        ModelMorphData data = data("src", "tgt", false,
            group(new String[] {"body:core"}, new String[] {"body:core"}));

        ModelMorphValidation.Result result = ModelMorphValidation.validate(data, source, target, "src", "tgt", "tgt");

        assertTrue(result.warnings().stream().noneMatch(m -> m.contains("differs from the model")),
            "端点一致时不应提示: " + result.warnings());
    }
}
