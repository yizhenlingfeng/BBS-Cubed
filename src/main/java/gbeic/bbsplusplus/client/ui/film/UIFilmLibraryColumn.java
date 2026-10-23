package gbeic.bbsplusplus.client.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.List;

/**
 * 影片库中的一列：显示某个文件夹的子文件夹与直属影片。
 */
public class UIFilmLibraryColumn extends UIElement
{
    private static final int ROW_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 22;
    private static final int FOOTER_HEIGHT = 22;

    /** 列表底部深色衬底向下渐隐的过渡高度，文件夹列与影片列（搜索列）一致。 */
    private static final int BOTTOM_FADE = 18;

    private final UIFilmLibraryColumnView view;
    private final DataPath folder;
    private final Scroll scroll;
    private final Area listArea = new Area();
    private final List<DataPath> folders = new ArrayList<>();
    private final List<DataPath> films = new ArrayList<>();
    private final boolean searchMode;
    private final String query;

    private DataPath selected;
    private DataPath contextPath;
    private boolean contextFolder;
    private DataPath lastClickPath;
    private long lastClickTime;

    public UIFilmLibraryColumn(UIFilmLibraryColumnView view, DataPath folder)
    {
        this(view, folder, null);
    }

    public UIFilmLibraryColumn(UIFilmLibraryColumnView view, DataPath folder, String query)
    {
        this.view = view;
        this.folder = folder;
        this.query = query;
        this.searchMode = query != null;
        this.scroll = new Scroll(this.listArea, ROW_HEIGHT);

        this.context((menu) ->
        {
            if (this.contextPath == null)
            {
                return;
            }

            if (this.contextFolder)
            {
                menu.action(Icons.ADD, L10n.lang("bbspp.ui.film.library.new_folder"), () -> this.view.support().createFolderIn(this.contextPath));
                menu.action(Icons.FOLDER, L10n.lang("bbspp.ui.film.library.new_film"), () -> this.view.support().createFilmIn(this.contextPath));
                menu.action(Icons.FOLDER, UIKeys.PANELS_CONTEXT_OPEN, () -> this.view.support().openFolderInSystem(this.contextPath));

                if (!this.contextPath.strings.isEmpty())
                {
                    menu.action(Icons.EDIT, UIKeys.PANELS_MODALS_RENAME_FOLDER_TITLE, () -> this.view.support().renameFolder(this.contextPath));

                    menu.action(Icons.REMOVE, L10n.lang("bbspp.ui.film.library.remove_folder"), () -> this.openRemoveFolderMenu());
                }

                if (this.contextPath.strings.isEmpty())
                {
                    return;
                }

                menu.action(Icons.FAVORITE, L10n.lang("bbspp.ui.film.library.set_default_open"), () -> FilmLibraryDefaultLocation.setFolder(this.contextPath));

                if (FilmLibraryShortcuts.isPinned(this.contextPath))
                {
                    menu.action(Icons.FAVORITE, L10n.lang("bbspp.ui.film.library.unpin_shortcut"), () -> this.view.togglePin(this.contextPath));
                }
                else
                {
                    menu.action(Icons.FAVORITE, L10n.lang("bbspp.ui.film.library.pin_shortcut"), () -> this.view.togglePin(this.contextPath));
                }
            }
            else
            {
                menu.action(Icons.FILM, UIKeys.PANELS_CONTEXT_OPEN, () -> this.view.support().openFilm(this.contextPath));
                menu.action(Icons.EDIT, UIKeys.GENERAL_RENAME, () -> this.view.support().renameFilm(this.contextPath));
                menu.action(Icons.REMOVE, UIKeys.GENERAL_REMOVE, () -> this.view.support().removeFilm(this.contextPath));
            }
        });
    }

    public DataPath getFolder()
    {
        return this.folder;
    }

    public boolean isSearchMode()
    {
        return this.searchMode;
    }

    public void rebuild()
    {
        this.folders.clear();
        this.films.clear();

        if (this.searchMode)
        {
            this.films.addAll(this.view.support().searchFilms(this.query));
        }
        else
        {
            this.folders.addAll(this.view.support().getFolders(this.folder));
            this.films.addAll(this.view.support().getFilms(this.folder));
        }

        if (this.selected != null && !this.folders.contains(this.selected) && !this.films.contains(this.selected))
        {
            this.selected = null;
        }

        this.scroll.setSize(this.rows());
    }

    private int rows()
    {
        return this.folders.size() + this.films.size() + (this.folders.isEmpty() || this.films.isEmpty() ? 0 : 1);
    }

    private int filmOffset()
    {
        return this.folders.size() + (this.folders.isEmpty() ? 0 : 1);
    }

    @Override
    public void render(UIContext context)
    {
        this.listArea.set(this.area.x, this.area.y + HEADER_HEIGHT, this.area.w, this.area.h - HEADER_HEIGHT - FOOTER_HEIGHT);
        this.scroll.setSize(this.rows());

        int primary = BBSSettings.primaryColor.get() & Colors.RGB;
        FontRenderer font = context.batcher.getFont();
        boolean active = this.view.isActive(this);
        int deep = BBSSettings.sunkenSurface();

        /* 原版 FS 的卡片语言（对照 Landing 卡片）：投影 + 抬升面板 + 细分隔描边 */
        context.batcher.dropShadow(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 10, Colors.A50, 0);
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), BBSSettings.raisedSurface());
        context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), BBSSettings.color(BBSSettings.dividerColor(), Colors.A12));

        if (active)
        {
            context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), BBSSettings.accentOverlay(Colors.A50));
        }

        /* 顶栏：下沉色带加分隔线，激活列在左缘立一根强调条 */
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + HEADER_HEIGHT, deep);
        context.batcher.box(this.area.x, this.area.y + HEADER_HEIGHT, this.area.ex(), this.area.y + HEADER_HEIGHT + 1, BBSSettings.dividerColor());

        String title = this.searchMode
            ? L10n.lang("bbspp.ui.film.library.search_results").get()
            : (this.folder.strings.isEmpty() ? L10n.lang("bbspp.ui.film.library.root_folder").get() : this.folder.getLast());
        int titleColor = active ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.7F);

        context.batcher.textShadow(font.limitToWidth(title, this.area.w - 20), this.area.x + 10, this.area.y + 7, titleColor);

        if (active)
        {
            context.batcher.box(this.area.x, this.area.y, this.area.x + 2, this.area.y + HEADER_HEIGHT, Colors.A100 | primary);
        }

        int y = this.listArea.y - (int) this.scroll.getScroll();
        DataPath drop = this.view.getDropFolder(context);

        for (DataPath path : this.folders)
        {
            if (y >= this.listArea.y && y + ROW_HEIGHT <= this.listArea.ey())
            {
                boolean dropTarget = drop != null && drop.equals(path);
                boolean picked = this.selected != null && this.selected.equals(path);
                boolean hovered = !dropTarget && this.isRowHovered(context, y);

                if (dropTarget)
                {
                    RowStyle.dropTarget(context.batcher, this.listArea.x, y, this.listArea.w, ROW_HEIGHT);
                }
                else
                {
                    RowStyle.row(context.batcher, this.listArea.x, y, this.listArea.w, ROW_HEIGHT, 0, false, hovered, picked);
                }

                boolean lit = hovered || picked;
                boolean defaultFolder = FilmLibraryDefaultLocation.isFolder(path);
                Icon icon = defaultFolder ? Icons.FAVORITE : Icons.FOLDER;
                int iconColor = defaultFolder ? (Colors.A100 | primary) : RowStyle.iconColor(lit);
                int center = y + ROW_HEIGHT / 2;

                context.batcher.icon(icon, iconColor, this.listArea.x + 8, center, 0.5F, 0.5F);
                context.batcher.icon(Icons.ARROW_RIGHT, RowStyle.iconColor(hovered), this.listArea.ex() - 10, center, 0.5F, 0.5F);
                context.batcher.text(font.limitToWidth(path.getLast(), this.listArea.w - 36), this.listArea.x + 22,
                    y + (ROW_HEIGHT - font.getHeight()) / 2 + 1, RowStyle.textColor(lit), false);
            }

            y += ROW_HEIGHT;
        }

        if (!this.folders.isEmpty() && !this.films.isEmpty())
        {
            int divider = y + ROW_HEIGHT / 2;

            context.batcher.box(this.listArea.x + 8, divider, this.listArea.ex() - 8, divider + 1, BBSSettings.dividerColor());
            y += ROW_HEIGHT;
        }

        for (DataPath path : this.films)
        {
            if (y >= this.listArea.y && y + ROW_HEIGHT <= this.listArea.ey())
            {
                boolean dropTarget = drop != null && drop.equals(this.folder);
                boolean picked = this.selected != null && this.selected.equals(path);
                boolean hovered = !dropTarget && this.isRowHovered(context, y);

                /* 拖放到整列时，影片行随列一起亮起，但没有投放目标的强调条 */
                if (dropTarget)
                {
                    RowStyle.carried(context.batcher, this.listArea.x, y, this.listArea.w, ROW_HEIGHT);
                }
                else
                {
                    RowStyle.row(context.batcher, this.listArea.x, y, this.listArea.w, ROW_HEIGHT, 0, false, hovered, picked);
                }

                boolean lit = hovered || picked;
                int center = y + ROW_HEIGHT / 2;

                context.batcher.icon(Icons.FILM, RowStyle.iconColor(lit), this.listArea.x + 8, center, 0.5F, 0.5F);
                context.batcher.text(font.limitToWidth(path.getLast(), this.listArea.w - 30), this.listArea.x + 22,
                    y + (ROW_HEIGHT - font.getHeight()) / 2 + 1, RowStyle.textColor(lit), false);
            }

            y += ROW_HEIGHT;
        }

        this.scroll.renderScrollbar(context.batcher);

        /* 列表底部：深色衬底向下渐隐，让滚出去的行沉入暗处 */
        context.batcher.gradientVBox(this.listArea.x, this.listArea.ey() - BOTTOM_FADE, this.listArea.ex(), this.listArea.ey(),
            Colors.setA(deep, 0F), Colors.A100 | deep);

        int footerY = this.area.ey() - FOOTER_HEIGHT;

        if (!this.searchMode)
        {
            boolean hoverFooter = this.area.isInside(context.mouseX, context.mouseY) && context.mouseY >= footerY;

            context.batcher.box(this.area.x, footerY, this.area.ex(), this.area.ey(), deep);
            context.batcher.box(this.area.x, footerY, this.area.ex(), footerY + 1, BBSSettings.dividerColor());
            context.batcher.text(font.limitToWidth(L10n.lang("bbspp.ui.film.library.new_film_button").get(), this.area.w - 16),
                this.area.x + 8, footerY + (FOOTER_HEIGHT - font.getHeight()) / 2 + 1,
                hoverFooter ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.7F), false);
        }

        super.render(context);
    }

    private boolean isRowHovered(UIContext context, int y)
    {
        return this.listArea.isInside(context.mouseX, context.mouseY)
            && context.mouseY >= y
            && context.mouseY < y + ROW_HEIGHT;
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return false;
        }

        if (this.scroll.mouseClicked(context))
        {
            return true;
        }

        if (context.mouseButton == 1)
        {
            this.updateContextTarget(context);

            return false;
        }

        if (context.mouseButton != 0)
        {
            return false;
        }

        if (context.mouseY >= this.area.ey() - FOOTER_HEIGHT)
        {
            if (!this.searchMode)
            {
                this.view.support().createFilmIn(this.folder);
            }

            return true;
        }

        DataPath path = this.rowAt(context);

        if (path == null)
        {
            this.selected = null;

            return true;
        }

        if (this.isFolder(path))
        {
            this.view.openFolder(this, path);
        }
        else
        {
            this.selected = path;

            long now = System.currentTimeMillis();

            if (path.equals(this.lastClickPath) && now - this.lastClickTime <= 300)
            {
                this.view.support().openFilm(path);
                this.lastClickPath = path.copy();
                this.lastClickTime = now;

                return true;
            }

            this.lastClickPath = path.copy();
            this.lastClickTime = now;
            this.view.startDrag(path, context);
        }

        return true;
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (Window.isShiftPressed())
        {
            return false;
        }

        return this.scroll.mouseScroll(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        this.scroll.mouseReleased(context);

        return super.subMouseReleased(context);
    }

    @Override
    protected boolean mouseClickedContextMenu(UIContext context)
    {
        this.updateContextTarget(context);

        return super.mouseClickedContextMenu(context);
    }

    /**
     * 「移除文件夹」二级菜单：替换当前菜单展示两种移除方式——「从影片库移除」
     * 只把文件夹从库视图收起（不动磁盘文件）；空文件夹额外提供「移除此文件夹」
     * 物理删除（沿用原确认弹窗）。点返回或 Esc 可回到原菜单。
     */
    private void openRemoveFolderMenu()
    {
        UIContext context = this.getContext();

        if (context == null || this.contextPath == null)
        {
            return;
        }

        context.replaceContextMenu((menu) ->
        {
            menu.action(Icons.REMOVE, L10n.lang("bbspp.ui.film.library.remove_from_library"), () -> this.view.support().removeFolder(this.contextPath));

            if (this.view.support().getList() instanceof IFilmLibraryPathList pathList
                && pathList.bbspp$isFilmLibraryFolderEmpty(this.contextPath))
            {
                menu.action(Icons.TRASH, UIKeys.PANELS_MODALS_REMOVE_FOLDER_TITLE, () -> this.view.support().deleteFolder(this.contextPath));
            }
        });
    }

    private void updateContextTarget(UIContext context)
    {
        DataPath path = this.rowAt(context);

        if (path == null)
        {
            this.contextPath = this.searchMode ? null : this.folder;
            this.contextFolder = this.contextPath != null;
        }
        else
        {
            this.contextPath = path;
            this.contextFolder = this.isFolder(path);
        }
    }

    private boolean isFolder(DataPath path)
    {
        return this.folders.contains(path);
    }

    /**
     * 返回鼠标位置对应的投放文件夹；文件夹行投放到自身，影片/空白区域投放到当前列。
     */
    public DataPath getDropFolder(UIContext context)
    {
        if (this.searchMode)
        {
            return null;
        }

        DataPath path = this.rowAt(context);

        return path != null && this.isFolder(path) ? path : this.folder;
    }

    private DataPath rowAt(UIContext context)
    {
        if (!this.listArea.isInside(context))
        {
            return null;
        }

        int index = (int) ((context.mouseY - this.listArea.y + this.scroll.getScroll()) / ROW_HEIGHT);

        if (index < 0)
        {
            return null;
        }

        if (index < this.folders.size())
        {
            return this.folders.get(index);
        }

        index -= this.filmOffset();

        return index >= 0 && index < this.films.size() ? this.films.get(index) : null;
    }
}
