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
import mchorse.bbs_mod.utils.repos.IRepository;

import java.io.File;
import java.util.List;

/**
 * 影片库的仓库操作集合。
 * <p>
 * 影片库现在有两个宿主：BBS 2.7 的影片列表浮窗和影片面板的 Landing 主页。两者的数据和
 * 右键菜单都基于同一个 {@link UIDataPathList}，所以把「新建/重命名/复制/删除影片」和
 * 「新建/重命名/删除文件夹」等仓库操作集中在这里，避免两套 UI 各写一份。
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

    public void showAll()
    {
        if (this.list instanceof IFilmLibraryPathList pathList)
        {
            pathList.bbspp$showAllFilmLibraryFilms();
        }
    }

    public void showFolder(DataPath folder)
    {
        if (this.list instanceof IFilmLibraryPathList pathList)
        {
            pathList.bbspp$showFilmLibraryFolder(folder == null ? DataPath.EMPTY : folder);
        }
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
     * 把影片移动到目标文件夹（右侧列表拖到左侧文件夹树时使用）。
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

        /* 「全部影片」视图下列表 path 为根，removeFile 只接受当前文件夹直属的文件名，
         * 嵌套文件夹里的影片删不掉列表项，这里统一重新请求名称以重建影片库视图。 */
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

                if (!pathList.bbspp$isShowingAllFilmLibraryFilms() && current.startsWith(folder))
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

                if (!pathList.bbspp$isShowingAllFilmLibraryFilms() && current.startsWith(folder))
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
