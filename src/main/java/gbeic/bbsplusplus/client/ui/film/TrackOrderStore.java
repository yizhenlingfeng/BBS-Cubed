package gbeic.bbsplusplus.client.ui.film;

import gbeic.bbsplusplus.BBSAddonsSettings;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 回放编辑器轨道排序的持久化存储。
 *
 * <p>「解锁排序」后用户在 dope sheet 名称列拖动轨道行重排；每次拖动提交时把
 * 当前视图的完整 filterKey 顺序合并进存储——当前视图的键按新顺序排在前，
 * 视图外（被分类页签/过滤器隐藏）的键保持原有相对顺序接在后面。默认视图
 * 是「全部轨道」，此时快照完整，顺序精确；在分类页签里重排会让该页签的
 * 轨道整体排到未动过的其他轨道之前，属于已知取舍。</p>
 *
 * <p>应用方式：{@link #apply} 对构建好的扁平轨道列表做稳定排序——存储表中
 * 有的键按表中序号排，没有的键保持默认相对顺序整体排在末尾。这样切分类、
 * 切回放、改过滤器等任何重建都会自动重放自定义顺序。</p>
 */
public class TrackOrderStore
{
    private static final String SEPARATOR = ",";

    /**
     * 排序解锁状态：解锁后名称列才响应左键拖动重排。仅本次会话有效，
     * 不持久化——每次启动默认回到锁定。
     */
    private static boolean unlocked;

    public static boolean isUnlocked()
    {
        return unlocked;
    }

    public static void setUnlocked(boolean value)
    {
        unlocked = value;
    }

    /** 读取存储的自定义顺序（可能包含已失效的旧键，调用方无需关心）。 */
    public static List<String> getOrder()
    {
        List<String> keys = new ArrayList<>();

        if (BBSAddonsSettings.replayTrackOrder != null)
        {
            String raw = BBSAddonsSettings.replayTrackOrder.get();

            if (raw != null && !raw.isEmpty())
            {
                for (String key : raw.split(SEPARATOR))
                {
                    if (!key.isEmpty())
                    {
                        keys.add(key);
                    }
                }
            }
        }

        return keys;
    }

    /** 清空自定义顺序，恢复目录默认排序并持久化。 */
    public static void reset()
    {
        if (BBSAddonsSettings.replayTrackOrder != null)
        {
            BBSAddonsSettings.replayTrackOrder.set("");
        }
    }

    /**
     * 把当前视图的轨道顺序合并进存储。
     * 当前视图的键按给定顺序排前，存储中视图外的键保持相对顺序接在后。
     */
    public static void saveOrder(List<UIKeyframeSheet> sheets)
    {
        if (BBSAddonsSettings.replayTrackOrder == null)
        {
            return;
        }

        LinkedHashSet<String> merged = new LinkedHashSet<>();

        for (UIKeyframeSheet sheet : sheets)
        {
            merged.add(sheet.getFilterKey());
        }

        merged.addAll(getOrder());

        BBSAddonsSettings.replayTrackOrder.set(String.join(SEPARATOR, merged));
    }

    /**
     * 对构建好的扁平轨道列表应用自定义顺序（稳定排序）。
     *
     * @return 列表顺序是否发生了变化（未变化时调用方可跳过滚动缓存刷新）
     */
    public static boolean apply(List<UIKeyframeSheet> sheets)
    {
        if (sheets.isEmpty())
        {
            return false;
        }

        List<String> order = getOrder();

        if (order.isEmpty())
        {
            return false;
        }

        Map<String, Integer> indexes = new HashMap<>();

        for (int i = 0; i < order.size(); i++)
        {
            indexes.putIfAbsent(order.get(i), i);
        }

        List<UIKeyframeSheet> before = new ArrayList<>(sheets);

        sheets.sort((a, b) ->
        {
            int ia = indexes.getOrDefault(a.getFilterKey(), Integer.MAX_VALUE);
            int ib = indexes.getOrDefault(b.getFilterKey(), Integer.MAX_VALUE);

            return Integer.compare(ia, ib);
        });

        /* UIKeyframeSheet 未重写 equals，这里即引用相等比较 */
        return !before.equals(sheets);
    }
}
