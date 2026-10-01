package bbslezy.audio;

import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.FFMpegUtils;

import java.io.File;
import java.io.InputStream;
import java.util.List;

/**
 * Extra audio codecs read on demand through ffmpeg — the same backend BBS
 * already uses for video files, so originals are never converted.
 */
public class LezyAudioCodecs
{
    private static final String[] SUPPORTED = {
        ".mp3", ".m4a", ".aac", ".opus", ".wma",
        ".alac", ".ape", ".flac", ".aif", ".aiff", ".ac3"
    };

    private static final String[] VIDEO = {
        ".mp4", ".mov", ".mkv", ".webm", ".avi", ".m4v"
    };

    public static boolean shouldHandle(String pathLower)
    {
        if (pathLower.endsWith(".wav") || pathLower.endsWith(".ogg") || isVideo(pathLower))
        {
            return false;
        }

        for (String extension : SUPPORTED)
        {
            if (pathLower.endsWith(extension))
            {
                return true;
            }
        }

        return false;
    }

    public static boolean isVideo(String pathLower)
    {
        for (String extension : VIDEO)
        {
            if (pathLower.endsWith(extension))
            {
                return true;
            }
        }

        return false;
    }

    public static boolean allVideo(List<File> files)
    {
        for (File file : files)
        {
            if (!isVideo(file.getName().toLowerCase()))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Extensions the copy importer claims: every supported codec plus wav/ogg.
     * Single source of truth — decode and import can never drift apart.
     */
    public static String[] copyExtensions()
    {
        String[] extensions = new String[SUPPORTED.length + 2];

        System.arraycopy(SUPPORTED, 0, extensions, 0, SUPPORTED.length);

        extensions[SUPPORTED.length] = ".wav";
        extensions[SUPPORTED.length + 1] = ".ogg";

        return extensions;
    }

    /**
     * Verbatim copy of core's video-audio ffmpeg pipe: stereo 44.1kHz 16-bit
     * PCM. Returns null on missing file or empty output, like the original.
     */
    public static Wave decode(AssetProvider provider, Link link) throws Exception
    {
        File file = provider.getFile(link);

        if (file == null || !file.isFile())
        {
            return null;
        }

        ProcessBuilder builder = new ProcessBuilder(
            FFMpegUtils.getFFMPEG(),
            "-i", file.getAbsolutePath(),
            "-vn", "-sn", "-dn",
            "-ac", "2", "-ar", "44100",
            "-acodec", "pcm_s16le", "-f", "s16le",
            "pipe:1"
        );

        builder.redirectError(ProcessBuilder.Redirect.DISCARD);

        Process process = builder.start();
        byte[] data;

        try (InputStream stream = process.getInputStream())
        {
            data = stream.readAllBytes();
        }
        finally
        {
            process.destroy();
        }

        if (data.length == 0)
        {
            return null;
        }

        return new Wave(1, 2, 44100, 16, data);
    }
}
