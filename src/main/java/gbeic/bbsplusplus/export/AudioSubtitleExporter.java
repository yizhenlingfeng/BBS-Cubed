package gbeic.bbsplusplus.export;

import gbeic.bbsplusplus.BBSPPPSettings;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import gbeic.bbsplusplus.BBSPlusPlusMod;

/**
 * 视频导出完成后生成音频 SRT 字幕的工具类。
 * <p>
 * BBS 的视频导出流程分别在 UI 录制器和底层视频录制器里掌握轨道数据与文件名。
 * BBSPPP 通过 Mixin 暂存这两份信息，并在录制停止时按音频片段生成同名 {@code .srt} 文件。
 * </p>
 */
public class AudioSubtitleExporter
{
    /** 在 UIFilmRecorder 准备导出前，暂存当前影片的镜头轨道数据 */
    public static Clips currentCameraClips;
    /** 暂存视频导出的起始刻，用于处理循环选区导出 */
    public static int currentMinTick;
    /** 暂存视频导出的文件名，不包含扩展名 */
    public static String currentMovieName;

    public static void export()
    {
        if (!BBSPPPSettings.shouldExportAudioSubtitle())
        {
            reset();

            return;
        }

        if (currentCameraClips == null || currentMovieName == null)
        {
            return;
        }

        if (!hasAudioClips())
        {
            reset();

            return;
        }

        File srtFile = new File(BBSRendering.getVideoFolder(), currentMovieName + ".srt");

        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(srtFile), StandardCharsets.UTF_8)))
        {
            int index = 1;

            for (Clip clip : currentCameraClips.get())
            {
                if (!(clip instanceof AudioClip audio))
                {
                    continue;
                }

                int tick = audio.tick.get() - currentMinTick;
                int duration = audio.duration.get();

                if (tick + duration <= 0)
                {
                    continue;
                }

                if (tick < 0)
                {
                    duration += tick;
                    tick = 0;
                }

                writer.println(index++);
                writer.println(formatSRTTime(tick) + " --> " + formatSRTTime(tick + duration));
                writer.println(getAudioName(audio));
                writer.println();
            }
        }
        catch (Exception e)
        {
            BBSPlusPlusMod.LOGGER.warn("音频字幕导出失败", e);
        }
        finally
        {
            reset();
        }
    }

    public static void reset()
    {
        currentCameraClips = null;
        currentMovieName = null;
        currentMinTick = 0;
    }

    private static boolean hasAudioClips()
    {
        for (Clip clip : currentCameraClips.get())
        {
            if (clip instanceof AudioClip)
            {
                return true;
            }
        }

        return false;
    }

    private static String getAudioName(AudioClip audio)
    {
        String text = audio.audio.get() != null ? audio.audio.get().path : "unknown";
        int slash = text.lastIndexOf('/');

        if (slash != -1)
        {
            text = text.substring(slash + 1);
        }

        int dot = text.lastIndexOf('.');

        if (dot != -1)
        {
            text = text.substring(0, dot);
        }

        return text;
    }

    /**
     * 将 Minecraft 刻转换成 SRT 使用的 {@code HH:mm:ss,SSS} 时间格式。
     */
    private static String formatSRTTime(int tick)
    {
        long ms = tick * 50L;
        long hours = ms / 3_600_000L;
        long minutes = (ms / 60_000L) % 60L;
        long seconds = (ms / 1_000L) % 60L;
        long milliseconds = ms % 1_000L;

        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, milliseconds);
    }
}
