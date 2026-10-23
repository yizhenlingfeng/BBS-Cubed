package gbeic.bbsplusplus.client.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.List;

/**
 * 影片库最左侧的快捷栏，保存用户固定的文件夹。
 */
public class UIFilmLibraryShortcutBar extends UIElement
{
    private static final int ROW_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 22;

    /** 与影片列一致的底部深色衬底渐隐高度。 */
    private static final int BOTTOM_FADE = 18;

    private final UIFilmLibraryColumnView view;
    private final Area listArea = new Area();
    private final Scroll scroll = new Scroll(this.listArea, ROW_HEIGHT);
    private final List<DataPath> shortcuts = new ArrayList<>();
    private DataPath contextPath;

    public UIFilmLibraryShortcutBar(UIFilmLibraryColumnView view)
    {
        this.view = view;

        this.context((menu) ->
        {
            if (this.contextPath != null)
            {
                menu.action(Icons.REMOVE, L10n.lang("bbspp.ui.film.library.unpin_shortcut"), () -> this.view.togglePin(this.contextPath));
            }
        });

        this.refresh();
    }

    public void refresh()
    {
        List<DataPath> loaded = FilmLibraryShortcuts.load();
        List<DataPath> valid = new ArrayList<>();

        if (this.view.support().getList() instanceof IFilmLibraryPathList pathList
            && pathList.bbspp$getFilmLibraryFolders().isEmpty())
        {
            return;
        }

        for (DataPath path : loaded)
        {
            if (this.view.isValidFolder(path))
            {
                valid.add(path);
            }
        }

        if (!valid.equals(this.shortcuts))
        {
            this.shortcuts.clear();
            this.shortcuts.addAll(valid);
            FilmLibraryShortcuts.save(valid);
        }

        this.scroll.setSize(this.shortcuts.size());
    }

    @Override
    public void render(UIContext context)
    {
        this.listArea.set(this.area.x, this.area.y + HEADER_HEIGHT, this.area.w, this.area.h - HEADER_HEIGHT);

        int primary = BBSSettings.primaryColor.get() & Colors.RGB;
        FontRenderer font = context.batcher.getFont();

        /* 与影片列同款的卡片画法：投影 + 抬升面板 + 细分隔描边 */
        context.batcher.dropShadow(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 10, Colors.A50, 0);
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), BBSSettings.raisedSurface());
        context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), BBSSettings.color(BBSSettings.dividerColor(), Colors.A12));

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + HEADER_HEIGHT, BBSSettings.sunkenSurface());
        context.batcher.box(this.area.x, this.area.y + HEADER_HEIGHT, this.area.ex(), this.area.y + HEADER_HEIGHT + 1, BBSSettings.dividerColor());
        context.batcher.textShadow(L10n.lang("bbspp.ui.film.library.shortcuts").get(), this.area.x + 8, this.area.y + 7, Colors.setA(Colors.WHITE, 0.7F));

        context.batcher.clip(this.listArea, context);

        int y = this.listArea.y - (int) this.scroll.getScroll();

        for (DataPath path : this.shortcuts)
        {
            if (y >= this.listArea.y && y + ROW_HEIGHT <= this.listArea.ey())
            {
                boolean hovered = this.listArea.isInside(context.mouseX, context.mouseY)
                    && context.mouseY >= y
                    && context.mouseY < y + ROW_HEIGHT;

                if (hovered)
                {
                    RowStyle.hover(context.batcher, this.listArea.x, y, this.listArea.w, ROW_HEIGHT, 0);
                }

                context.batcher.icon(Icons.FAVORITE, Colors.A100 | primary, this.listArea.x + 8, y + ROW_HEIGHT / 2, 0.5F, 0.5F);
                context.batcher.text(font.limitToWidth(path.getLast(), this.listArea.w - 28), this.listArea.x + 22,
                    y + (ROW_HEIGHT - font.getHeight()) / 2 + 1, RowStyle.textColor(hovered), false);
            }

            y += ROW_HEIGHT;
        }

        if (this.shortcuts.isEmpty())
        {
            context.batcher.text(L10n.lang("bbspp.ui.film.library.shortcuts_empty").get(), this.listArea.x + 8,
                this.listArea.y + 4, Colors.setA(Colors.WHITE, 0.5F), false);
        }

        /* 底部与影片列一致：深色衬底向下渐隐 */
        int deep = BBSSettings.sunkenSurface();

        context.batcher.gradientVBox(this.listArea.x, this.listArea.ey() - BOTTOM_FADE, this.listArea.ex(), this.listArea.ey(),
            Colors.setA(deep, 0F), Colors.A100 | deep);

        this.scroll.renderScrollbar(context.batcher);
        context.batcher.unclip(context);

        super.render(context);
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

        DataPath row = this.rowAt(context);

        if (row == null)
        {
            return false;
        }

        if (context.mouseButton == 1)
        {
            this.contextPath = row;

            return false;
        }

        if (context.mouseButton == 0)
        {
            this.view.openShortcut(row);

            return true;
        }

        return false;
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
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
        this.contextPath = this.rowAt(context);

        return super.mouseClickedContextMenu(context);
    }

    private DataPath rowAt(UIContext context)
    {
        if (!this.listArea.isInside(context))
        {
            return null;
        }

        int index = (int) ((context.mouseY - this.listArea.y + this.scroll.getScroll()) / ROW_HEIGHT);

        return index >= 0 && index < this.shortcuts.size() ? this.shortcuts.get(index) : null;
    }
}
