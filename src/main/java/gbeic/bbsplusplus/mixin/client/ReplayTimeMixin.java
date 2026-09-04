package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.clips.ReplayTimeBridge;
import mchorse.bbs_mod.film.replays.Replay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the active camera replay time to replay keyframes and properties. */
@Mixin(value = Replay.class, remap = false)
public abstract class ReplayTimeMixin
{
    @Inject(method = "getTick", at = @At("HEAD"), cancellable = true)
    private void bbspp_cml$mapReplayTick(int tick, CallbackInfoReturnable<Integer> cir)
    {
        if (ReplayTimeBridge.isResampled())
        {
            cir.setReturnValue(ReplayTimeBridge.mapTick((Replay) (Object) this, tick));
        }
    }
}
