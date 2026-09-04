package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.ui.film.FilmVisibilityController;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.utils.colors.Color;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional IRLite integration for both supported light-guide renderers. */
@Pseudo
@Mixin(targets = {
    "qualet.irlite.client.forms.PointLightFormRenderer",
    "qualet.irlite.client.forms.SpotlightFormRenderer"
}, remap = false)
public abstract class IRLiteGuideVisibilityMixin
{
    @Inject(method = "renderGuide", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void bbspp_cml$hideGuide(FormRenderingContext context, Color color, CallbackInfo ci)
    {
        if (!FilmVisibilityController.isIrlVisible())
        {
            ci.cancel();
        }
    }

    @Inject(method = "renderStencilHandles", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void bbspp_cml$hideHandles(FormRenderingContext context, CallbackInfo ci)
    {
        if (!FilmVisibilityController.isIrlVisible())
        {
            ci.cancel();
        }
    }
}
