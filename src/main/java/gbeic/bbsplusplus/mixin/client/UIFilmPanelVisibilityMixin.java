package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.ui.film.FilmVisibilityController;
import gbeic.bbsplusplus.ui.film.FilmVisibilityUIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.tabs.UIDataTabs;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the film visibility button and widens the tabs inset for it.
 *
 * <p>2.5 适配:分隔柄与拖拽状态迁移到共享 UIDockLayout(影片 dock),
 * 硬锁防拖拽的守卫改由 UIDockLayoutMiniWindowMixin 上的
 * {@link FilmVisibilityController#preventsLockedLayoutResizing} dock 重载实现。</p>
 */
@Mixin(value = UIFilmPanel.class, remap = false)
public abstract class UIFilmPanelVisibilityMixin
{
    @Shadow
    private UIElement topBarActions;

    @Shadow
    public UIIcon openCameraEditor;

    @Shadow
    private void renderTopBarButton(UIContext context, UIIcon button, boolean active)
    {
    }

    @Unique
    private UIIcon bbspp_cml$visibilityButton;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$addVisibilityButton(CallbackInfo ci)
    {
        UIFilmPanel panel = (UIFilmPanel) (Object) this;

        this.bbspp_cml$visibilityButton = new UIIcon(Icons.VISIBLE,
            (button) -> FilmVisibilityController.openMenu(panel.getContext()));
        this.bbspp_cml$visibilityButton.tooltip(FilmVisibilityUIKeys.TITLE, Direction.BOTTOM);
        this.bbspp_cml$visibilityButton.wh(UIDataTabs.TABS_HEIGHT_PX, UIDataTabs.TABS_HEIGHT_PX);

        this.topBarActions.addBefore(this.openCameraEditor, this.bbspp_cml$visibilityButton);

        int width = UIDataTabs.TABS_HEIGHT_PX * 4 + 8;
        this.topBarActions.x(1F, -width).w(width);
    }

    @Inject(method = "renderTopBarActions", at = @At("TAIL"), remap = false)
    private void bbspp_cml$renderVisibilityButton(UIContext context, CallbackInfo ci)
    {
        this.renderTopBarButton(context, this.bbspp_cml$visibilityButton, false);
    }

    @Inject(method = "getTabsRightInsetPx", at = @At("RETURN"), cancellable = true, remap = false)
    private void bbspp_cml$expandTabsInset(CallbackInfoReturnable<Integer> cir)
    {
        cir.setReturnValue(cir.getReturnValue() + UIDataTabs.TABS_HEIGHT_PX);
    }
}
