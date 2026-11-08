package routu.bbsuirabbit.mixin;

import routu.bbsuirabbit.client.ui.home.FilmFrameCapture;
import mchorse.bbs_mod.film.Recorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The frame a manual "set as cover" takes is drawn without the camera preview frustum (see
 * {@link FilmFrameCapture}). Hooked in the method itself: BBS-POV already redirects its call site.
 */
@Mixin(value = Recorder.class, remap = false)
public abstract class FilmCaptureHideRecorderMixin
{
    @Inject(method = "renderCameraPreview", at = @At("HEAD"), cancellable = true, remap = false)
    private static void rabbit$hideForCapture(CallbackInfo ci)
    {
        if (FilmFrameCapture.hidingOverlays())
        {
            ci.cancel();
        }
    }
}
