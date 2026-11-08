package routu.bbsuirabbit.mixin;

import routu.bbsuirabbit.client.ui.home.IFilmHomeHost;
import routu.bbsuirabbit.client.ui.home.UIFilmLibraryHome;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The film panel's empty tab is "Film library" while BBS++'s library home is on. */
@Mixin(value = UIFilmPanel.class, remap = false)
public abstract class UIFilmPanelFilmHomeMixin
{
    @Inject(method = "getNewTabLabel", at = @At("RETURN"), cancellable = true, remap = false)
    private void rabbit$libraryTabLabel(CallbackInfoReturnable<IKey> cir)
    {
        if (UIFilmLibraryHome.isHomeOn())
        {
            cir.setReturnValue(L10n.lang("rabbit.ui.film.home.library"));
        }
    }

    @Inject(method = "getTabIcon", at = @At("RETURN"), cancellable = true, remap = false)
    private void rabbit$libraryTabIcon(String id, CallbackInfoReturnable<Icon> cir)
    {
        if (id == null && UIFilmLibraryHome.isHomeOn())
        {
            cir.setReturnValue(Icons.GALLERY);
        }
    }

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void rabbit$followFilmHomeSetting(UIContext context, CallbackInfo ci)
    {
        if ((Object) this instanceof IFilmHomeHost host && host.rabbit$homeOutOfDate())
        {
            ((UIFilmPanel) (Object) this).syncLanding();
        }
    }
}
