package gbeic.bbsplusplus.premiere.xml;

import gbeic.bbsplusplus.premiere.util.AudioTrackPacker;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 生成 SRT 字幕文件(多轨道并列音频用空行/注释分隔)。
 * 轨道打包与 XML 共用 {@link AudioTrackPacker}(layer 优先 + 重叠拆轨)。
 */
public class SrtGenerator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SrtGenerator.class);

    public List<File> generate(File srtFile, List<AudioClip> audioClips, int frameRate)
    {
        List<File> generatedFiles = new ArrayList<>();

        if (audioClips == null || audioClips.isEmpty())
        {
            return generatedFiles;
        }

        List<AudioTrackPacker.PackedItem<Void>> items = new ArrayList<>();
        for (AudioClip clip : audioClips)
        {
            items.add(new AudioTrackPacker.PackedItem<>(clip, null));
        }

        List<List<AudioTrackPacker.PackedItem<Void>>> tracks = AudioTrackPacker.pack(items);
        LOGGER.info("[premiere] SRT assigned to {} tracks", tracks.size());
        generateSrtFile(srtFile, tracks, frameRate);
        generatedFiles.add(srtFile);
        return generatedFiles;
    }

    private void generateSrtFile(File srtFile,
                                  List<List<AudioTrackPacker.PackedItem<Void>>> tracks,
                                  int frameRate)
    {
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(srtFile), StandardCharsets.UTF_8)))
        {
            int sequenceNumber = 1;

            for (int trackIndex = 0; trackIndex < tracks.size(); trackIndex++)
            {
                List<AudioTrackPacker.PackedItem<Void>> track = tracks.get(trackIndex);

                if (tracks.size() > 1)
                {
                    out.println(sequenceNumber);
                    out.println(formatSrtTime(0) + " --> " + formatSrtTime(1000));
                    out.println("[Track " + (trackIndex + 1) + "]");
                    out.println();
                    sequenceNumber++;
                }

                for (AudioTrackPacker.PackedItem<Void> packed : track)
                {
                    AudioClip clip = packed.clip;
                    long startMs = ticksToMs(clip.tick.get());
                    long durationMs = ticksToMs(clip.duration.get());
                    long endMs = startMs + durationMs;

                    out.println(sequenceNumber);
                    out.println(formatSrtTime(startMs) + " --> " + formatSrtTime(endMs));

                    String title = clip.title.get();
                    if (title == null || title.isEmpty())
                    {
                        title = "Audio " + sequenceNumber;
                    }
                    out.println(title);
                    out.println();
                    sequenceNumber++;
                }
            }

            LOGGER.info("[premiere] wrote SRT {} ({} tracks, {} cues)",
                srtFile.getAbsolutePath(), tracks.size(), sequenceNumber - 1);
        }
        catch (IOException e)
        {
            LOGGER.error("[premiere] failed to write SRT file", e);
        }
    }

    private long ticksToMs(int ticks)
    {
        return (long) (ticks * 1000.0 / 20.0);
    }

    private String formatSrtTime(long ms)
    {
        long hours = ms / 3600000;
        long minutes = (ms % 3600000) / 60000;
        long seconds = (ms % 60000) / 1000;
        long millis = ms % 1000;
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis);
    }
}
