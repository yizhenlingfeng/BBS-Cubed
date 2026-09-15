package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.ui.film.FilmVisibilityController;
import gbeic.bbsplusplus.ui.film.FilmVisibilityUIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把“影片可见性”按钮加到影片面板的动作栏（2.6 顶栏已由 tabBar 改为 actions() 槽位）。
 *
 * <p>2.5 时代注入 topBarActions/renderTopBarButton/getTabsRightInsetPx，2.6 这些成员全部删除；
 * 现在动作栏由 {@code actions()} 的 editor/action/layout/common/menu 槽构建，直接在构造末尾
 * 往 action 槽追加一个图标即可，槽位自己负责宽度，不再需要手动扩 tabs inset。</p>
 */
@Mixin(value = UIFilmPanel.class, remap = false)
public abstract class UIFilmPanelVisibilityMixin
{
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$addVisibilityButton(CallbackInfo ci)
    {
        UIFilmPanel panel = (UIFilmPanel) (Object) this;

        UIIcon visibilityButton = new UIIcon(Icons.VISIBLE,
            (button) -> FilmVisibilityController.openMenu(panel.getContext()));
        visibilityButton.tooltip(FilmVisibilityUIKeys.TITLE, Direction.BOTTOM);

        panel.actions().action(visibilityButton);
    }
}
