package gbeic.bbsplusplus.util.keybind;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.settings.Settings;
import mchorse.bbs_mod.settings.value.ValueKeyCombo;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 快捷键冲突指纹索引。
 *
 * <p>数据全部来自 keybinds Settings 模块的公开 API（零反射）：
 * <pre>
 *   BBSMod.getSettings().modules.get("keybinds")
 *       -> Settings.categories -> ValueGroup.getAll() -> ValueKeyCombo.get()
 * </pre>
 * ValueKeyCombo.get() 返回的就是带 label / categoryKey 的静态 KeyCombo 本体，
 * 改绑时 copy() 仅原地更新 keys，因此索引内容始终与用户改绑保持同步。</p>
 *
 * <p>指纹 = 归一化（左右 Shift/Ctrl/Alt 折叠为同一虚拟键）后排序的键序列拼接，
 * 避免录入顺序不同导致漏判。索引缓存，改绑完成时调用 {@link #markDirty()}。</p>
 */
public final class ConflictIndex
{
    public static final String MODULE = "keybinds";

    private static volatile Map<String, List<Entry>> byFingerprint = Collections.emptyMap();
    private static volatile boolean dirty = true;
    private static long builtAt;

    private ConflictIndex() {}

    /** 一条冲突条目，指向一个真实的 ValueKeyCombo。 */
    public static final class Entry
    {
        public final ValueKeyCombo value;
        public final KeyCombo combo;
        public final String groupId;

        public Entry(ValueKeyCombo value, KeyCombo combo, String groupId)
        {
            this.value = value;
            this.combo = combo;
            this.groupId = groupId;
        }

        public String name()
        {
            return this.combo.label == null ? "?" : this.combo.label.get();
        }

        public String keys()
        {
            return this.combo.getKeyCombo();
        }
    }

    /** 改绑或设置变更后调用，使下次查询重建索引。 */
    public static void markDirty()
    {
        dirty = true;
    }

    /**
     * 查询与给定 combo 冲突的全部条目。
     *
     * @return 同指纹条目 ≥ 2 时返回全部条目（含自身对应的静态项），否则返回空列表。
     */
    public static List<Entry> conflictsOf(KeyCombo combo)
    {
        if (combo == null || combo.keys.isEmpty()) return Collections.emptyList();
        ensureBuilt();
        List<Entry> list = byFingerprint.get(fingerprint(combo));
        return list != null && list.size() >= 2 ? list : Collections.emptyList();
    }

    /** 计算指纹（归一化后排序拼接）。 */
    public static String fingerprint(KeyCombo c)
    {
        return c.keys.stream()
            .map(Integer::intValue)
            .map(ConflictIndex::normalize)
            .sorted()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
    }

    private static synchronized void ensureBuilt()
    {
        if (!dirty && System.currentTimeMillis() - builtAt < 5000L) return;

        List<Entry> all = new ArrayList<>();
        Settings s = BBSMod.getSettings().modules.get(MODULE);
        if (s != null)
        {
            for (ValueGroup group : s.categories.values())
            {
                for (BaseValue v : group.getAll())
                {
                    if (!(v instanceof ValueKeyCombo)) continue;
                    KeyCombo c = ((ValueKeyCombo) v).get();
                    if (c == null || c.keys.isEmpty() || c.label == null) continue;
                    all.add(new Entry((ValueKeyCombo) v, c, group.getId()));
                }
            }
        }

        Map<String, List<Entry>> map = new HashMap<>();
        for (Entry e : all)
        {
            map.computeIfAbsent(fingerprint(e.combo), k -> new ArrayList<>()).add(e);
        }
        byFingerprint = map;
        dirty = false;
        builtAt = System.currentTimeMillis();
    }

    private static int normalize(int key)
    {
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT) return -100;
        if (key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL) return -101;
        if (key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT) return -102;
        return key;
    }
}
