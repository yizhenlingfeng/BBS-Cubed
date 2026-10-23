package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.BBSAddonsSettings;
import gbeic.bbsplusplus.client.ui.film.FilmLibrarySupport;
import gbeic.bbsplusplus.client.ui.film.IFilmLibraryLayoutToggle;
import gbeic.bbsplusplus.client.ui.film.IFilmLibraryPathList;
import gbeic.bbsplusplus.client.ui.film.UIFilmLibraryColumnView;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.landing.ILandingHost;
import mchorse.bbs_mod.ui.dashboard.panels.landing.UILandingScreen;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UIDataOverlayPanel;
import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.utils.colors.Colors;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;

/**
 * 用影片库工作区替换影片面板的 Landing 主页。
 * <p>
 * 工作区由左侧快捷栏、根目录列和向右展开的子列组成，不再保留右侧影片列表。
 * 关闭开关时恢复原版 Landing 布局。
 * </p>
 */
@Mixin(value = UILandingScreen.class, remap = true)
public abstract class UIFilmLibraryLandingMixin extends UIElement implements IFilmLibraryLayoutToggle
{
    private static final int CARD_W = 440;
    private static final int CARD_H = 360;

    @Shadow @Final private ILandingHost host;
    @Shadow @Final private UIElement card;

    @Unique private UIDataDashboardPanel<?> bbspp$panel;
    @Unique private UIDataOverlayPanel<?> bbspp$overlay;
    @Unique private UIDataPathList bbspp$list;
    @Unique private FilmLibrarySupport bbspp$support;
    @Unique private UIElement bbspp$library;
    @Unique private List<UIElement> bbspp$hidden;
    @Unique private boolean bbspp$applied;
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
        if (!(panel.overlay instanceof UIDataOverlayPanel<?> overlay) || overlay.namesList == null)
        {
            return false;
        }

        this.bbspp$applied = true;
        this.bbspp$panel = panel;
        this.bbspp$overlay = overlay;
        this.bbspp$list = overlay.namesList;
        this.bbspp$support = new FilmLibrarySupport(panel, overlay.namesList);

        this.bbspp$ensureLibraryListMode();
        this.bbspp$support.rememberPhysicalFolders();
        this.bbspp$hideOriginalLanding();

        this.card.relative((UIElement) (Object) this).xy(0, 0).wh(1F, 1F).anchor(0F, 0F);

        UIElement library = new UIElement();

        library.relative(this.card).xy(0, 0).wh(1F, 1F);
        library.add(new UIRenderable((context) -> this.bbspp$renderBackground(context, library.area)));

        UIFilmLibraryColumnView columns = new UIFilmLibraryColumnView(this.bbspp$support);

        columns.relative(library).xy(0, 0).wh(1F, 1F);
        library.add(columns);
        this.card.add(library);
        this.bbspp$library = library;

        if (!overlay.hasParent())
        {
            overlay.setVisible(false);
            library.add(overlay);
            this.bbspp$overlayAttached = true;
        }

        ((UIElement) (Object) this).resize();

        return true;
    }

    @Unique
    private void bbspp$ensureLibraryListMode()
    {
        if (!(this.bbspp$list instanceof IFilmLibraryPathList pathList) || pathList.bbspp$isFilmLibraryMode())
        {
            return;
        }

        pathList.bbspp$setFilmLibraryMode(true);
        pathList.bbspp$setFilmLibrarySortDescending(this.bbspp$support.isSortDescending());
        pathList.bbspp$applyFilmLibraryDefaultWhenFilled();
        this.bbspp$panel.requestNames();
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
        if (!this.bbspp$applied || this.bbspp$hidden == null)
        {
            return;
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
    private void bbspp$renderBackground(UIContext context, mchorse.bbs_mod.ui.utils.Area area)
    {
        /* 原版 Landing 的动态渐变背景（LandingBackdrop）在本层之下照常绘制：
         * 这里只铺一层由上至下渐轻的暗纱保证前景可读，不再用重色把它盖住。 */
        context.batcher.gradientVBox(area.x, area.y, area.ex(), area.ey(), Colors.A50, Colors.A25);
    }

    @Unique
    private void bbspp$restoreFilmLibraryLanding()
    {
        this.bbspp$applied = false;

        if (this.bbspp$overlayAttached && this.bbspp$overlay != null)
        {
            this.bbspp$overlay.removeFromParent();
            this.bbspp$overlay.setVisible(true);
            this.bbspp$overlayAttached = false;
        }

        if (this.bbspp$library != null)
        {
            this.bbspp$library.removeFromParent();
            this.bbspp$library = null;
        }

        this.card.relative((UIElement) (Object) this).xy(0.5F, 0.5F).wh(CARD_W, CARD_H).anchor(0.5F, 0.5F);

        if (this.bbspp$hidden != null)
        {
            for (UIElement element : this.bbspp$hidden)
            {
                element.setVisible(true);
            }

            this.bbspp$hidden.clear();
        }

        if (this.bbspp$list instanceof IFilmLibraryPathList pathList)
        {
            pathList.bbspp$setFilmLibraryMode(false);
        }

        this.bbspp$support = null;
        this.bbspp$list = null;
        this.bbspp$overlay = null;
        this.bbspp$panel = null;

        ((UIElement) (Object) this).resize();
    }
}
