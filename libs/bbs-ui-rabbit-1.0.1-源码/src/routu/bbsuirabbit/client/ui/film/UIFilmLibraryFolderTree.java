/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSSettings
 *  mchorse.bbs_mod.l10n.L10n
 *  mchorse.bbs_mod.ui.UIKeys
 *  mchorse.bbs_mod.ui.dashboard.list.UIDataPathList
 *  mchorse.bbs_mod.ui.framework.UIContext
 *  mchorse.bbs_mod.ui.framework.elements.UIElement
 *  mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox
 *  mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer
 *  mchorse.bbs_mod.ui.utils.Scroll
 *  mchorse.bbs_mod.ui.utils.icons.Icon
 *  mchorse.bbs_mod.ui.utils.icons.Icons
 *  mchorse.bbs_mod.utils.DataPath
 *  mchorse.bbs_mod.utils.NaturalOrderComparator
 */
package routu.bbsuirabbit.client.ui.film;

import routu.bbsuirabbit.client.ui.film.FilmLibraryDefaultLocation;
import routu.bbsuirabbit.client.ui.film.IFilmLibraryPathList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.NaturalOrderComparator;

public class UIFilmLibraryFolderTree
extends UIElement {
    private static final int ROW_HEIGHT = 20;
    private static final int INDENT = 12;
    private final UITextbox search;
    private final UIDataPathList list;
    private final Actions actions;
    private final Scroll scroll;
    private final Set<String> expanded;
    private FolderRow contextRow;

    public UIFilmLibraryFolderTree(UITextbox search, UIDataPathList list, Actions actions) {
        this.scroll = new Scroll(this.area, 20);
        this.expanded = new HashSet<String>();
        this.search = search;
        this.list = list;
        this.actions = actions;
        this.context(menu -> {
            FolderRow row = this.contextRow;
            if (row == null) {
                return;
            }
            menu.action(Icons.FOLDER, UIKeys.PANELS_MODALS_ADD_FOLDER_TITLE, () -> this.actions.createFolder(row.path));
            if (!row.background) {
                menu.action(Icons.ADD, L10n.lang((String)"rabbit.ui.film.library.new_film"), () -> this.actions.createFilm(row.path));
                menu.action(Icons.FOLDER, UIKeys.PANELS_CONTEXT_OPEN, () -> this.actions.openFolder(row.path));
                if (!row.root) {
                    IFilmLibraryPathList pathList;
                    menu.action(Icons.EDIT, UIKeys.PANELS_MODALS_RENAME_FOLDER_TITLE, () -> this.actions.renameFolder(row.path));
                    UIDataPathList patt0$temp = this.list;
                    if (patt0$temp instanceof IFilmLibraryPathList && (pathList = (IFilmLibraryPathList)patt0$temp).rabbit$isFilmLibraryFolderEmpty(row.path)) {
                        menu.action(Icons.REMOVE, UIKeys.PANELS_MODALS_REMOVE_FOLDER_TITLE, () -> this.actions.deleteFolder(row.path));
                    }
                }
            } else {
                menu.action(Icons.FOLDER, UIKeys.PANELS_CONTEXT_OPEN, () -> this.actions.openFolder(row.path));
            }
            if (!FilmLibraryDefaultLocation.isFolder(row.path)) {
                menu.action(Icons.BOOKMARK, L10n.lang((String)"rabbit.ui.film.library.set_default_open"), () -> FilmLibraryDefaultLocation.setFolder(row.path));
            }
            menu.action(Icons.REFRESH, L10n.lang((String)"rabbit.ui.film.library.refresh"), this.actions::refresh);
        });
    }

    protected boolean subMouseClicked(UIContext context) {
        UIDataPathList uIDataPathList;
        if (!this.area.isInside(context) || !((uIDataPathList = this.list) instanceof IFilmLibraryPathList)) {
            return false;
        }
        IFilmLibraryPathList pathList = (IFilmLibraryPathList)uIDataPathList;
        if (this.scroll.mouseClicked(context)) {
            return true;
        }
        List<FolderRow> rows = this.buildRows(pathList.rabbit$getFilmLibraryFolders());
        int index = (int)(((double)(context.mouseY - this.area.y) + this.scroll.getScroll()) / 20.0);
        if (index < 0 || index >= rows.size()) {
            this.contextRow = new FolderRow(DataPath.EMPTY.copy(), 0, false, true, true);
            return false;
        }
        FolderRow row = rows.get(index);
        if (context.mouseButton == 1) {
            this.contextRow = row;
            this.search.setText("");
            pathList.rabbit$showFilmLibraryFolder(row.path);
            return false;
        }
        if (context.mouseButton != 0) {
            return false;
        }
        int arrowX = this.area.ex() - 18;
        if (!row.root && row.hasChildren && context.mouseX >= arrowX && context.mouseX < arrowX + 16) {
            String key = row.path.toString();
            if (this.expanded.contains(key)) {
                this.expanded.remove(key);
            } else {
                this.expanded.add(key);
            }
        } else {
            this.search.setText("");
            pathList.rabbit$showFilmLibraryFolder(row.path);
        }
        return true;
    }

    protected boolean subMouseScrolled(UIContext context) {
        return this.scroll.mouseScroll(context);
    }

    protected boolean subMouseReleased(UIContext context) {
        this.scroll.mouseReleased(context);
        return super.subMouseReleased(context);
    }

    public void render(UIContext context) {
        UIDataPathList uIDataPathList = this.list;
        if (!(uIDataPathList instanceof IFilmLibraryPathList)) {
            super.render(context);
            return;
        }
        IFilmLibraryPathList pathList = (IFilmLibraryPathList)uIDataPathList;
        List<FolderRow> rows = this.buildRows(pathList.rabbit$getFilmLibraryFolders());
        DataPath current = pathList.rabbit$getFilmLibraryFolder();
        FontRenderer font = context.batcher.getFont();
        this.scroll.setSize(rows.size());
        this.scroll.drag(context);
        context.batcher.clip(this.area, context);
        for (int i = 0; i < rows.size(); ++i) {
            int color;
            int y = this.area.y + i * 20 - (int)this.scroll.getScroll();
            if (y + 20 <= this.area.y) continue;
            if (y >= this.area.ey()) break;
            FolderRow row = rows.get(i);
            boolean active = !pathList.rabbit$isShowingAllFilmLibraryFilms() && row.path.equals((Object)current);
            int n = color = active ? -1 : -7829368;
            if (active) {
                context.batcher.box((float)this.area.x, (float)y, (float)this.area.ex(), (float)(y + 20), 0x44000000 | (Integer)BBSSettings.primaryColor.get());
            }
            int indent = row.depth * 12;
            int iconX = this.area.x + indent + 10;
            int textX = this.area.x + indent + 24;
            int arrowX = this.area.ex() - 18;
            int centerY = y + 10;
            for (int depth = 0; depth < row.depth; ++depth) {
                int lineX = this.area.x + depth * 12 + 4;
                context.batcher.box((float)lineX, (float)y, (float)(lineX + 1), (float)(y + 20), -1);
            }
            if (!row.root && row.hasChildren) {
                Icon arrow = this.expanded.contains(row.path.toString()) ? Icons.ARROW_DOWN : Icons.ARROW_RIGHT;
                context.batcher.icon(arrow, color, (float)arrowX, (float)centerY, 0.0f, 0.5f);
            }
            context.batcher.icon(Icons.FOLDER, color, (float)iconX, (float)centerY, 0.5f, 0.5f);
            String label = row.root ? L10n.lang((String)"rabbit.ui.film.library.root_folder").get() : row.path.getLast();
            int rightPadding = row.hasChildren || this.scroll.hasScrollbar() ? 22 : 4;
            String text = font.limitToWidth(label, this.area.ex() - textX - rightPadding);
            context.batcher.textShadow(text, (float)textX, (float)(y + (20 - font.getHeight()) / 2), color);
        }
        this.scroll.renderScrollbar(context.batcher);
        context.batcher.unclip(context);
        super.render(context);
    }

    private List<FolderRow> buildRows(Collection<DataPath> folders) {
        ArrayList<DataPath> sorted = new ArrayList<DataPath>(folders);
        ArrayList<FolderRow> rows = new ArrayList<FolderRow>();
        sorted.sort((a, b) -> NaturalOrderComparator.compare((boolean)true, (String)a.toString(), (String)b.toString()));
        rows.add(new FolderRow(DataPath.EMPTY.copy(), 0, false, true, false));
        for (DataPath folder : sorted) {
            if (!this.isVisible(folder)) continue;
            rows.add(new FolderRow(folder, folder.strings.size() - 1, this.hasChildren(folder, sorted), false, false));
        }
        return rows;
    }

    private boolean isVisible(DataPath folder) {
        for (int i = 1; i < folder.strings.size(); ++i) {
            DataPath parent = folder.getTo(i);
            parent.folder = true;
            if (this.expanded.contains(parent.toString())) continue;
            return false;
        }
        return true;
    }

    private boolean hasChildren(DataPath folder, List<DataPath> folders) {
        for (DataPath candidate : folders) {
            if (!candidate.startsWith(folder, 1)) continue;
            return true;
        }
        return false;
    }

    public static interface Actions {
        public void createFilm(DataPath var1);

        public void createFolder(DataPath var1);

        public void renameFolder(DataPath var1);

        public void deleteFolder(DataPath var1);

        public void openFolder(DataPath var1);

        public void refresh();
    }

    private record FolderRow(DataPath path, int depth, boolean hasChildren, boolean root, boolean background) {
    }
}
