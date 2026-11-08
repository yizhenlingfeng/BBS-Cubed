package routu.bbsuirabbit.mixin;

import routu.bbsuirabbit.RabbitSettings;
import routu.bbsuirabbit.client.ui.film.FilmLibraryDefaultLocation;
import routu.bbsuirabbit.client.ui.film.IFilmLibraryLayoutToggle;
import routu.bbsuirabbit.client.ui.film.IFilmLibraryPathList;
import routu.bbsuirabbit.client.ui.film.IFilmLibrarySearchBox;
import routu.bbsuirabbit.client.ui.film.UIAllFilmsNavItem;
import routu.bbsuirabbit.client.ui.film.UIFilmLibraryFolderTree;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.UICRUDDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UIDataOverlayPanel;
import mchorse.bbs_mod.ui.film.UIFilmOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IFocusedUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.ui.utils.keys.Keybind;
import mchorse.bbs_mod.utils.DataPath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * BBS++'s film library: a sidebar ("all films" and a folder tree) beside a searchable, sortable
 * film list with a "new film" button, opened at a configurable default location.
 *
 * <p>BBS 2.5.2 showed the film list on a card of the film panel's selection screen
 * (UISelectionScreen / UIFilmSelectionPanel) and this layout replaced that card. BBS 2.7 removed
 * the selection screen: an empty film panel shows its landing page, and the film list lives in the
 * data manager overlay (UIFilmOverlayPanel), so the library is laid out in that overlay instead,
 * which is opened larger while the library is on (UICRUDDashboardPanelLibrarySizeMixin). Create,
 * duplicate, rename, remove, copy and paste are 2.7's own overlay operations.</p>
 */
@Mixin(value = UIFilmOverlayPanel.class, remap = false)
public abstract class UIFilmOverlayLibraryLayoutMixin extends UIDataOverlayPanel<Film> implements IFilmLibraryLayoutToggle
{
    @Unique
    private static final int rabbit$PRIMARY_BUTTON_WIDTH = 112;

    @Unique
    private UIElement rabbit$workspace;
    @Unique
    private UIButton rabbit$sortButton;
    @Unique
    private boolean rabbit$filmLibraryLayoutApplied;
    @Unique
    private Keybind rabbit$filmLibrarySearchKeybind;
    @Unique
    private List<Consumer<ContextMenuManager>> rabbit$originalContext;

    private UIFilmOverlayLibraryLayoutMixin(IKey title, UIDataDashboardPanel<Film> panel, Consumer<String> callback)
    {
        super(title, panel, callback);
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void rabbit$setupFilmLibraryLayout(IKey title, UIDataDashboardPanel<Film> panel, Consumer<String> callback, CallbackInfo ci)
    {
        this.rabbit$syncFilmLibraryLayout();
    }

    /** Called every frame (UIElementMixin) so toggling the setting applies right away. */
    @Override
    public void rabbit$syncFilmLibraryLayout()
    {
        if (rabbit$isNewFilmLibraryUiEnabled())
        {
            if (!this.rabbit$filmLibraryLayoutApplied)
            {
                this.rabbit$applyFilmLibraryLayout();
            }
        }
        else if (this.rabbit$filmLibraryLayoutApplied)
        {
            this.rabbit$restoreOriginalFilmLibraryLayout();
        }
    }

    @Unique
    private static boolean rabbit$isNewFilmLibraryUiEnabled()
    {
        return RabbitSettings.newFilmLibraryUi != null && RabbitSettings.newFilmLibraryUi.get();
    }

    /* Layout */

    @Unique
    private void rabbit$applyFilmLibraryLayout()
    {
        this.rabbit$filmLibraryLayoutApplied = true;

        this.add.tooltip(L10n.lang("rabbit.ui.film.library.new_film"));
        this.dupe.tooltip(L10n.lang("rabbit.ui.film.library.duplicate"));
        this.rename.tooltip(L10n.lang("rabbit.ui.film.library.rename"));
        this.remove.tooltip(L10n.lang("rabbit.ui.film.library.remove"));
        this.dupe.setVisible(false);
        this.rename.setVisible(false);
        this.remove.setVisible(false);
        this.namesList.scroll.scrollItemSize = 20;

        if (this.namesList instanceof IFilmLibraryPathList pathList)
        {
            pathList.rabbit$setFilmLibraryMode(true);
            pathList.rabbit$setFilmLibrarySortDescending(this.rabbit$isSortDescending());
            this.rabbit$rememberPhysicalFilmLibraryFolders(pathList);
            this.names.search.callback = pathList::rabbit$filterFilmLibrary;
            this.names.label(L10n.lang("rabbit.ui.film.library.search"));
            this.names.search.border().background(true);
        }

        if (this.names.search instanceof IFilmLibrarySearchBox searchBox)
        {
            searchBox.rabbit$setFilmLibrarySearchBox(true);
        }

        this.rabbit$setupFilmLibraryActions();

        /* Take the search box and the list out of the stock search list, into the library */
        this.content.remove(this.names);
        this.names.remove(this.names.search);
        this.names.remove(this.namesList);

        this.rabbit$addLibraryPanels();
    }

    @Unique
    private void rabbit$setupFilmLibraryActions()
    {
        UIElementContextAccessor accessor = (UIElementContextAccessor) this.namesList;

        this.rabbit$originalContext = accessor.rabbit$getContextOptions();
        accessor.rabbit$setContextOptions(null);

        this.namesList.context((menu) ->
        {
            DataPath selected = this.namesList.getCurrentFirst();
            boolean single = this.namesList.getCurrent().size() == 1;

            menu.action(Icons.ADD, UIKeys.GENERAL_ADD, this::rabbit$createFilmAtCurrentLocation);

            MapType clipboard = Window.getClipboardMap("_ContentType_" + this.panel.getType().getId());

            if (clipboard != null)
            {
                menu.action(Icons.PASTE, UIKeys.PANELS_CONTEXT_PASTE, () -> this.addNewData(this.getContext(), clipboard));
            }

            if (selected != null && !selected.folder)
            {
                if (single)
                {
                    menu.action(Icons.EDIT, UIKeys.GENERAL_RENAME, () -> this.renameData(this.rename));
                }

                menu.action(Icons.DUPE, UIKeys.GENERAL_DUPE, () -> this.dupeData(this.dupe));

                if (single)
                {
                    menu.action(Icons.COPY, UIKeys.PANELS_CONTEXT_COPY, this.copyPasteController::copy);
                }

                menu.action(Icons.REMOVE, UIKeys.GENERAL_REMOVE, () -> this.removeData(this.remove));
            }

            File folder = this.panel.getType().getRepository().getFolder();

            if (folder != null)
            {
                menu.action(Icons.FOLDER, UIKeys.PANELS_CONTEXT_OPEN, () ->
                {
                    DataPath target = selected != null && !selected.folder ? selected.getParent() : this.namesList.getPath();

                    UIUtils.openFolder(new File(folder, target.toString()));
                });
            }
        });

        if (this.rabbit$filmLibrarySearchKeybind == null)
        {
            this.rabbit$filmLibrarySearchKeybind = this.keys().register(new KeyCombo(UIKeys.GENERAL_SEARCH, GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_LEFT_CONTROL), () ->
            {
                UIContext context = this.names.search.getContext();

                if (context != null)
                {
                    context.focus((IFocusedUIElement) this.names.search);
                }
            }).active(() -> this.rabbit$filmLibraryLayoutApplied);
        }
    }

    @Unique
    private void rabbit$addLibraryPanels()
    {
        UIElement workspace = new UIElement();

        workspace.relative(this.content).xy(6, 6).w(1F, -12).h(1F, -12).row(10);

        UIElement sidebar = new UIElement();

        sidebar.w(190).h(1F);
        sidebar.add(new UIRenderable((ctx) -> this.rabbit$renderPanel(ctx, sidebar.area)));

        UILabel browse = this.rabbit$section("rabbit.ui.film.library.browse");
        UILabel folders = this.rabbit$section("rabbit.ui.film.library.folders");

        browse.relative(sidebar).xy(10, 10).w(1F, -20).h(14);
        folders.relative(sidebar).xy(10, 58).w(1F, -20).h(14);

        UIAllFilmsNavItem all = new UIAllFilmsNavItem(L10n.lang("rabbit.ui.film.library.all_films"), this.names.search, this.namesList);
        UIFilmLibraryFolderTree folderTree = new UIFilmLibraryFolderTree(this.names.search, this.namesList, new UIFilmLibraryFolderTree.Actions()
        {
            @Override
            public void createFilm(DataPath folder)
            {
                rabbit$switchToFolder(folder);
                rabbit$createFilmAtCurrentLocation();
            }

            @Override
            public void createFolder(DataPath folder)
            {
                rabbit$switchToFolder(folder);
                addNewFolder();
            }

            @Override
            public void renameFolder(DataPath folder)
            {
                rabbit$renameFolder(folder);
            }

            @Override
            public void deleteFolder(DataPath folder)
            {
                rabbit$deleteFolder(folder);
            }

            @Override
            public void openFolder(DataPath folder)
            {
                rabbit$openSystemFolder(folder);
            }

            @Override
            public void refresh()
            {
                if (namesList instanceof IFilmLibraryPathList pathList)
                {
                    rabbit$rememberPhysicalFilmLibraryFolders(pathList);
                }

                panel.requestNames();
            }
        });

        all.relative(sidebar).xy(8, 30).w(1F, -16).h(20);
        folderTree.relative(sidebar).xy(8, 78).w(1F, -16).h(1F, -86);
        sidebar.add(browse, all, folders, folderTree);

        UIElement content = new UIElement();
        UIElement listWrap = new UIElement();

        content.h(1F);
        content.add(new UIRenderable((ctx) -> this.rabbit$renderPanel(ctx, content.area)));

        this.rabbit$sortButton = new UIButton(this.rabbit$getSortLabel(), (b) -> this.rabbit$toggleSortMode());

        UIButton newFilm = new UIButton(L10n.lang("rabbit.ui.film.library.new_film_button"), (b) -> this.rabbit$createFilmAtCurrentLocation());

        newFilm.relative(content).x(1F, -rabbit$PRIMARY_BUTTON_WIDTH).y(0).w(rabbit$PRIMARY_BUTTON_WIDTH).h(20);
        this.rabbit$sortButton.relative(content).x(1F, -2 * rabbit$PRIMARY_BUTTON_WIDTH - 10).y(0).w(rabbit$PRIMARY_BUTTON_WIDTH).h(20);
        this.names.search.relative(content).xy(0, 0).w(1F, -2 * rabbit$PRIMARY_BUTTON_WIDTH - 20).h(20);
        listWrap.relative(content).xy(0, 22).w(1F).h(1F, -22);
        this.namesList.relative(listWrap).xy(0, 0).wh(1F, 1F);
        listWrap.add(this.namesList);
        content.add(this.names.search, this.rabbit$sortButton, newFilm, listWrap);

        workspace.add(sidebar, content);

        this.rabbit$workspace = workspace;
        this.content.add(workspace);

        this.rabbit$openDefaultFilmLibraryLocation();
        this.rabbit$requestNames();
        this.resize();
    }

    @Unique
    private void rabbit$restoreOriginalFilmLibraryLayout()
    {
        this.rabbit$filmLibraryLayoutApplied = false;

        if (this.namesList instanceof IFilmLibraryPathList pathList)
        {
            pathList.rabbit$setFilmLibraryMode(false);
        }

        if (this.names.search instanceof IFilmLibrarySearchBox searchBox)
        {
            searchBox.rabbit$setFilmLibrarySearchBox(false);
        }

        if (this.rabbit$originalContext != null)
        {
            ((UIElementContextAccessor) this.namesList).rabbit$setContextOptions(this.rabbit$originalContext);
            this.rabbit$originalContext = null;
        }

        this.names.search.callback = (str) -> this.names.filter(str, false);
        this.names.label(UIKeys.GENERAL_SEARCH);
        this.names.search.border().background(false);
        this.add.tooltip(UIKeys.GENERAL_ADD);
        this.dupe.tooltip(UIKeys.GENERAL_DUPE);
        this.rename.tooltip(UIKeys.GENERAL_RENAME);
        this.remove.tooltip(UIKeys.GENERAL_REMOVE);
        this.dupe.setVisible(true);
        this.rename.setVisible(true);
        this.remove.setVisible(true);
        this.namesList.scroll.scrollItemSize = 16;

        if (this.rabbit$workspace != null)
        {
            this.rabbit$workspace.removeFromParent();
            this.rabbit$workspace = null;
        }

        this.names.search.removeFromParent();
        this.namesList.removeFromParent();

        /* Back to the shape UICRUDOverlayPanel/UISearchList give it */
        this.names.search.relative(this.names).set(0, 0, 0, 20).w(1, 0);
        this.namesList.relative(this.names).set(0, 20, 0, 0).w(1, 0).h(1, -20);
        this.names.add(this.names.search, this.namesList);
        this.names.full(this.content).x(6).w(1F, -12);
        this.content.add(this.names);

        this.names.filter("", true);
        this.namesList.deselect();
        this.rabbit$requestNames();
        this.resize();
    }

    /**
     * The layout is first applied from this overlay's own constructor, before the panel has stored it
     * in its final "overlay" field; asking for names then makes UIDataDashboardPanel.fillNames read
     * a null overlay. The panel requests names itself once it is built, so only refresh afterwards.
     */
    @Unique
    private void rabbit$requestNames()
    {
        if (((UICRUDDashboardPanel) (Object) this.panel).overlay == (Object) this)
        {
            this.panel.requestNames();
        }
    }

    /* Actions */

    @Unique
    private void rabbit$createFilmAtCurrentLocation()
    {
        this.names.search.setText("");
        this.add.clickItself();
    }

    @Unique
    private void rabbit$switchToFolder(DataPath folder)
    {
        if (this.namesList instanceof IFilmLibraryPathList pathList)
        {
            this.names.search.setText("");
            pathList.rabbit$showFilmLibraryFolder(folder == null ? DataPath.EMPTY : folder);
        }
    }

    @Unique
    private void rabbit$openDefaultFilmLibraryLocation()
    {
        if (!(this.namesList instanceof IFilmLibraryPathList pathList))
        {
            return;
        }

        this.names.search.setText("");

        DataPath folder = FilmLibraryDefaultLocation.isAllFilms() ? null : FilmLibraryDefaultLocation.getFolder();

        if (folder == null || (!folder.strings.isEmpty() && !pathList.rabbit$getFilmLibraryFolders().contains(folder)))
        {
            pathList.rabbit$showAllFilmLibraryFilms();
        }
        else
        {
            pathList.rabbit$showFilmLibraryFolder(folder);
        }
    }

    @Unique
    private void rabbit$renameFolder(DataPath folder)
    {
        if (folder == null || folder.strings.isEmpty())
        {
            return;
        }

        UIPromptOverlayPanel overlay = new UIPromptOverlayPanel(UIKeys.PANELS_MODALS_RENAME_FOLDER_TITLE, UIKeys.PANELS_MODALS_RENAME_FOLDER, (str) -> this.rabbit$renameFolder(folder, str));

        overlay.text.setText(folder.getLast());
        overlay.text.filename();
        UIOverlay.addOverlay(this.getContext(), overlay);
    }

    @Unique
    private void rabbit$renameFolder(DataPath folder, String name)
    {
        if (name.trim().isEmpty())
        {
            this.getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);

            return;
        }

        String from = folder.toString();

        this.panel.getType().getRepository().renameFolder(from, name, (bool) ->
        {
            if (!bool)
            {
                return;
            }

            String id = this.panel.getData() == null ? null : this.panel.getData().getId();

            if (id != null && id.startsWith(from + "/"))
            {
                this.panel.getData().setId(this.rabbit$getRenamedFolderPath(folder, name) + "/" + id.substring((from + "/").length()));
            }

            this.panel.onDataFolderRenamed(from, name);

            if (this.namesList instanceof IFilmLibraryPathList pathList)
            {
                pathList.rabbit$renameKnownFilmLibraryFolder(folder, name);

                DataPath current = pathList.rabbit$getFilmLibraryFolder();

                if (!pathList.rabbit$isShowingAllFilmLibraryFilms() && current.startsWith(folder))
                {
                    pathList.rabbit$showFilmLibraryFolder(new DataPath(this.rabbit$getPathAfterFolderRename(current, folder, name)));
                }
            }

            this.panel.requestNames();
        });
    }

    @Unique
    private String rabbit$getRenamedFolderPath(DataPath folder, String name)
    {
        DataPath parent = folder.getParent();

        return parent.strings.isEmpty() ? name : parent + "/" + name;
    }

    @Unique
    private String rabbit$getPathAfterFolderRename(DataPath path, DataPath folder, String name)
    {
        String from = folder.toString();
        String renamed = this.rabbit$getRenamedFolderPath(folder, name);
        String current = path.toString();

        return current.equals(from) ? renamed : renamed + "/" + current.substring((from + "/").length());
    }

    @Unique
    private void rabbit$deleteFolder(DataPath folder)
    {
        if (folder == null || folder.strings.isEmpty())
        {
            return;
        }

        UIOverlay.addOverlay(this.getContext(), new UIConfirmOverlayPanel(UIKeys.PANELS_MODALS_REMOVE_FOLDER_TITLE, UIKeys.PANELS_MODALS_REMOVE_FOLDER, (confirm) ->
        {
            if (confirm)
            {
                this.rabbit$deleteFolderNow(folder);
            }
        }));
    }

    @Unique
    private void rabbit$deleteFolderNow(DataPath folder)
    {
        String path = folder.toString();

        this.panel.getType().getRepository().deleteFolder(path, (bool) ->
        {
            if (!bool)
            {
                this.getContext().notifyError(L10n.lang("rabbit.ui.film.library.folder_remove_failed_hidden"));

                return;
            }

            this.panel.onDataFolderRemoved(path);

            if (this.namesList instanceof IFilmLibraryPathList pathList)
            {
                pathList.rabbit$forgetFilmLibraryFolder(folder);

                DataPath current = pathList.rabbit$getFilmLibraryFolder();

                if (!pathList.rabbit$isShowingAllFilmLibraryFilms() && current.startsWith(folder))
                {
                    pathList.rabbit$showFilmLibraryFolder(folder.getParent());
                }
            }

            this.panel.requestNames();
        });
    }

    @Unique
    private void rabbit$openSystemFolder(DataPath path)
    {
        File folder = this.panel.getType().getRepository().getFolder();

        if (folder != null)
        {
            UIUtils.openFolder(new File(folder, path == null ? "" : path.toString()));
        }
    }

    @Unique
    private void rabbit$rememberPhysicalFilmLibraryFolders(IFilmLibraryPathList pathList)
    {
        File folder = this.panel.getType().getRepository().getFolder();

        if (folder != null && folder.isDirectory())
        {
            this.rabbit$rememberPhysicalFilmLibraryFolders(pathList, folder, DataPath.EMPTY);
        }
    }

    @Unique
    private void rabbit$rememberPhysicalFilmLibraryFolders(IFilmLibraryPathList pathList, File folder, DataPath path)
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
            pathList.rabbit$rememberFilmLibraryFolder(child);
            this.rabbit$rememberPhysicalFilmLibraryFolders(pathList, file, child);
        }
    }

    /* Looks */

    @Unique
    private UILabel rabbit$section(String key)
    {
        UILabel label = UI.label(L10n.lang(key), 12, 0xFFAAAAAA);

        label.labelAnchor(0F, 0.5F);

        return label;
    }

    @Unique
    private void rabbit$renderPanel(UIContext context, Area area)
    {
        int bg = BBSSettings.baseSurface();
        int border = BBSSettings.color(BBSSettings.dividerColor(), 0x44000000);

        context.batcher.box(area.x, area.y, area.ex(), area.ey(), bg);
        context.batcher.outline(area.x, area.y, area.ex(), area.ey(), border);
    }

    /* Sorting */

    @Unique
    private IKey rabbit$getSortLabel()
    {
        return this.rabbit$isSortDescending() ? L10n.lang("rabbit.ui.film.library.sort_name_desc") : L10n.lang("rabbit.ui.film.library.sort_name_asc");
    }

    @Unique
    private boolean rabbit$isSortDescending()
    {
        return RabbitSettings.filmLibrarySortMode != null && RabbitSettings.filmLibrarySortMode.get() == 1;
    }

    @Unique
    private void rabbit$toggleSortMode()
    {
        boolean descending = !this.rabbit$isSortDescending();

        if (RabbitSettings.filmLibrarySortMode != null)
        {
            RabbitSettings.filmLibrarySortMode.set(descending ? 1 : 0);
        }

        if (this.namesList instanceof IFilmLibraryPathList pathList)
        {
            pathList.rabbit$setFilmLibrarySortDescending(descending);
        }

        this.rabbit$sortButton.label = this.rabbit$getSortLabel();
    }
}
