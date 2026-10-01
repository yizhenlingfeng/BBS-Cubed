package bbslezy.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.audio.AudioRenderer;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.WorldVideoExportSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.util.List;

@Mixin(value = WorldVideoExportSession.class, remap = false)
public abstract class WorldVideoExportSessionMixin
{
    @Shadow
    private Film film;

    /**
     * Disable shouldAbortWarmup check so asynchronous film loading does not cancel F6 export.
     */
    @Inject(method = "shouldAbortWarmup", at = @At("HEAD"), cancellable = true)
    private void bbslezy$disableAbortWarmup(CallbackInfoReturnable<Boolean> cir)
    {
        cir.setReturnValue(false);
    }

    /**
     * If F4 or world export was started without an explicit film reference,
     * resolve the currently playing film from world controllers so film audio
     * clips are rendered and separated instead of skipped.
     */
    @Inject(method = "prepare", at = @At("HEAD"))
    private void bbslezy$resolveActiveFilm(CallbackInfoReturnable<Boolean> cir)
    {
        if (this.film == null)
        {
            List<BaseFilmController> controllers = BBSModClient.getFilms().getControllers();

            if (!controllers.isEmpty())
            {
                this.film = controllers.get(0).film;
            }
        }
    }

    /**
     * In first-person or actor-centric playback, camera clips might be empty
     * or shorter than the replay action. Use the full film duration so audio
     * is not truncated or skipped with 0 duration.
     */
    @Redirect(
        method = "prepare",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/audio/AudioRenderer;renderAudio(Ljava/io/File;Ljava/util/List;IIFF)Z"
        )
    )
    private boolean bbslezy$renderAudioFullDuration(File file, List<AudioClip> clips, int totalDuration, int sampleRate, float from, float to)
    {
        if (this.film != null)
        {
            totalDuration = Math.max(totalDuration, this.film.calculateDuration());
        }

        return AudioRenderer.renderAudio(file, clips, totalDuration, sampleRate, from, to);
    }
}
