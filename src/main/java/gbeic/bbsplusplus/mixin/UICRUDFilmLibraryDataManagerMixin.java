package gbeic.bbsplusplus.mixin;


import gbeic.bbsplusplus.settings.ModSettings;
import gbeic.bbsplusplus.client.ui.film.IFilmLibraryLayoutToggle;
import mchorse.bbs_mod.ui.dashboard.panels.UICRUDDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.UIEditorDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.landing.UILandingScreen;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 影片面板不再使用独立的列表浮窗。
 * <p>
 * 「全新影片库界面」开启后，影片库主页就是 Landing 页，浮窗的列表已经被借到主页面里；
 * 此时再打开数据管理器只会得到一个空浮窗，所以直接拦截。拦截前先让 Landing 同步一次，
 * 这样运行中关闭开关后，列表会先归还浮窗，再正常打开原版数据管理器。
 * </p>
 */
@Mixin(value = UICRUDDashboardPanel.class, remap = true)
public abstract class UICRUDFilmLibraryDataManagerMixin
{
    @Inject(method = "openDataManager", at = @At("HEAD"), cancellable = true, remap = true)
    private void bbspp$syncLandingBeforeDataManager(CallbackInfo ci)
    {
        if (!((Object) this instanceof UIFilmPanel))
        {
            return;
        }

        UILandingScreen landing = ((UIEditorDashboardPanel) (Object) this).landing;

        if (landing instanceof IFilmLibraryLayoutToggle toggle)
        {
            toggle.bbspp$syncFilmLibraryLayout();

            /* 只有 Landing 侧确实接管了影片库（Mixin 已应用）时才停用浮窗；
             * 否则保留原版数据管理器作为降级入口。 */
            if (ModSettings.newFilmLibraryUi != null && ModSettings.newFilmLibraryUi.get())
            {
                ci.cancel();
            }
        }
    }
}
