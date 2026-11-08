package routu.bbsuirabbit.mixin;

import routu.bbsuirabbit.RabbitSettings;
import mchorse.bbs_mod.ui.dashboard.panels.UICRUDDashboardPanel;
import mchorse.bbs_mod.ui.film.UIFilmOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/** The film data manager opens big enough for the BBS++ film library when the library is on. */
@Mixin(value = UICRUDDashboardPanel.class, remap = false)
public class UICRUDDashboardPanelLibrarySizeMixin
{
    /* WrapOperation, not Redirect: BBS++ redirects the same call, and two redirects there crash the game */
    @WrapOperation(method = "openDataManager", at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/ui/framework/elements/overlay/UIOverlay;addOverlay(Lmchorse/bbs_mod/ui/framework/UIContext;Lmchorse/bbs_mod/ui/framework/elements/overlay/UIOverlayPanel;IF)Lmchorse/bbs_mod/ui/framework/elements/overlay/UIOverlay;"), remap = false)
    private UIOverlay rabbit$openFilmLibrarySize(UIContext context, UIOverlayPanel panel, int w, float h, Operation<UIOverlay> original)
    {
        if (panel instanceof UIFilmOverlayPanel && RabbitSettings.newFilmLibraryUi != null && RabbitSettings.newFilmLibraryUi.get())
        {
            return UIOverlay.addOverlay(context, panel, 0.8F, 0.9F);
        }

        return original.call(context, panel, w, h);
    }
}
