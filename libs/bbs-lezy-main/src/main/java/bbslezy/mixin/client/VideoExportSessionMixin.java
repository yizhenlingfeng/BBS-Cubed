package bbslezy.mixin.client;

import bbslezy.audio.LezyAudioMuxer;
import bbslod.LodSettings;
import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.film.VideoExportSession;
import mchorse.bbs_mod.utils.VideoMuxer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

@Mixin(value = VideoExportSession.class, remap = false)
public abstract class VideoExportSessionMixin
{
    /**
     * Prevent warmup from aborting prematurely during F6 Record & Replay.
     */
    @Redirect(
        method = "update",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/film/VideoExportSession;shouldAbortWarmup()Z"
        )
    )
    private boolean bbslezy$ignoreShouldAbortWarmup(VideoExportSession session)
    {
        return false;
    }

    /**
     * When separate tracks are on, stash the deferred film WAV for the mux
     * redirect and hand the mixer a null film track (Minecraft-only mix).
     */
    @Inject(method = "readWave", at = @At("HEAD"), cancellable = true)
    private static void bbslezy$deferFilmTrack(File file, CallbackInfoReturnable<Wave> cir)
    {
        if (LodSettings.separateAudioTracks.get())
        {
            if (file != null && file.isFile())
            {
                File filmWav = new File(file.getParentFile(), file.getName().replace(".wav", "") + ".film.wav");

                if (file.renameTo(filmWav))
                {
                    file = filmWav;
                }
            }

            LezyAudioMuxer.setDeferredFilm(file);
            cir.setReturnValue(new Wave(1, 2, 48000, 16, new byte[4]));
        }
    }

    /**
     * Mux film audio and Minecraft sounds as two tracks; fall back to the
     * original single-track mux when there is no stashed film track.
     */
    @Redirect(method = "finishCapturedSounds",
        at = @At(value = "INVOKE",
            target = "Lmchorse/bbs_mod/utils/VideoMuxer;mux(Ljava/io/File;Ljava/io/File;Ljava/lang/String;)Ljava/io/File;"))
    private static File bbslezy$muxTwoTracks(File video, File audio, String movieName)
    {
        if (LodSettings.separateAudioTracks.get())
        {
            File deferred = LezyAudioMuxer.consumeDeferredFilm();

            if (deferred != null && deferred.isFile())
            {
                return LezyAudioMuxer.muxTwoTracks(video, deferred, audio, movieName);
            }
        }
        else
        {
            LezyAudioMuxer.consumeDeferredFilm();
        }

        File result = VideoMuxer.mux(video, audio, movieName);

        if (result != null && audio != null && audio.exists())
        {
            audio.delete();
        }

        return result;
    }
}
