package gbeic.bbsplusplus.client.ui.film;

import gbeic.bbsplusplus.BBSAddonsSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.NaturalOrderComparator;
import mchorse.bbs_mod.utils.repos.IRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 影片库的仓库操作集合。
 * <p>
 * 列视图只负责展示与交互，新建、重命名、删除、移动影片和文件夹等操作集中在这里，
 * 通过隐藏的 {@link UIDataPathList} 访问 BBS 的影片层级与仓库。
 * </p>
 */
public class FilmLibrarySupport
{
    private final UIDataDashboardPanel<?> panel;
    private final UIDataPathList list;

    public FilmLibrarySupport(UIDataDashboardPanel<?> panel, UIDataPathList list)
    {
        this.panel = panel;
        this.list = list;
    }

    public UIDataDashboardPanel<?> getPanel()
    {
        return this.panel;
    }

    public UIDataPathList getList()
    {
        return this.list;
    }

    /* ------------------------------------------------------------------ */
    /* 列表视图                                                            */
    /* ------------------------------------------------------------------ */

    public boolean isSortDescending()
    {
        return BBSAddonsSettings.filmLibrarySortMode != null && BBSAddonsSettings.filmLibrarySortMode.get() == 1;
    }

    public IKey getSortLabel()
    {
        return this.isSortDescending()
            ? L10n.lang("bbspp.ui.film.library.sort_name_desc")
            : L10n.lang("bbspp.ui.film.library.sort_name_asc");
    }

    public void toggleSort()
    {
        boolean descending = !this.isSortDescending();

        if (BBSAddonsSettings.filmLibrarySortMode != null)
        {
            BBSAddonsSettings.filmLibrarySortMode.set(descending ? 1 : 0);
        }

        if (this.list instanceof IFilmLibraryPathList pathList)
        {
            pathList.bbspp$setFilmLibrarySortDescending(descending);
        }
    }

    public void showFolder(DataPath folder)
    {
        if (this.list instanceof IFilmLibraryPathList pathList)
        {
            pathList.bbspp$showFilmLibraryFolder(folder == null ? DataPath.EMPTY : folder);
        }
    }

    public List<DataPath> getFolders(DataPath parent)
    {
        List<DataPath> folders = new ArrayList<>();

        if (!(this.list instanceof IFilmLibraryPathList pathList) || parent == null)
        {
            return folders;
        }

        for (DataPath folder : pathList.bbspp$getFilmLibraryFolders())
        {
            if (folder.strings.size() == parent.strings.size() + 1 && isChildOf(folder, parent))
            {
                folders.add(folder);
            }
        }

        folders.sort(this::compareNames);

        return folders;
    }

    public List<DataPath> getFilms(DataPath folder)
    {
        List<DataPath> films = new ArrayList<>();

        if (this.list instanceof IFilmLibraryPathList pathList && folder != null)
        {
            films.addAll(pathList.bbspp$getFilmLibraryFilms(folder));
            films.sort(this::compareNames);
        }

        return films;
    }

    public void openFilm(DataPath film)
    {
        if (this.list instanceof IFilmLibraryPathList pathList)
        {
            pathList.bbspp$openFilmLibraryFilm(film);
        }
    }

    public List<DataPath> searchFilms(String query)
    {
        List<DataPath> films = new ArrayList<>();

        if (query == null || query.trim().isEmpty() || !(this.list instanceof IFilmLibraryPathList pathList))
        {
            return films;
        }

        String needle = query.trim().toLowerCase();

        for (DataPath film : pathList.bbspp$getFilmLibraryAllFilms())
        {
            if (film.toString().toLowerCase().contains(needle) || film.getLast().toLowerCase().contains(needle))
            {
                films.add(film);
            }
        }

        films.sort(this::compareNames);

        return films;
    }

    public int getRevision()
    {
        return this.list instanceof IFilmLibraryPathList pathList ? pathList.bbspp$getFilmLibraryRevision() : 0;
    }

    private int compareNames(DataPath a, DataPath b)
    {
        int result = NaturalOrderComparator.compare(true, a.getLast(), b.getLast());

        return this.isSortDescending() ? -result : result;
    }

    private static boolean isChildOf(DataPath path, DataPath parent)
    {
        for (int i = 0; i < parent.strings.size(); i++)
        {
            if (!parent.strings.get(i).equals(path.strings.get(i)))
            {
                return false;
            }
        }

        return true;
    }

    public DataPath getCurrentFolder()
    {
        if (this.list instanceof IFilmLibraryPathList pathList)
        {
            return pathList.bbspp$getFilmLibraryFolder();
        }

        return DataPath.EMPTY.copy();
    }

    public void rememberPhysicalFolders()
    {
        if (!(this.list instanceof IFilmLibraryPathList pathList))
        {
            return;
        }

        File folder = this.panel.getType().getRepository().getFolder();

        if (folder != null && folder.isDirectory())
        {
            this.rememberPhysicalFolders(pathList, folder, DataPath.EMPTY);
        }
    }

    private void rememberPhysicalFolders(IFilmLibraryPathList pathList, File folder, DataPath path)
    {
        File[] files = folder.listFiles();

        if (files == null)
        {
            return;
        }

        for (File file : files)
        {
            if (!file.isDirectory() || file.getName().startsWith("_"))
            {
                continue;
            }

            DataPath child = path.getChild(file.getName());

            child.folder = true;
            pathList.bbspp$rememberFilmLibraryFolder(child);
            this.rememberPhysicalFolders(pathList, file, child);
        }
    }

    /* ------------------------------------------------------------------ */
    /* 影片                                                                */
    /* ------------------------------------------------------------------ */

    public void createFilmAtCurrentLocation()
    {
        UIContext context = this.panel.getContext();

        if (context != null)
        {
            this.panel.addNewData(context);
        }
    }

    public void createFilmIn(DataPath folder)
    {
        this.showFolder(folder);
        this.createFilmAtCurrentLocation();
    }

    /**
     * 把影片移动到目标文件夹（拖到文件夹行或列时使用）。
     * 已经是目标文件夹的影片、或目标位置已有同名影片时跳过。
     */
    public void moveFilms(List<DataPath> films, DataPath folder)
    {
        if (films == null || films.isEmpty() || folder == null)
        {
            return;
        }

        boolean moved = false;

        for (DataPath film : films)
        {
            if (film == null || film.folder)
            {
                continue;
            }

            String from = film.toString();
            String name = film.getLast();
            String to = folder.strings.isEmpty() ? name : folder + "/" + name;

            if (from.equals(to) || this.list.hasInHierarchy(to))
            {
                continue;
            }

            this.panel.getType().getRepository().rename(from, to);

            ValueGroup data = this.panel.getData();

            if (data != null && from.equals(data.getId()))
            {
                data.setId(to);
            }

            this.panel.onDataRenamed(from, to);
            moved = true;
        }

        if (moved)
        {
            this.panel.requestNames();
        }
    }

    public void renameFilm(DataPath film)
    {
        UIContext context = this.panel.getContext();

        if (context == null || film == null || film.folder)
        {
            return;
        }

        UIPromptOverlayPanel overlay = new UIPromptOverlayPanel(
            UIKeys.GENERAL_RENAME,
            UIKeys.PANELS_MODALS_RENAME,
            (name) -> this.renameFilmNow(film, name)
        );

        overlay.text.setText(film.getLast());
        overlay.text.filename();

        UIOverlay.addOverlay(context, overlay);
    }

    private void renameFilmNow(DataPath film, String name)
    {
        UIContext context = this.panel.getContext();

        if (name == null || name.trim().isEmpty())
        {
            if (context != null)
            {
                context.notifyError(UIKeys.PANELS_MODALS_EMPTY);
            }

            return;
        }

        String newName = name.trim();
        String from = film.toString();
        DataPath parent = film.getParent();
        String to = parent.strings.isEmpty() ? newName : parent + "/" + newName;

        if (from.equals(to) || this.list.hasInHierarchy(to))
        {
            return;
        }

        this.panel.getType().getRepository().rename(from, to);

        ValueGroup data = this.panel.getData();

        if (data != null && from.equals(data.getId()))
        {
            data.setId(to);
        }

        this.panel.onDataRenamed(from, to);
        this.panel.requestNames();
    }

    public void removeFilm(DataPath film)
    {
        UIContext context = this.panel.getContext();

        if (context == null || film == null || film.folder)
        {
            return;
        }

        UIConfirmOverlayPanel overlay = new UIConfirmOverlayPanel(
            UIKeys.GENERAL_REMOVE,
            UIKeys.PANELS_MODALS_REMOVE,
            (confirm) ->
            {
                if (Boolean.TRUE.equals(confirm))
                {
                    this.removeFilmNow(film);
                }
            }
        );

        UIOverlay.addOverlay(context, overlay);
    }

    private void removeFilmNow(DataPath film)
    {
        String id = film.toString();

        this.panel.getType().getRepository().delete(id);
        this.list.removeFile(id);

        ValueGroup data = this.panel.getData();

        if (data != null && id.equals(data.getId()))
        {
            this.panel.fill(null);
        }

        this.panel.onDataRemoved(id);

        /* removeFile 只处理当前文件夹直属文件；统一重新请求名称，让所有列同步刷新。 */
        this.panel.requestNames();
    }

    /* ------------------------------------------------------------------ */
    /* 文件夹                                                              */
    /* ------------------------------------------------------------------ */

    public void createFolderIn(DataPath folder)
    {
        UIContext context = this.panel.getContext();

        if (context == null)
        {
            return;
        }

        UIPromptOverlayPanel overlay = new UIPromptOverlayPanel(
            UIKeys.PANELS_MODALS_ADD_FOLDER_TITLE,
            UIKeys.PANELS_MODALS_ADD_FOLDER,
            (name) -> this.createFolderNow(folder, name)
        );

        overlay.text.filename();

        UIOverlay.addOverlay(context, overlay);
    }

    private void createFolderNow(DataPath folder, String name)
    {
        UIContext context = this.panel.getContext();

        if (name == null || name.trim().isEmpty())
        {
            if (context != null)
            {
                context.notifyError(UIKeys.PANELS_MODALS_EMPTY);
            }

            return;
        }

        String base = folder == null ? "" : folder.toString();
        String path = base.isEmpty() ? name.trim() : base + "/" + name.trim();

        this.panel.getType().getRepository().addFolder(path, (success) ->
        {
            if (Boolean.TRUE.equals(success) && this.list instanceof IFilmLibraryPathList pathList)
            {
                DataPath created = new DataPath(path);

                created.folder = true;
                pathList.bbspp$rememberFilmLibraryFolder(created);
            }

            this.panel.requestNames();
        });
    }

    public void renameFolder(DataPath folder)
    {
        UIContext context = this.panel.getContext();

        if (context == null || folder == null || folder.strings.isEmpty())
        {
            return;
        }

        UIPromptOverlayPanel overlay = new UIPromptOverlayPanel(
            UIKeys.PANELS_MODALS_RENAME_FOLDER_TITLE,
            UIKeys.PANELS_MODALS_RENAME_FOLDER,
            (name) -> this.renameFolderNow(folder, name)
        );

        overlay.text.setText(folder.getLast());
        overlay.text.filename();

        UIOverlay.addOverlay(context, overlay);
    }

    private void renameFolderNow(DataPath folder, String name)
    {
        UIContext context = this.panel.getContext();

        if (name == null || name.trim().isEmpty())
        {
            if (context != null)
            {
                context.notifyError(UIKeys.PANELS_MODALS_EMPTY);
            }

            return;
        }

        String newName = name.trim();
        String from = folder.toString();
        DataPath parent = folder.getParent();
        String to = parent.strings.isEmpty() ? newName : parent + "/" + newName;

        if (from.equals(to))
        {
            return;
        }

        IRepository<? extends ValueGroup> repository = this.panel.getType().getRepository();

        repository.renameFolder(from, to, (success) ->
        {
            if (!Boolean.TRUE.equals(success))
            {
                return;
            }

            ValueGroup data = this.panel.getData();

            if (data != null && data.getId() != null && data.getId().startsWith(from + "/"))
            {
                data.setId(to + "/" + data.getId().substring(from.length() + 1));
            }

            this.panel.onDataFolderRenamed(from, newName);

            if (this.list instanceof IFilmLibraryPathList pathList)
            {
                pathList.bbspp$renameKnownFilmLibraryFolder(folder, newName);

                DataPath current = pathList.bbspp$getFilmLibraryFolder();

                if (current.startsWith(folder))
                {
                    String suffix = current.toString().substring(from.length());

                    pathList.bbspp$showFilmLibraryFolder(new DataPath(to + suffix));
                }
            }

            this.panel.requestNames();
        });
    }

    public void deleteFolder(DataPath folder)
    {
        UIContext context = this.panel.getContext();

        if (context == null || folder == null || folder.strings.isEmpty())
        {
            return;
        }

        UIConfirmOverlayPanel overlay = new UIConfirmOverlayPanel(
            UIKeys.PANELS_MODALS_REMOVE_FOLDER_TITLE,
            UIKeys.PANELS_MODALS_REMOVE_FOLDER,
            (confirm) ->
            {
                if (Boolean.TRUE.equals(confirm))
                {
                    this.deleteFolderNow(folder);
                }
            }
        );

        UIOverlay.addOverlay(context, overlay);
    }

    private void deleteFolderNow(DataPath folder)
    {
        String path = folder.toString();

        this.panel.getType().getRepository().deleteFolder(path, (success) ->
        {
            if (!Boolean.TRUE.equals(success))
            {
                UIContext context = this.panel.getContext();

                if (context != null)
                {
                    context.notifyError(L10n.lang("bbspp.ui.film.library.folder_remove_failed_hidden"));
                }

                return;
            }

            this.panel.onDataFolderRemoved(path);

            if (this.list instanceof IFilmLibraryPathList pathList)
            {
                pathList.bbspp$forgetFilmLibraryFolder(folder);

                DataPath current = pathList.bbspp$getFilmLibraryFolder();

                if (current.startsWith(folder))
                {
                    pathList.bbspp$showFilmLibraryFolder(folder.getParent());
                }
            }

            this.panel.requestNames();
        });
    }

    public void openFolderInSystem(DataPath folder)
    {
        File root = this.panel.getType().getRepository().getFolder();

        if (root != null)
        {
            UIUtils.openFolder(new File(root, folder == null ? "" : folder.toString()));
        }
    }

    public void refresh()
    {
        this.rememberPhysicalFolders();
        this.panel.requestNames();
    }
}
