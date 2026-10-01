package bbslezy.audio;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.utils.FFMpegUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Muxes film audio and Minecraft-captured audio as two separate tracks,
 * mirroring {@code VideoMuxer}'s contract (temp name, swap-in-place, logging,
 * timeout). The film track file is stashed by the readWave inject and consumed
 * exactly once by the mux redirect in the export session mixin.
 */
public class LezyAudioMuxer
{
    private static File deferredFilm;

    public static void setDeferredFilm(File file)
    {
        deferredFilm = file;
    }

    public static File consumeDeferredFilm()
    {
        File file = deferredFilm;

        deferredFilm = null;

        return file;
    }

    /**
     * Merge {@code film} (track 1) and {@code mc} (track 2) into {@code video},
     * replacing it. Returns the merged file, or null when the merge failed.
     */
    public static File muxTwoTracks(File video, File film, File mc, String movieName)
    {
        File folder = video.getParentFile();
        String tempName = movieName + ".tmp";

        try
        {
            List<String> args = new ArrayList<>();

            args.add(FFMpegUtils.getFFMPEG());
            args.add("-y");
            args.add("-i");
            args.add(video.getName());
            args.add("-i");
            args.add(film.getName());
            args.add("-i");
            args.add(mc.getName());
            args.add("-map");
            args.add("0:v:0");
            args.add("-map");
            args.add("1:a:0");
            args.add("-map");
            args.add("2:a:0");
            args.add("-c:v");
            args.add("copy");
            args.add("-c:a:0");
            args.add("aac");
            args.add("-b:a:0");
            args.add("192k");
            args.add("-ac:a:0");
            args.add("2");
            args.add("-c:a:1");
            args.add("aac");
            args.add("-b:a:1");
            args.add("192k");
            args.add("-ac:a:1");
            args.add("2");
            args.add("-metadata:s:a:0");
            args.add("title=BBS film audio");
            args.add("-metadata:s:a:1");
            args.add("title=Minecraft sounds");
            args.add("-shortest");
            args.add(tempName + ".mp4");

            System.out.println("Muxing two audio tracks with following arguments: " + args);

            ProcessBuilder builder = new ProcessBuilder(args);
            File log = new File(folder, movieName + ".mux.log");

            if (!BBSSettings.videoEncoderLog.get())
            {
                log = BBSMod.getSettingsPath("video.log");
            }

            builder.directory(folder);
            builder.redirectErrorStream(true);
            builder.redirectOutput(log);

            Process process = builder.start();

            process.getOutputStream().close();

            if (!process.waitFor(10, TimeUnit.MINUTES))
            {
                process.destroy();
                deleteTemp(folder, tempName);

                return null;
            }

            File merged = findTemp(folder, tempName);

            if (process.exitValue() != 0 || merged == null)
            {
                deleteTemp(folder, tempName);

                return null;
            }

            String extension = merged.getName().substring(tempName.length());
            File result = new File(folder, movieName + extension);

            if (video.exists() && !video.delete())
            {
                return merged;
            }

            File finalResult = merged.renameTo(result) ? result : merged;

            if (finalResult != null)
            {
                if (film != null && film.exists())
                {
                    film.delete();
                }

                if (mc != null && mc.exists())
                {
                    mc.delete();
                }
            }

            return finalResult;
        }
        catch (Exception e)
        {
            e.printStackTrace();

            return null;
        }
    }

    private static File findTemp(File folder, String tempName)
    {
        File[] files = folder.listFiles();

        if (files == null)
        {
            return null;
        }

        String prefix = tempName + ".";

        for (File file : files)
        {
            if (file.getName().startsWith(prefix))
            {
                return file;
            }
        }

        return null;
    }

    private static void deleteTemp(File folder, String tempName)
    {
        File merged = findTemp(folder, tempName);

        if (merged != null)
        {
            merged.delete();
        }
    }
}
