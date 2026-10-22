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
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UICRUDOverlayPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UIDataOverlayPanel;
import mchorse.bbs_mod.ui.film.UIFilmOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.colors.Colors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * 把 BBS 2.7 的影片列表浮窗改造成新版影片库工作区。
 * <p>
 * BBS 2.6 起影片选择器从 {@code UISelectionScreen} 换成 Landing + {@code UIFilmOverlayPanel}，
 * 旧版 {@code UIFilmSelectionPanelLibraryLayoutMixin} 因此整体失效。这里以
 * {@link UIDataOverlayPanel}（影片列表浮窗的实际宿主）为注入点，复用原版搜索列表与仓库操作，
 * 在其左侧补上「全部影片 / 文件夹树 / 创建影片 / 排序」导航栏。
 * </p>
 * <p>
 * 默认不改变任何行为，只有「全新影片库界面」开关打开且实例是
 * {@link UIFilmOverlayPanel} 时才重排；关闭开关会即时还原原版浮窗。
 * 真正的仓库操作集中在 {@link FilmLibrarySupport}，Landing 主页复用同一份实现。
 * </p>
 */
@Mixin(value = UIDataOverlayPanel.class, remap = true)
public abstract class UIFilmLibraryOverlayMixin extends UICRUDOverlayPanel implements IFilmLibraryLayoutToggle, IFilmLibraryFolderActionsHost
{
    @Unique private static final int BBSPP_SIDEBAR_WIDTH = 186;
    @Unique private static final int BBSPP_SIDEBAR_GAP = 6;

    @Shadow protected UIDataDashboardPanel<?> panel;

    @Unique private UIElement bbspp$sidebar;
    @Unique private UIAllFilmsNavItem bbspp$allFilmsNav;
    @Unique private UIFilmLibraryFolderTree bbspp$folderTree;
    @Unique private UIButton bbspp$newFilmButton;
    @Unique private UIButton bbspp$sortButton;
    @Unique private FilmLibrarySupport bbspp$support;
    @Unique private boolean bbspp$layoutApplied;
    @Unique private boolean bbspp$contextAdded;

    protected UIFilmLibraryOverlayMixin(IKey title, Consumer<String> callback)
    {
        super(title, callback);
    }

    /**
     * 注入目标：{@link UIDataOverlayPanel} 构造方法末尾。
     * 注入原因：影片列表浮窗在仪表盘创建时就会构造，开关可能在构造之后才被修改。
     * 修改后的行为：仅对影片列表浮窗同步一次布局；其它内容类型的浮窗不受影响。
     */
    @Inject(method = "<init>", at = @At("TAIL"), remap = true)
    private void bbspp$onOverlayInit(IKey title, UIDataDashboardPanel<?> panel, Consumer<String> callback, CallbackInfo ci)
    {
        this.bbspp$syncFilmLibraryLayout();
    }

    @Override
    public void bbspp$syncFilmLibraryLayout()
    {
        if (!((Object) this instanceof UIFilmOverlayPanel))
        {
            return;
        }

        boolean enabled = BBSAddonsSettings.newFilmLibraryUi != null && BBSAddonsSettings.newFilmLibraryUi.get();

        if (enabled)
        {
            if (!this.bbspp$layoutApplied)
            {
                this.bbspp$applyFilmLibraryLayout();
            }
        }
        else if (this.bbspp$layoutApplied)
        {
            this.bbspp$restoreFilmLibraryLayout();
        }
    }

    @Unique
    private FilmLibrarySupport bbspp$support()
    {
        if (this.bbspp$support == null)
        {
            this.bbspp$support = new FilmLibrarySupport(this.panel, this.namesList);
        }

        return this.bbspp$support;
    }

    @Unique
    private void bbspp$applyFilmLibraryLayout()
    {
        this.bbspp$layoutApplied = true;

        if (this.namesList instanceof IFilmLibraryPathList pathList)
        {
            pathList.bbspp$setFilmLibraryMode(true);
            pathList.bbspp$setFilmLibrarySortDescending(this.bbspp$support().isSortDescending());
            pathList.bbspp$applyFilmLibraryDefaultWhenFilled();
        }

        this.bbspp$support().rememberPhysicalFolders();

        if (this.names.search instanceof IFilmLibrarySearchBox searchBox)
        {
            searchBox.bbspp$setFilmLibrarySearchBox(true);
        }

        this.names.search.callback = (query) ->
        {
            if (this.namesList instanceof IFilmLibraryPathList pathList)
            {
                pathList.bbspp$filterFilmLibrary(query);
            }
        };
        this.names.label(L10n.lang("bbspp.ui.film.library.search"));

        this.dupe.setVisible(false);
        this.rename.setVisible(false);
        this.remove.setVisible(false);

        this.bbspp$setupFilmLibraryContextMenu();
        this.bbspp$addLibrarySidebar();
        this.bbspp$layoutFilmLibraryList();

        /* 浮窗在仪表盘构造期间就会被创建，此时影片面板尚未初始化完成，不能直接请求名称；
         * 面板打开时会由 UIEditorDashboardPanel 的 onOpen 自动刷新。只有运行中开关切换
         * （浮窗已在界面树里）才需要主动请求一次以重建列表。 */
        if (((UIElement) (Object) this).hasParent())
        {
            this.bbspp$ensureLibrarySize();
            this.panel.requestNames();
            ((UIElement) (Object) this).resize();
        }
    }

    /**
     * 运行中开启影片库时，浮窗可能仍是最初 200px 宽的列表尺寸。
     * 这里只在明显放不下侧栏时把浮窗放大到影片库的最小可用尺寸，不覆盖用户已调整过的较大尺寸。
     */
    @Unique
    private void bbspp$ensureLibrarySize()
    {
        UIContext context = this.getContext();
        UIElement self = (UIElement) (Object) this;

        if (context == null || context.menu == null)
        {
            return;
        }

        int minWidth = Math.max(360, Math.min(760, context.menu.width - 40));
        int minHeight = Math.max(260, Math.min(480, context.menu.height - 40));

        if (self.area.w < minWidth)
        {
            self.w(minWidth);
        }

        if (self.area.h < minHeight)
        {
            self.h(minHeight);
        }
    }

    @Unique
    private void bbspp$restoreFilmLibraryLayout()
    {
        this.bbspp$layoutApplied = false;

        if (this.namesList instanceof IFilmLibraryPathList pathList)
        {
            pathList.bbspp$setFilmLibraryMode(false);
        }

        if (this.names.search instanceof IFilmLibrarySearchBox searchBox)
        {
            searchBox.bbspp$setFilmLibrarySearchBox(false);
        }

        this.names.search.callback = (query) -> this.names.filter(query, false);
        this.names.label(UIKeys.GENERAL_SEARCH);
        this.names.search.setText("");

        this.dupe.setVisible(true);
        this.rename.setVisible(true);
        this.remove.setVisible(true);

        if (this.bbspp$sidebar != null)
        {
            this.bbspp$sidebar.removeFromParent();
            this.bbspp$sidebar = null;
        }

        if (this.bbspp$folderTree != null)
        {
            FilmLibraryDragDrop.unbind(this.namesList, this.bbspp$folderTree);
        }

        this.bbspp$allFilmsNav = null;
        this.bbspp$folderTree = null;
        this.bbspp$newFilmButton = null;
        this.bbspp$sortButton = null;

        this.names.full(this.content).x(6).w(1F, -12);

        this.names.filter("", true);
        this.namesList.deselect();
        this.panel.requestNames();

        if (((UIElement) (Object) this).hasParent())
        {
            ((UIElement) (Object) this).resize();
        }
    }

    @Unique
    private void bbspp$addLibrarySidebar()
    {
        UIElement sidebar = new UIElement();

        sidebar.relative(this.content).x(0).y(0).w(BBSPP_SIDEBAR_WIDTH).h(1F);
        sidebar.add(new UIRenderable((context) -> this.bbspp$renderSidebar(context, sidebar.area)));

        UILabel browse = this.bbspp$section("bbspp.ui.film.library.browse");
        UILabel folders = this.bbspp$section("bbspp.ui.film.library.folders");

        browse.relative(sidebar).xy(12, 4).w(1F, -24).h(14);
        folders.relative(sidebar).xy(12, 52).w(1F, -24).h(14);

        this.bbspp$allFilmsNav = new UIAllFilmsNavItem(L10n.lang("bbspp.ui.film.library.all_films"), this.names.search, this.namesList);
        this.bbspp$allFilmsNav.relative(sidebar).xy(BBSPP_SIDEBAR_GAP, 22).w(1F, -BBSPP_SIDEBAR_GAP * 2).h(20);

        this.bbspp$folderTree = new UIFilmLibraryFolderTree(
            this.names.search,
            this.namesList,
            new UIFilmLibraryFolderActions((IFilmLibraryFolderActionsHost) (Object) this)
        );
        this.bbspp$folderTree.relative(sidebar).xy(BBSPP_SIDEBAR_GAP, 70).w(1F, -BBSPP_SIDEBAR_GAP * 2).h(1F, -126);
        FilmLibraryDragDrop.bind(this.namesList, this.bbspp$folderTree, this.bbspp$support());

        this.bbspp$newFilmButton = new UIButton(L10n.lang("bbspp.ui.film.library.new_film_button"), (button) -> this.bbspp$support().createFilmAtCurrentLocation());
        this.bbspp$newFilmButton.relative(sidebar).x(BBSPP_SIDEBAR_GAP).y(1F, -50).w(1F, -BBSPP_SIDEBAR_GAP * 2).h(20);

        this.bbspp$sortButton = new UIButton(this.bbspp$support().getSortLabel(), (button) -> this.bbspp$toggleSortMode());
        this.bbspp$sortButton.relative(sidebar).x(BBSPP_SIDEBAR_GAP).y(1F, -26).w(1F, -BBSPP_SIDEBAR_GAP * 2).h(20);

        sidebar.add(browse, this.bbspp$allFilmsNav, folders, this.bbspp$folderTree, this.bbspp$newFilmButton, this.bbspp$sortButton);
        this.content.add(sidebar);

        this.bbspp$sidebar = sidebar;
    }

    @Unique
    private void bbspp$layoutFilmLibraryList()
    {
        int left = BBSPP_SIDEBAR_WIDTH + BBSPP_SIDEBAR_GAP;

        this.names.x(left).y(0).w(1F, -(left + BBSPP_SIDEBAR_GAP)).h(1F);
    }

    @Unique
    private void bbspp$toggleSortMode()
    {
        this.bbspp$support().toggleSort();

        if (this.bbspp$sortButton != null)
        {
            this.bbspp$sortButton.label = this.bbspp$support().getSortLabel();
        }
    }

    @Unique
    private void bbspp$setupFilmLibraryContextMenu()
    {
        if (this.bbspp$contextAdded)
        {
            return;
        }

        this.bbspp$contextAdded = true;

        this.namesList.context((menu) ->
        {
            /* 列表可能被 Landing 主页借走显示（浮窗本身不渲染），因此这里看开关而不是看
             * 浮窗自己的布局标记，否则运行中才开启功能时 Landing 上的右键菜单会缺项。 */
            if (BBSAddonsSettings.newFilmLibraryUi == null || !BBSAddonsSettings.newFilmLibraryUi.get())
            {
                return;
            }

            DataPath selected = this.namesList.getCurrentFirst();
            boolean single = this.namesList.getCurrent().size() == 1;

            menu.action(Icons.ADD, L10n.lang("bbspp.ui.film.library.new_film"), () -> this.bbspp$support().createFilmAtCurrentLocation());

            if (selected != null && !selected.folder)
            {
                if (single)
                {
                    menu.action(Icons.EDIT, UIKeys.GENERAL_RENAME, () -> this.bbspp$support().renameFilm(selected));
                }

                /* 复制/粘贴沿用 BBS 原版剪贴板（菜单顶部由 UIDataOverlayPanel 安装），
                 * 这里不再额外提供「复制影片」条目。 */
                menu.action(Icons.REMOVE, UIKeys.GENERAL_REMOVE, () -> this.bbspp$support().removeFilm(selected));
            }
        });
    }

    @Override
    public void bbspp$createFilmAt(DataPath folder)
    {
        this.bbspp$support().createFilmIn(folder);
    }

    @Override
    public void bbspp$createFolderAt(DataPath folder)
    {
        this.bbspp$support().createFolderIn(folder);
    }

    @Override
    public void bbspp$renameFolder(DataPath folder)
    {
        this.bbspp$support().renameFolder(folder);
    }

    @Override
    public void bbspp$deleteFolder(DataPath folder)
    {
        this.bbspp$support().deleteFolder(folder);
    }

    @Override
    public void bbspp$openFolder(DataPath folder)
    {
        this.bbspp$support().openFolderInSystem(folder);
    }

    @Override
    public void bbspp$refreshFilmLibrary()
    {
        this.bbspp$support().refresh();
    }

    @Unique
    private void bbspp$renderSidebar(UIContext context, Area area)
    {
        int background = BBSSettings.baseSurface();
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
