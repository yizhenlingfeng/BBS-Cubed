package gbeic.bbsplusplus.client.ui.film;

import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 影片列表与左侧文件夹树之间的拖拽投放绑定。
 * <p>
 * 列表和树分别属于 {@code UIDataOverlayPanel} / Landing 两套布局，通过这里按列表实例记录
 * 「当前可见的树 + 仓库操作」，树在鼠标释放时就能把拖拽中的影片移动到对应文件夹。
 * 绑定只存在于影片库启用期间，关闭开关时会解绑，避免普通列表误触发移动。
 * </p>
 */
public final class FilmLibraryDragDrop
{
    public record Binding(UIFilmLibraryFolderTree tree, FilmLibrarySupport support) {
    }

    private static final Map<UIDataPathList, Binding> BINDINGS = new WeakHashMap<>();

    private FilmLibraryDragDrop()
    {
    }

    public static void bind(UIDataPathList list, UIFilmLibraryFolderTree tree, FilmLibrarySupport support)
    {
        if (list != null && tree != null && support != null)
        {
            BINDINGS.put(list, new Binding(tree, support));
        }
    }

    public static void unbind(UIDataPathList list, UIFilmLibraryFolderTree tree)
    {
        Binding binding = BINDINGS.get(list);

        if (binding != null && binding.tree() == tree)
        {
            BINDINGS.remove(list);
        }
    }

    public static Binding get(UIDataPathList list)
    {
        return list == null ? null : BINDINGS.get(list);
    }

    public static boolean isDragging(UIDataPathList list)
    {
        return list != null && list.isDragging();
    }
}
