package gbeic.bbsplusplus.ui.film;


import gbeic.bbsplusplus.settings.ModSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPreview;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 预览工具栏「工具收纳」的注册表与持久化。
 *
 * <p>影片预览工具栏（{@link UIFilmPreview} 的 icons 横条）最右侧有一个三横线的
 * 收纳按钮：悬停向上展开收纳的工具，右键弹出清单勾选哪些工具收进去。被收纳的
 * 工具从横条隐藏（RowResizer 会跳过不可见子元素，横条自动重新居中），在展开栏
 * 里出现一个镜像按钮，点击走原按钮的完整点击管线。</p>
 *
 * <p>工具来源分两路：内置九个按钮由收纳 Mixin 在构造尾部注册；BBS++ 自己加的
 * 扩展按钮（骨骼优先级、Premiere 导出、光影控制）由各自的 Mixin 在创建按钮时
 * 调用 {@link #register} 注册。注册用 order 权重排序，因此不依赖 Mixin 的应用
 * 顺序。收纳清单持久化在隐藏设置项 {@code film_toolbar_overflow}（逗号分隔 ID），
 * UI 每次 rebuild 后由 {@link #syncVisibility} 恢复。</p>
 */
public class FilmToolbarOverflow
{
    public static final IKey OVERFLOW_TOOLTIP = L10n.lang("bbspp.ui.film.toolbar_overflow");
    public static final IKey RESET_LABEL = L10n.lang("bbspp.ui.film.toolbar_overflow_reset");

    /** 单个可收纳工具：稳定 ID + 按钮引用 + 展示名 + 排序权重 */
    public static final class Tool
    {
        public final String id;
        public final UIIcon button;
        public final IKey label;
        public final int order;

        private Tool(String id, UIIcon button, IKey label, int order)
        {
            this.id = id;
            this.button = button;
            this.label = label;
            this.order = order;
        }
    }

    private static final String SEPARATOR = ",";

    /**
     * 按预览实例隔离的注册表。UIFilmPreview 每次打开影片编辑器都会重建，
     * 用弱引用键避免持有旧实例。
     */
    private static final Map<UIFilmPreview, List<Tool>> REGISTERED = new WeakHashMap<>();

    /**
     * 注册一个可收纳工具。由收纳 Mixin（内置工具）与各扩展按钮的 Mixin 调用。
     *
     * @param order 排序权重：内置工具 0~8，扩展按钮 20 起
     */
    public static void register(UIFilmPreview preview, String id, UIIcon button, IKey label, int order)
    {
        REGISTERED.computeIfAbsent(preview, p -> new ArrayList<>()).add(new Tool(id, button, label, order));
    }

    /** 注册表快照，按 order 排序，供右键配置清单与展开栏使用。 */
    public static List<Tool> getTools(UIFilmPreview preview)
    {
        List<Tool> tools = REGISTERED.get(preview);

        if (tools == null || tools.isEmpty())
        {
            return List.of();
        }

        List<Tool> copy = new ArrayList<>(tools);

        copy.sort(Comparator.comparingInt(t -> t.order));

        return copy;
    }

    /* ---- 收纳清单（被收纳 = 从横条隐藏、进展开栏） ---- */

    /** 读取收纳清单（逗号分隔的工具 ID）。 */
    public static Set<String> getHidden()
    {
        Set<String> hidden = new LinkedHashSet<>();

        if (ModSettings.filmToolbarOverflow != null)
        {
            String raw = ModSettings.filmToolbarOverflow.get();

            if (raw != null && !raw.isEmpty())
            {
                for (String id : raw.split(SEPARATOR))
                {
                    if (!id.isEmpty())
                    {
                        hidden.add(id);
                    }
                }
            }
        }

        return hidden;
    }

    public static boolean isHidden(String id)
    {
        return getHidden().contains(id);
    }

    public static void setHidden(String id, boolean hidden)
    {
        if (ModSettings.filmToolbarOverflow == null)
        {
            return;
        }

        Set<String> ids = getHidden();

        if (hidden)
        {
            ids.add(id);
        }
        else
        {
            ids.remove(id);
        }

        ModSettings.filmToolbarOverflow.set(String.join(SEPARATOR, ids));
    }

    /** 清空收纳清单，全部工具还原到横条并持久化。 */
    public static void resetAll()
    {
        if (ModSettings.filmToolbarOverflow != null)
        {
            ModSettings.filmToolbarOverflow.set("");
        }
    }

    /**
     * 把收纳清单同步到按钮可见性（差量：仅在发生变化时重排横条）。
     * 每帧由收纳按钮的 render 驱动，开销为一次清单解析 + N 次布尔比较。
     */
    public static void syncVisibility(UIFilmPreview preview)
    {
        boolean changed = false;

        for (Tool tool : getTools(preview))
        {
            boolean visible = !isHidden(tool.id);

            if (tool.button.isVisible() != visible)
            {
                tool.button.setVisible(visible);
                changed = true;
            }
        }

        if (changed)
        {
            preview.icons.resize();
        }
    }
}
