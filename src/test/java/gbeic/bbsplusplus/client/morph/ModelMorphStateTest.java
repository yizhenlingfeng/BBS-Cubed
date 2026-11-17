package gbeic.bbsplusplus.client.morph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 形变状态的瞬时/缓存分层。
 *
 * <p>回归背景：{@code clear()} 在每帧属性应用前都会执行。如果它同时丢弃
 * 采样缓冲区，形变期间就会每帧重新分配所有顶点数组，与文档承诺的「复用、不每帧分配」
 * 相矛盾。这组测试把「clear 只清瞬时状态、缓冲区按键复用」这条约定钉住。</p>
 */
public class ModelMorphStateTest
{
    private static ModelMorphData data(boolean enabled, String source, String target)
    {
        ModelMorphData data = new ModelMorphData();

        data.setEnabled(enabled);
        data.setSourceModel(source);
        data.setTargetModel(target);

        return data;
    }

    @Test
    void inactiveByDefault()
    {
        ModelMorphState state = new ModelMorphState();

        assertFalse(state.active());
        assertNull(state.ensureGeometry());
    }

    @Test
    void activeNeedsEnabledDataAndBothModels()
    {
        ModelMorphState state = new ModelMorphState();

        state.set("bed", "boat", 0.5F, data(true, "bed", "boat"));
        assertTrue(state.active());

        /* 进度 0 与 1 都不能接管：端点的外观由 BBS 自己的模型渲染负责。
           若端点也接管，会同时绘制「形变几何」与「真实模型」两套完全重合的几何，
           产生 z-fighting 与额外开销，表现为画面细微卡顿。 */
        state.set("bed", "boat", 0F, data(true, "bed", "boat"));
        assertFalse(state.active(), "进度 0 不应接管，端点交给真实模型");

        state.set("bed", "boat", 1F, data(true, "bed", "boat"));
        assertFalse(state.active(), "进度 1 不应接管，端点交给真实模型");

        /* Morph 关闭 */
        state.set("bed", "boat", 0.5F, data(false, "bed", "boat"));
        assertFalse(state.active());

        /* 缺一侧模型 */
        state.set("bed", "", 0.5F, data(true, "bed", "boat"));
        assertFalse(state.active());

        state.set("bed", "boat", 0.5F, null);
        assertFalse(state.active());
    }

    @Test
    void sameModelCanMorphWhenCubeMappingReorganizesItsGeometry()
    {
        ModelMorphState state = new ModelMorphState();
        ModelMorphData data = data(true, "model", "model");

        state.set("model", "model", 0.5F, data);

        assertTrue(state.active(), "same model IDs are valid when the mapping changes Cube geometry");
        assertEquals("model", state.sourceId());
        assertEquals("model", state.targetId());
    }

    @Test
    void progressIsClamped()
    {
        ModelMorphState state = new ModelMorphState();
        ModelMorphData data = data(true, "bed", "boat");

        state.set("bed", "boat", 2.5F, data);
        assertEquals(1F, state.progress(), 1e-6F);

        state.set("bed", "boat", -3F, data);
        assertEquals(0F, state.progress(), 1e-6F);
    }

    /**
     * 形变区间内每帧都会 clear() + set()。这里验证 clear 之后重新 set 同样的键
     * 仍然是 active 的 —— 即 clear 只清瞬时状态，不会把状态对象本身废掉。
     *
     * <p>注意：不断言 {@code ensureGeometry()} 的结果，因为它会走 BBS 的模型管理器，
     * 需要完整客户端运行时；缓冲区复用由 {@code MorphGeometry} 的实现与文档保证。</p>
     */
    @Test
    void clearThenSetKeepsStateUsable()
    {
        ModelMorphState state = new ModelMorphState();
        ModelMorphData data = data(true, "bed", "boat");

        state.set("bed", "boat", 0.4F, data);
        state.clear();

        assertFalse(state.active());
        assertEquals("", state.sourceId());
        assertEquals("", state.targetId());
        assertEquals(0F, state.progress(), 1e-6F);

        state.set("bed", "boat", 0.6F, data);

        assertTrue(state.active());
        assertEquals(0.6F, state.progress(), 1e-6F);
    }

    @Test
    void clearResetsTransientFieldsOnly()
    {
        ModelMorphState state = new ModelMorphState();

        state.set("bed", "boat", 0.5F, data(true, "bed", "boat"));
        state.clear();

        assertFalse(state.active());
        assertNull(state.data());
        assertEquals("", state.sourceId());
        assertEquals("", state.targetId());
    }

    /**
     * 配置变化（不同 data 实例 -> 不同哈希）必须让旧缓冲区失效。
     */
    @Test
    void changingConfigurationInvalidatesGeometry()
    {
        ModelMorphState state = new ModelMorphState();
        ModelMorphData first = data(true, "bed", "boat");
        ModelMorphData second = data(true, "bed", "boat");

        /* 内容相同但加了一条映射组 -> 哈希变化 */
        second.addGroup(new MappingGroup());

        state.set("bed", "boat", 0.5F, first);
        state.set("bed", "boat", 0.5F, second);

        assertFalse(first.hashCode() == second.hashCode() && first.equals(second));
    }

    @Test
    void sameInstanceIsNotActiveWithoutData()
    {
        ModelMorphState state = new ModelMorphState();

        state.set("a", "b", 1F, null);

        assertFalse(state.active());
        assertNull(state.ensureGeometry());
    }
}
