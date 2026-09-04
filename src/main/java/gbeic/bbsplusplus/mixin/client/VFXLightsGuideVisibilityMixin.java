package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.ui.film.FilmVisibilityController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional VFXLIGHTS integration for the single guide visibility switch. */
@Mixin(targets = "com.bbsvfx.vfxlights.client.LightFormRenderer", remap = false)
@Pseudo
public abstract class VFXLightsGuideVisibilityMixin
{
    @Inject(method = "gizmosVisible", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void bbspp_cml$applyVisibility(CallbackInfoReturnable<Boolean> cir)
    {
        if (!FilmVisibilityController.isVfxVisible())
        {
            cir.setReturnValue(false);
        }
    }
}
