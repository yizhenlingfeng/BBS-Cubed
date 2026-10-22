package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.BBSAddonsSettings;
import gbeic.bbsplusplus.client.ui.film.FilmLibraryDragDrop;
import gbeic.bbsplusplus.client.ui.film.FilmLibrarySupport;
import gbeic.bbsplusplus.client.ui.film.IFilmLibraryFolderActionsHost;
import gbeic.bbsplusplus.client.ui.film.IFilmLibraryLayoutToggle;
import gbeic.bbsplusplus.client.ui.film.IFilmLibraryPathList;
import gbeic.bbsplusplus.client.ui.film.IFilmLibrarySearchBox;
import gbeic.bbsplusplus.client.ui.film.UIAllFilmsNavItem;
import gbeic.bbsplusplus.client.ui.film.UIFilmLibraryFolderActions;
import gbeic.bbsplusplus.client.ui.film.UIFilmLibraryFolderTree;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.landing.ILandingHost;
import mchorse.bbs_mod.ui.dashboard.panels.landing.UILandingScreen;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UIDataOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.colors.Colors;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;

/**
 * 用影片库工作区替掉影片面板的 Landing 主页。
 * <p>
 * BBS 2.7 的影片面板在没打开影片时显示 Landing 页（横幅、New film、Film list、最近影片等）。
 * 开启「全新影片库界面」后，这里把 Landing 的卡片铺满内容区，隐藏原有横幅/菜单/最近列表，
 * 直接复用影片列表浮窗的搜索列表并补上左侧文件夹树，让 Landing 本身变成影片库主页。
 * </p>
 * <p>
 * 列表与仓库操作和浮窗共用：列表直接取自 {@code UIDataDashboardPanel.overlay}，
 * 因此名称刷新、搜索、右键菜单、默认打开位置和排序全部沿用同一份状态，不会出现两套列表不同步。
 * 关闭开关时会把列表放回浮窗并还原 Landing 原始布局。
 * </p>
 */
@Mixin(value = UILandingScreen.class, remap = true)
public abstract class UIFilmLibraryLandingMixin extends UIElement implements IFilmLibraryLayoutToggle, IFilmLibraryFolderActionsHost
{
    @Unique private static final int BBSPP_SIDEBAR_WIDTH = 186;
    @Unique private static final int BBSPP_SIDEBAR_GAP = 6;
    @Unique private static final int BBSPP_CARD_W = 440;
    @Unique private static final int BBSPP_CARD_H = 360;

    @Shadow @Final private ILandingHost host;
    @Shadow @Final private UIElement card;

    @Unique private UIDataDashboardPanel<?> bbspp$panel;
    @Unique private UIDataOverlayPanel<?> bbspp$overlay;
    @Unique private UIDataPathList bbspp$list;
    @Unique private UISearchList<?> bbspp$names;
    @Unique private FilmLibrarySupport bbspp$support;
    @Unique private UIElement bbspp$library;
    @Unique private UIButton bbspp$sortButton;
    @Unique private UIFilmLibraryFolderTree bbspp$folderTree;
    @Unique private List<UIElement> bbspp$hidden;
    @Unique private boolean bbspp$applied;
    @Unique private boolean bbspp$contextAdded;
    @Unique private boolean bbspp$overlayAttached;

    @Override
    public void bbspp$syncFilmLibraryLayout()
    {
        if (!(this.host instanceof UIDataDashboardPanel<?> panel) || panel.getType() != ContentType.FILMS)
        {
            return;
        }

        boolean enabled = BBSAddonsSettings.newFilmLibraryUi != null && BBSAddonsSettings.newFilmLibraryUi.get();

        if (enabled)
        {
            if (!this.bbspp$applied)
            {
                this.bbspp$applyFilmLibraryLanding(panel);
            }
            else
            {
                /* Landing 的 refresh() 会在每次名称刷新时重新切换「打开文件夹」行的可见性，
                 * 这里保证影片库模式下原版元素始终被盖住。 */
                this.bbspp$ensureOriginalHidden();
            }
        }
        else if (this.bbspp$applied)
        {
            this.bbspp$restoreFilmLibraryLanding();
        }
    }

    @Unique
    private boolean bbspp$applyFilmLibraryLanding(UIDataDashboardPanel<?> panel)
    {
        if (!(panel.overlay instanceof UIDataOverlayPanel<?> overlay) || overlay.names == null || overlay.namesList == null)
        {
            /* 浮窗在 UICRUDDashboardPanel 构造器里创建，Landing 可能先于它被渲染一次。 */
            return false;
        }

        this.bbspp$applied = true;
        this.bbspp$panel = panel;
        this.bbspp$overlay = overlay;
        this.bbspp$list = overlay.namesList;
        this.bbspp$names = overlay.names;
        this.bbspp$support = new FilmLibrarySupport(panel, overlay.namesList);

        this.bbspp$ensureLibraryListMode();

        this.bbspp$support.rememberPhysicalFolders();
        this.bbspp$hideOriginalLanding();

        this.card.relative((UIElement) (Object) this).xy(0, 0).wh(1F, 1F).anchor(0F, 0F);

        UIElement library = new UIElement();

        library.relative(this.card).xy(0, 0).wh(1F, 1F);
        library.add(new UIRenderable((context) -> this.bbspp$renderBackground(context, library.area)));

        this.bbspp$buildSidebar(library);

        /* 列表从浮窗里借过来：名称刷新、搜索、右键菜单、默认位置全部保持同一份状态。 */
        this.bbspp$names.removeFromParent();
        this.bbspp$names.relative(library).x(BBSPP_SIDEBAR_WIDTH + BBSPP_SIDEBAR_GAP).y(0)
            .w(1F, -(BBSPP_SIDEBAR_WIDTH + BBSPP_SIDEBAR_GAP * 2)).h(1F);
        library.add(this.bbspp$names);

        this.card.add(library);
        this.bbspp$library = library;

        /* 浮窗面板本身不显示，但列表右键菜单里的原版条目（新建文件夹、剪贴板、打开文件夹）
         * 会调用 UICRUDOverlayPanel 自己的 getContext()。把浮窗挂到影片库元素下面并隐藏，
         * 既能让它沿父链拿到 Dashboard 的 UIContext，又不会渲染出来。 */
        if (!overlay.hasParent())
        {
            overlay.setVisible(false);
            library.add(overlay);
            this.bbspp$overlayAttached = true;
        }

        panel.openOverlay.setVisible(false);
        ((UIElement) (Object) this).resize();

        return true;
    }

    /**
     * 浮窗自身的 Mixin 通常已经开启影片库模式；这里只兜底处理「运行中才打开开关」的情况，
     * 避免重复设置默认打开位置（重复设置会让下一次普通刷新又跳回默认位置）。
     */
    @Unique
    private void bbspp$ensureLibraryListMode()
    {
        if (!(this.bbspp$list instanceof IFilmLibraryPathList pathList) || pathList.bbspp$isFilmLibraryMode())
        {
            return;
        }

        UISearchList<?> names = this.bbspp$names;
        UIDataPathList list = this.bbspp$list;

        pathList.bbspp$setFilmLibraryMode(true);
        pathList.bbspp$setFilmLibrarySortDescending(this.bbspp$support.isSortDescending());
        pathList.bbspp$applyFilmLibraryDefaultWhenFilled();

        if (names.search instanceof IFilmLibrarySearchBox searchBox)
        {
            searchBox.bbspp$setFilmLibrarySearchBox(true);
        }

        names.search.callback = (query) ->
        {
            if (list instanceof IFilmLibraryPathList current)
            {
                current.bbspp$filterFilmLibrary(query);
            }
        };
        names.label(L10n.lang("bbspp.ui.film.library.search"));

        this.bbspp$setupFilmLibraryContextMenu(list);

        /* 之前列表是按原版文件夹视图填充的，切换成影片库模式后需要重新请求一次，
         * 让默认打开位置和「全部影片」视图立即生效。 */
        this.bbspp$panel.requestNames();
    }

    /**
     * 浮窗 Mixin 未生效时（例如运行中才打开开关）由 Landing 补上影片库右键菜单。
     * 浮窗 Mixin 已生效时列表模式已经是开启状态，不会走到这里，因此不会重复添加。
     */
    @Unique
    private void bbspp$setupFilmLibraryContextMenu(UIDataPathList list)
    {
        if (this.bbspp$contextAdded)
        {
            return;
        }

        this.bbspp$contextAdded = true;

        list.context((menu) ->
        {
            if (BBSAddonsSettings.newFilmLibraryUi == null || !BBSAddonsSettings.newFilmLibraryUi.get() || this.bbspp$support == null)
            {
                return;
            }

            DataPath selected = list.getCurrentFirst();
            boolean single = list.getCurrent().size() == 1;

            menu.action(Icons.ADD, L10n.lang("bbspp.ui.film.library.new_film"), () -> this.bbspp$support.createFilmAtCurrentLocation());

            if (selected != null && !selected.folder)
            {
                if (single)
                {
                    menu.action(Icons.EDIT, UIKeys.GENERAL_RENAME, () -> this.bbspp$support.renameFilm(selected));
                }

                /* 复制/粘贴沿用 BBS 原版剪贴板，不再额外提供「复制影片」条目。 */
                menu.action(Icons.REMOVE, UIKeys.GENERAL_REMOVE, () -> this.bbspp$support.removeFilm(selected));
            }
        });
    }

    @Unique
    private void bbspp$hideOriginalLanding()
    {
        if (this.bbspp$hidden == null)
        {
            this.bbspp$hidden = new ArrayList<>();
        }

        this.bbspp$hidden.clear();
        this.bbspp$ensureOriginalHidden();
    }

    @Unique
    private void bbspp$ensureOriginalHidden()
    {
        if (!this.bbspp$applied)
        {
            return;
        }

        if (this.bbspp$hidden == null)
        {
            this.bbspp$hidden = new ArrayList<>();
        }

        for (IUIElement child : new ArrayList<>(this.card.getChildren()))
        {
            if (child == this.bbspp$library || !(child instanceof UIElement element))
            {
                continue;
            }

            if (element.isVisible())
            {
                element.setVisible(false);

                if (!this.bbspp$hidden.contains(element))
                {
                    this.bbspp$hidden.add(element);
                }
            }
        }
    }

    @Unique
    private void bbspp$buildSidebar(UIElement library)
    {
        UIElement sidebar = new UIElement();

        sidebar.relative(library).x(0).y(0).w(BBSPP_SIDEBAR_WIDTH).h(1F);
        sidebar.add(new UIRenderable((context) -> this.bbspp$renderSidebar(context, sidebar.area)));

        UILabel browse = this.bbspp$section("bbspp.ui.film.library.browse");
        UILabel folders = this.bbspp$section("bbspp.ui.film.library.folders");

        browse.relative(sidebar).xy(12, 6).w(1F, -24).h(14);
        folders.relative(sidebar).xy(12, 54).w(1F, -24).h(14);

        UIAllFilmsNavItem allFilms = new UIAllFilmsNavItem(L10n.lang("bbspp.ui.film.library.all_films"), this.bbspp$names.search, this.bbspp$list);
        allFilms.relative(sidebar).xy(BBSPP_SIDEBAR_GAP, 24).w(1F, -BBSPP_SIDEBAR_GAP * 2).h(20);

        UIFilmLibraryFolderTree folderTree = new UIFilmLibraryFolderTree(
            this.bbspp$names.search,
            this.bbspp$list,
            new UIFilmLibraryFolderActions((IFilmLibraryFolderActionsHost) (Object) this)
        );
        folderTree.relative(sidebar).xy(BBSPP_SIDEBAR_GAP, 72).w(1F, -BBSPP_SIDEBAR_GAP * 2).h(1F, -128);
        this.bbspp$folderTree = folderTree;
        FilmLibraryDragDrop.bind(this.bbspp$list, folderTree, this.bbspp$support);

        UIButton newFilm = new UIButton(L10n.lang("bbspp.ui.film.library.new_film_button"), (button) -> this.bbspp$support.createFilmAtCurrentLocation());
        newFilm.relative(sidebar).x(BBSPP_SIDEBAR_GAP).y(1F, -52).w(1F, -BBSPP_SIDEBAR_GAP * 2).h(20);

        this.bbspp$sortButton = new UIButton(this.bbspp$support.getSortLabel(), (button) -> this.bbspp$toggleSortMode());
        this.bbspp$sortButton.relative(sidebar).x(BBSPP_SIDEBAR_GAP).y(1F, -28).w(1F, -BBSPP_SIDEBAR_GAP * 2).h(20);

        sidebar.add(browse, allFilms, folders, folderTree, newFilm, this.bbspp$sortButton);
        library.add(sidebar);
    }

    @Unique
    private void bbspp$toggleSortMode()
    {
        this.bbspp$support.toggleSort();

        if (this.bbspp$sortButton != null)
        {
            this.bbspp$sortButton.label = this.bbspp$support.getSortLabel();
        }
    }

    @Unique
    private void bbspp$restoreFilmLibraryLanding()
    {
        this.bbspp$applied = false;

        UISearchList<?> names = this.bbspp$names;
        UIDataOverlayPanel<?> overlay = this.bbspp$overlay;

        if (this.bbspp$folderTree != null)
        {
            FilmLibraryDragDrop.unbind(this.bbspp$list, this.bbspp$folderTree);
            this.bbspp$folderTree = null;
        }

        if (this.bbspp$overlayAttached && overlay != null)
        {
            overlay.removeFromParent();
            overlay.setVisible(true);
            this.bbspp$overlayAttached = false;
        }

        if (this.bbspp$library != null)
        {
            this.bbspp$library.removeFromParent();
            this.bbspp$library = null;
        }

        if (names != null)
        {
            names.removeFromParent();
            names.search.setText("");
            names.search.callback = (query) -> names.filter(query, false);
            names.label(UIKeys.GENERAL_SEARCH);

            if (overlay != null)
            {
                names.full(overlay.content).x(6).w(1F, -12);
                overlay.content.add(names);
            }
        }

        if (this.bbspp$hidden != null)
        {
            for (UIElement element : this.bbspp$hidden)
            {
                element.setVisible(true);
            }

            this.bbspp$hidden.clear();
        }

        this.card.relative((UIElement) (Object) this).xy(0.5F, 0.5F).wh(BBSPP_CARD_W, BBSPP_CARD_H).anchor(0.5F);

        if (this.bbspp$panel != null)
        {
            this.bbspp$panel.openOverlay.setVisible(true);
        }

        if (this.bbspp$list instanceof IFilmLibraryPathList pathList)
        {
            pathList.bbspp$setFilmLibraryMode(false);
            pathList.bbspp$filterFilmLibrary("");
        }

        if (this.bbspp$names != null)
        {
            this.bbspp$names.filter("", true);
        }

        if (this.bbspp$list != null)
        {
            this.bbspp$list.deselect();
        }

        if (this.bbspp$panel != null)
        {
            this.bbspp$panel.requestNames();
        }

        this.bbspp$panel = null;
        this.bbspp$overlay = null;
        this.bbspp$list = null;
        this.bbspp$names = null;
        this.bbspp$support = null;
        this.bbspp$sortButton = null;

        ((UIElement) (Object) this).resize();
    }

    @Override
    public void bbspp$createFilmAt(DataPath folder)
    {
        this.bbspp$support.createFilmIn(folder);
    }

    @Override
    public void bbspp$createFolderAt(DataPath folder)
    {
        this.bbspp$support.createFolderIn(folder);
    }

    @Override
    public void bbspp$renameFolder(DataPath folder)
    {
        this.bbspp$support.renameFolder(folder);
    }

    @Override
    public void bbspp$deleteFolder(DataPath folder)
    {
        this.bbspp$support.deleteFolder(folder);
    }

    @Override
    public void bbspp$openFolder(DataPath folder)
    {
        this.bbspp$support.openFolderInSystem(folder);
    }

    @Override
    public void bbspp$refreshFilmLibrary()
    {
        this.bbspp$support.refresh();
    }

    @Unique
    private void bbspp$renderBackground(UIContext context, Area area)
    {
        context.batcher.box(area.x, area.y, area.ex(), area.ey(), BBSSettings.baseSurface());
    }

    @Unique
    private void bbspp$renderSidebar(UIContext context, Area area)
    {
        int background = BBSSettings.raisedSurface();
        int border = BBSSettings.color(BBSSettings.dividerColor(), Colors.A25);

        context.batcher.box(area.x, area.y, area.ex(), area.ey(), background);
        context.batcher.outline(area.x, area.y, area.ex(), area.ey(), border);
    }

    @Unique
    private UILabel bbspp$section(String key)
    {
        return this.bbspp$label(key, Colors.LIGHTER_GRAY);
    }

    @Unique
    private UILabel bbspp$label(String key, int color)
    {
        UILabel label = UI.label(L10n.lang(key), 12, color);

        label.labelAnchor(0F, 0.5F);

        return label;
    }
}
