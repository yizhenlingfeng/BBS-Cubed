package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.clips.ReplayTimeBridge;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.forms.entities.IEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps entity interpolation aligned with the remapped replay tick. */
@Mixin(value = BaseFilmController.class, remap = false)
public abstract class BaseFilmControllerReplayTimeMixin
{
    @Inject(method = "getTransition", at = @At("HEAD"), cancellable = true)
    private void bbspp_cml$mapReplayTransition(IEntity entity, float transition,
        CallbackInfoReturnable<Float> cir)
    {
        BaseFilmController controller = (BaseFilmController) (Object) this;

        if (ReplayTimeBridge.isResampled() && !controller.paused)
        {
            cir.setReturnValue(ReplayTimeBridge.mapTransition(transition));
        }
    }
}
