package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.clips.ReplayActionPolicy;
import gbeic.bbsplusplus.clips.ReplayTimeBridge;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses irreversible replay actions while the replay time is resampled.
 *
 * <p>Injects into {@link ActionClip#applyClient} itself instead of the call site
 * in {@code Replay#applyClientActions}: Mixin allows only one {@code @Redirect}
 * per call instruction, and BBS-POV's {@code ReplayPovMixin} already redirects
 * that instruction, which silently disabled this guard. {@code applyClient} is
 * final and its only caller is the timeline pass, so cancelling at {@code HEAD}
 * is equivalent to skipping the call.</p>
 */
@Mixin(value = ActionClip.class, remap = false)
public abstract class ReplayActionSafetyMixin
{
    @Inject(method = "applyClient", at = @At("HEAD"), cancellable = true, require = 1)
    private void bbspp_cml$skipResampledAction(IEntity entity, Film film, Replay replay, int tick, CallbackInfo info)
    {
        if (ReplayTimeBridge.isResampled()
            && ReplayActionPolicy.bypassesResampling(this.getClass().getName()))
        {
            info.cancel();
        }
    }
}
