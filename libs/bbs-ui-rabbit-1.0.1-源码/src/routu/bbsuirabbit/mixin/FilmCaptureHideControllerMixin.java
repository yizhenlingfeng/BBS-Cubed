package routu.bbsuirabbit.mixin;

import routu.bbsuirabbit.client.ui.home.FilmFrameCapture;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The frame a manual "set as cover" takes is drawn without the orbit centre marker (see {@link FilmFrameCapture}).
 */
@Mixin(value = UIFilmController.class, remap = false)
public abstract class FilmCaptureHideControllerMixin
{
    @Inject(method = "renderOrbitCenterMarker", at = @At("HEAD"), cancellable = true, remap = false)
    private void rabbit$hideOrbitMarker(CallbackInfo ci)
    {
        if (FilmFrameCapture.hidingOverlays())
        {
            ci.cancel();
        }
    }
}
