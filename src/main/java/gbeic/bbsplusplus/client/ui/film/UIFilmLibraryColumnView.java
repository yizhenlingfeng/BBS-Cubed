package gbeic.bbsplusplus.client.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.List;

/**
 * 影片库主视图：左侧快捷栏 + 根目录列 + 向右展开的子列。
 */
public class UIFilmLibraryColumnView extends UIElement
{
    private static final int SHORTCUT_WIDTH = 152;
    private static final int COLUMN_WIDTH = 200;
    private static final int MIN_COLUMN_WIDTH = 32;
    private static final int GAP = 6;
    private static final int TOP = 24;

    private final FilmLibrarySupport support;
    private final UIFilmLibraryShortcutBar shortcuts;
    private final UITextbox search;
    private final UIButton sort;
    private final UIElement columnsHost;
    private final List<UIFilmLibraryColumn> columns = new ArrayList<>();

    private UIFilmLibraryColumn searchColumn;
    private int revision = -1;
    private int activeIndex;
    private float columnWidth = COLUMN_WIDTH;
    private float scrollX;
    private float targetScrollX;
    private String query = "";

    private DataPath dragFilm;
    private int dragStartX;
    private int dragStartY;
    private boolean dragging;

    public UIFilmLibraryColumnView(FilmLibrarySupport support)
    {
        this.support = support;
        this.shortcuts = new UIFilmLibraryShortcutBar(this);
        this.search = new UITextbox();
        this.sort = new UIButton(support.getSortLabel(), (button) -> this.toggleSort());

        this.search.placeholder(L10n.lang("bbspp.ui.film.library.search"));
        this.search.relative(this).x(SHORTCUT_WIDTH + GAP).y(0)
            .w(1F, -(SHORTCUT_WIDTH + GAP + 96)).h(20);
        this.sort.relative(this).x(1F, -90).y(0).w(84).h(20);

        this.shortcuts.relative(this).x(0).y(TOP).w(SHORTCUT_WIDTH).h(1F, -TOP);

        this.columnsHost = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                context.batcher.clip(this.area, context);
                super.render(context);
                context.batcher.unclip(context);
            }
        };
        this.columnsHost.relative(this).x(SHORTCUT_WIDTH + GAP).y(TOP)
            .w(1F, -(SHORTCUT_WIDTH + GAP)).h(1F, -TOP);

        this.add(this.search, this.sort, this.shortcuts, this.columnsHost);
        this.openRoot();
        this.search.callback = (text) -> this.setQuery(text);
    }

    public FilmLibrarySupport support()
    {
        return this.support;
    }

    public void openRoot()
    {
        List<DataPath> paths = new ArrayList<>();

        paths.add(DataPath.EMPTY.copy());
        this.setColumns(paths);
    }

    public void openFolder(UIFilmLibraryColumn parent, DataPath folder)
    {
        int index = this.columns.indexOf(parent);

        if (index < 0)
        {
            return;
        }

        while (this.columns.size() > index + 1)
        {
            this.removeColumn(this.columns.get(this.columns.size() - 1));
        }

        this.addColumn(folder);
        this.activeIndex = this.columns.size() - 1;
        this.scrollToEnd();
    }

    public void openShortcut(DataPath folder)
    {
        List<DataPath> paths = new ArrayList<>();

        paths.add(DataPath.EMPTY.copy());

        for (int i = 1; i <= folder.strings.size(); i++)
        {
            DataPath path = folder.getTo(i);

            path.folder = true;
            paths.add(path);
        }

        this.setColumns(paths);
    }

    public void togglePin(DataPath folder)
    {
        if (FilmLibraryShortcuts.isPinned(folder))
        {
            FilmLibraryShortcuts.unpin(folder);
        }
        else
        {
            FilmLibraryShortcuts.pin(folder);
        }

        this.shortcuts.refresh();
    }

    public boolean isActive(UIFilmLibraryColumn column)
    {
        return this.columns.indexOf(column) == this.activeIndex;
    }

    public boolean isValidFolder(DataPath folder)
    {
        if (folder == null || folder.strings.isEmpty())
        {
            return false;
        }

        if (this.support.getList() instanceof IFilmLibraryPathList pathList)
        {
            for (DataPath candidate : pathList.bbspp$getFilmLibraryFolders())
            {
                if (candidate.equals(folder))
                {
                    return true;
                }
            }
        }

        return false;
    }

    public void startDrag(DataPath film, UIContext context)
    {
        this.dragFilm = film;
        this.dragStartX = context.mouseX;
        this.dragStartY = context.mouseY;
        this.dragging = false;
    }

    public DataPath getDropFolder(UIContext context)
    {
        if (!this.dragging)
        {
            return null;
        }

        for (UIFilmLibraryColumn column : this.columns)
        {
            if (column.isSearchMode() || !column.area.isInside(context))
            {
                continue;
            }

            return column.getDropFolder(context);
        }

        return null;
    }

    private void setColumns(List<DataPath> paths)
    {
        while (!this.columns.isEmpty())
        {
            this.removeColumn(this.columns.get(this.columns.size() - 1));
        }

        for (DataPath path : paths)
        {
            this.addColumn(path);
        }

        this.activeIndex = this.columns.size() - 1;
        this.scrollToEnd();
    }

    private void addColumn(DataPath folder)
    {
        UIFilmLibraryColumn column = new UIFilmLibraryColumn(this, folder);

        column.relative(this.columnsHost).y(0).w(COLUMN_WIDTH).h(1F);
        column.rebuild();
        this.columnsHost.add(column);
        this.columns.add(column);
    }

    private void removeColumn(UIFilmLibraryColumn column)
    {
        if (this.searchColumn == column)
        {
            this.searchColumn = null;
        }

        column.removeFromParent();
        this.columns.remove(column);
    }

    private void toggleSort()
    {
        this.support.toggleSort();
        this.sort.label = this.support.getSortLabel();
        this.rebuild();
    }

    private void setQuery(String text)
    {
        String next = text == null ? "" : text.trim();

        if (next.equals(this.query))
        {
            return;
        }

        this.query = next;

        if (this.searchColumn != null)
        {
            this.removeColumn(this.searchColumn);
            this.searchColumn = null;
        }

        if (!this.query.isEmpty())
        {
            this.searchColumn = new UIFilmLibraryColumn(this, DataPath.EMPTY, this.query);
            this.searchColumn.relative(this.columnsHost).y(0).w(COLUMN_WIDTH).h(1F);
            this.searchColumn.rebuild();
            this.columnsHost.add(this.searchColumn);
            this.columns.add(this.searchColumn);
            this.scrollToEnd();
        }

        this.layout();
    }

    private void rebuild()
    {
        for (UIFilmLibraryColumn column : this.columns)
        {
            column.rebuild();
        }

        this.layout();
    }

    private int viewportWidth()
    {
        return Math.max(0, this.area.w - (SHORTCUT_WIDTH + GAP));
    }

    private void scrollToEnd()
    {
        this.layout();

        int viewport = this.viewportWidth();
        int minContent = this.columns.size() * MIN_COLUMN_WIDTH + Math.max(0, this.columns.size() - 1) * GAP;

        this.targetScrollX = minContent > viewport ? Math.max(0, minContent - viewport) : 0;
    }

    private int contentWidth()
    {
        int width = Math.max(1, (int) this.columnWidth);

        return this.columns.size() * width + Math.max(0, this.columns.size() - 1) * GAP;
    }

    private void layout()
    {
        int viewport = this.viewportWidth();
        int count = this.columns.size();
        int targetWidth = COLUMN_WIDTH;

        if (viewport > 0 && count > 0)
        {
            int fit = (viewport - (count - 1) * GAP) / count;

            targetWidth = Math.min(COLUMN_WIDTH, Math.max(MIN_COLUMN_WIDTH, fit));
        }

        this.columnWidth += (targetWidth - this.columnWidth) * 0.35F;

        if (Math.abs(targetWidth - this.columnWidth) < 0.5F)
        {
            this.columnWidth = targetWidth;
        }

        int width = Math.max(1, (int) this.columnWidth);
        int maxScroll = viewport > 0 ? Math.max(0, this.contentWidth() - viewport) : 0;

        this.targetScrollX = Math.max(0, Math.min(maxScroll, this.targetScrollX));

        if (maxScroll <= 0)
        {
            this.scrollX = 0;
            this.targetScrollX = 0;
        }
        else
        {
            this.scrollX += (this.targetScrollX - this.scrollX) * 0.22F;

            if (Math.abs(this.targetScrollX - this.scrollX) < 0.5F)
            {
                this.scrollX = this.targetScrollX;
            }
        }

        int x = 0;

        for (UIFilmLibraryColumn column : this.columns)
        {
            column.x((int) (x - this.scrollX));
            column.y(0);
            column.w(width);
            column.h(1F);
            column.setVisible(true);
            column.resize();

            x += width + GAP;
        }

        this.shortcuts.refresh();
    }

    @Override
    public void render(UIContext context)
    {
        int nextRevision = this.support.getRevision();

        if (nextRevision != this.revision)
        {
            this.revision = nextRevision;
            this.rebuild();
        }

        if (this.dragFilm != null)
        {
            this.dragging = Window.isMouseButtonPressed(0)
                && (Math.abs(context.mouseX - this.dragStartX) > 4 || Math.abs(context.mouseY - this.dragStartY) > 4);

            if (!Window.isMouseButtonPressed(0) && !this.dragging)
            {
                this.dragFilm = null;
            }
        }

        this.layout();
        super.render(context);

        int viewport = this.viewportWidth();
        int content = this.contentWidth();

        if (content > viewport)
        {
            int barY = this.columnsHost.area.ey() - 2;
            int barW = Math.max(20, viewport * viewport / content);
            int barX = this.columnsHost.area.x + (int) ((viewport - barW) * (this.scrollX / (content - viewport)));

            context.batcher.box(this.columnsHost.area.x, barY, this.columnsHost.area.x + viewport, barY + 2, Colors.A25 | Colors.WHITE);
            context.batcher.box(barX, barY, barX + barW, barY + 2, Colors.A100 | BBSSettings.primaryColor.get());
        }

        if (this.dragging && this.dragFilm != null)
        {
            FontRenderer font = context.batcher.getFont();
            String label = this.dragFilm.getLast();
            int width = font.getWidth(label) + 16;

            context.batcher.box(context.mouseX + 4, context.mouseY + 4, context.mouseX + width, context.mouseY + 22, Colors.A75);
            context.batcher.textShadow(label, context.mouseX + 12, context.mouseY + 8, Colors.WHITE);
        }
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (!this.columnsHost.area.isInside(context))
        {
            return false;
        }

        int maxScroll = Math.max(0, this.contentWidth() - this.viewportWidth());

        this.targetScrollX = Math.max(0, Math.min(maxScroll, this.targetScrollX + (float) (-context.mouseWheel * 40)));

        return true;
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (this.dragFilm != null)
        {
            if (Math.abs(context.mouseX - this.dragStartX) > 4 || Math.abs(context.mouseY - this.dragStartY) > 4)
            {
                this.dragging = true;
            }

            DataPath folder = this.getDropFolder(context);

            if (folder != null)
            {
                List<DataPath> films = new ArrayList<>();

                films.add(this.dragFilm);
                this.support.moveFilms(films, folder);
            }

            this.dragFilm = null;
            this.dragging = false;

            return true;
        }

        return super.subMouseReleased(context);
    }
}
