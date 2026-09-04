package gbeic.bbsplusplus.premiere.audio;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.audio.AudioReader;
import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.audio.wav.WaveWriter;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.resources.Link;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 将每个 AudioClip 导出为独立 WAV,供 Premiere XML 引用。
 * <p>
 * 文件名优先 clip.title;未命名则用音频资源原名;冲突时加后缀。
 * 导出时已按 offset/duration 裁切,XML 侧 in 应写 0。
 */
public class IndividualAudioExporter
{
    private static final Logger LOGGER = LoggerFactory.getLogger(IndividualAudioExporter.class);

    public static class ExportedClip
    {
        private final AudioClip clip;
        private final File wavFile;
        private final String displayName;
        private final int sampleRate;
        private final int channels;
        private final int bitsPerSample;
        private final float durationSeconds;

        public ExportedClip(AudioClip clip, File wavFile, String displayName,
                            int sampleRate, int channels, int bitsPerSample, float durationSeconds)
        {
            this.clip = clip;
            this.wavFile = wavFile;
            this.displayName = displayName;
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.bitsPerSample = bitsPerSample;
            this.durationSeconds = durationSeconds;
        }

        public AudioClip getClip()
        {
            return this.clip;
        }

        public File getWavFile()
        {
            return this.wavFile;
        }

        /** PR clipitem 显示名(含扩展名或与文件一致) */
        public String getDisplayName()
        {
            return this.displayName;
        }

        public int getSampleRate()
        {
            return this.sampleRate;
        }

        public int getChannels()
        {
            return this.channels;
        }

        public int getBitsPerSample()
        {
            return this.bitsPerSample;
        }

        public float getDurationSeconds()
        {
            return this.durationSeconds;
        }
    }

    public List<ExportedClip> export(List<AudioClip> clips, File outputDir)
    {
        List<ExportedClip> result = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();
        int index = 0;

        for (AudioClip clip : clips)
        {
            if (!clip.enabled.get())
            {
                continue;
            }

            Link audioLink = clip.audio.get();

            if (audioLink == null)
            {
                LOGGER.warn("[premiere] AudioClip at tick {} has no audio link, skipping", clip.tick.get());
                continue;
            }

            try
            {
                Wave sourceWave = AudioReader.read(BBSMod.getProvider(), audioLink);

                if (sourceWave == null || sourceWave.data == null || sourceWave.data.length == 0)
                {
                    LOGGER.warn("[premiere] could not read audio for {}, skipping", audioLink);
                    continue;
                }

                float offsetSeconds = TimeUtils.toSeconds(clip.offset.get());
                float durationSeconds = TimeUtils.toSeconds(clip.duration.get());
                float from = offsetSeconds;
                float to = offsetSeconds + durationSeconds;

                Wave excerpt = sourceWave.excerptMono(from, to);

                if (excerpt.data == null || excerpt.data.length == 0)
                {
                    LOGGER.warn("[premiere] excerpt is empty for {} (offset={}s, dur={}s), skipping",
                        audioLink, from, durationSeconds);
                    continue;
                }

                float volume = clip.volume.get();
                if (volume != 1.0F)
                {
                    applyVolume(excerpt, volume);
                }

                String displayBase = resolveDisplayBase(clip, audioLink, index);
                String fileName = uniqueWavName(displayBase, usedNames, index);
                File wavFile = new File(outputDir, fileName);

                WaveWriter.write(wavFile, excerpt);

                float actualDuration = excerpt.getDuration();
                result.add(new ExportedClip(
                    clip,
                    wavFile,
                    fileName,
                    excerpt.sampleRate,
                    excerpt.numChannels,
                    excerpt.bitsPerSample,
                    actualDuration
                ));
                index++;

                LOGGER.info("[premiere] exported audio clip {} -> {} (title='{}', {}Hz, {}ch, {}s)",
                    audioLink, fileName, clip.title.get(), excerpt.sampleRate, excerpt.numChannels, actualDuration);
            }
            catch (Exception e)
            {
                LOGGER.error("[premiere] failed to export audio clip {}", audioLink, e);
            }
        }

        return result;
    }

    /**
     * 命名优先级: clip.title → 音频资源文件名 → audio_clip_NNN
     */
    private static String resolveDisplayBase(AudioClip clip, Link audioLink, int index)
    {
        String title = clip.title.get();
        if (title != null)
        {
            title = title.trim();
            if (!title.isEmpty())
            {
                return stripExtension(title);
            }
        }

        String fromLink = basenameFromLink(audioLink);
        if (fromLink != null && !fromLink.isEmpty())
        {
            return stripExtension(fromLink);
        }

        return String.format(Locale.ROOT, "audio_clip_%03d", index);
    }

    private static String basenameFromLink(Link link)
    {
        if (link == null || link.path == null || link.path.isEmpty())
        {
            return null;
        }
        String path = link.path.replace('\\', '/');
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return name.isEmpty() ? null : name;
    }

    private static String stripExtension(String name)
    {
        if (name == null)
        {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0)
        {
            String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (ext.equals("wav") || ext.equals("ogg") || ext.equals("mp3")
                || ext.equals("flac") || ext.equals("aiff") || ext.equals("aif"))
            {
                return name.substring(0, dot);
            }
        }
        return name;
    }

    private static String uniqueWavName(String base, Set<String> used, int index)
    {
        String sanitized = sanitizeFileName(base);
        if (sanitized.isEmpty())
        {
            sanitized = String.format(Locale.ROOT, "audio_clip_%03d", index);
        }

        String candidate = sanitized + ".wav";
        if (!used.contains(candidate.toLowerCase(Locale.ROOT)))
        {
            used.add(candidate.toLowerCase(Locale.ROOT));
            return candidate;
        }

        int n = 2;
        while (true)
        {
            candidate = sanitized + "_" + n + ".wav";
            if (!used.contains(candidate.toLowerCase(Locale.ROOT)))
            {
                used.add(candidate.toLowerCase(Locale.ROOT));
                return candidate;
            }
            n++;
        }
    }

    private static String sanitizeFileName(String name)
    {
        if (name == null)
        {
            return "";
        }
        /* Windows 非法字符 + 控制字符 */
        String s = name.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", "_").trim();
        while (s.endsWith("."))
        {
            s = s.substring(0, s.length() - 1).trim();
        }
        if (s.length() > 120)
        {
            s = s.substring(0, 120).trim();
        }
        return s;
    }

    private void applyVolume(Wave wave, float volume)
    {
        if (wave.data == null || wave.bitsPerSample != 16)
        {
            return;
        }

        byte[] data = wave.data;
        for (int i = 0; i + 1 < data.length; i += 2)
        {
            short sample = (short) ((data[i + 1] << 8) | (data[i] & 0xFF));
            sample = (short) (sample * volume);
            data[i] = (byte) (sample & 0xFF);
            data[i + 1] = (byte) ((sample >> 8) & 0xFF);
        }
    }
}
