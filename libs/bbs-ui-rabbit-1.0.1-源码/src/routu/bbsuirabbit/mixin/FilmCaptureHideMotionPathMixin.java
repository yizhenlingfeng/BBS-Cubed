package routu.bbsuirabbit.mixin;

import routu.bbsuirabbit.client.ui.home.FilmFrameCapture;
import mchorse.bbs_mod.ui.film.controller.MotionPath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The frame a manual "set as cover" takes is drawn without the motion path (see {@link FilmFrameCapture}).
 */
@Mixin(value = MotionPath.class, remap = false)
public abstract class FilmCaptureHideMotionPathMixin
{
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private static void rabbit$hideForCapture(CallbackInfo ci)
    {
        if (FilmFrameCapture.hidingOverlays())
        {
            ci.cancel();
        }
    }
}
