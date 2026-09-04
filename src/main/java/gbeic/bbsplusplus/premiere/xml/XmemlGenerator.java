package gbeic.bbsplusplus.premiere.xml;

import gbeic.bbsplusplus.premiere.audio.IndividualAudioExporter;
import gbeic.bbsplusplus.premiere.util.AudioTrackPacker;
import gbeic.bbsplusplus.premiere.util.PremierePathUrl;
import gbeic.bbsplusplus.premiere.util.TickToFrameConverter;
import gbeic.bbsplusplus.settings.CMLSettings;
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
 * 生成 Premiere Pro 兼容的 xmeml v5 XML。
 * <ul>
 *   <li>pathurl 使用 file://localhost/ 标准形式</li>
 *   <li>音频按 BBS layer 打包,同 layer 重叠再拆轨</li>
 *   <li>独立 WAV 已裁切,in/out 从 0 起</li>
 *   <li>音频元数据写真实采样率/声道</li>
 * </ul>
 */
public class XmemlGenerator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(XmemlGenerator.class);

    /**
     * @param videoFile 可为 null(仅音频导出时不写视频轨)
     */
    public void generate(File xmlFile, File videoFile,
                         List<IndividualAudioExporter.ExportedClip> exportedAudio,
                         int totalDurationTicks, int frameRate, int width, int height)
    {
        boolean useNtsc = CMLSettings.premiereExportNtscFlag != null
            && CMLSettings.premiereExportNtscFlag.get();
        TickToFrameConverter converter = new TickToFrameConverter(frameRate, useNtsc);

        int filmFrames = converter.ticksToFrames(totalDurationTicks);
        int audioEndFrames = 0;
        if (exportedAudio != null)
        {
            for (IndividualAudioExporter.ExportedClip ec : exportedAudio)
            {
                AudioClip clip = ec.getClip();
                int end = converter.ticksToFrames(clip.tick.get())
                    + converter.ticksToFrames(clip.duration.get());
                if (end > audioEndFrames)
                {
                    audioEndFrames = end;
                }
            }
        }

        /* 序列长度至少覆盖影片与音频,避免尾部被截 */
        int totalFrames = Math.max(filmFrames, audioEndFrames);
        if (totalFrames < 1)
        {
            totalFrames = 1;
        }

        boolean hasVideo = videoFile != null && videoFile.exists();
        String sequenceName = hasVideo
            ? stripExtension(videoFile.getName())
            : stripExtension(xmlFile.getName());
        String videoUri = hasVideo ? PremierePathUrl.fromFile(videoFile) : "";

        LOGGER.info("[premiere] writing XML sequence='{}' frames={} hasVideo={} videoUri={}",
            sequenceName, totalFrames, hasVideo, videoUri);

        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(xmlFile), StandardCharsets.UTF_8)))
        {
            out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            out.println("<!DOCTYPE xmeml>");
            out.println("<xmeml version=\"5\">");
            out.println("  <sequence id=\"bbs-sequence-1\">");
            out.println("    <name>" + escapeXml(sequenceName) + "</name>");
            out.println("    <duration>" + totalFrames + "</duration>");
            out.println("    <rate>");
            out.println("      <timebase>" + converter.getTimebase() + "</timebase>");
            out.println("      <ntsc>" + converter.getNtscString() + "</ntsc>");
            out.println("    </rate>");
            out.println("    <media>");

            if (hasVideo)
            {
                writeVideoTrack(out, videoFile, videoUri, totalFrames, width, height, converter);
            }
            else
            {
                /* 仅音频:仍写空 video 格式头,方便 PR 识别序列分辨率 */
                writeEmptyVideoFormat(out, totalFrames, width, height, converter);
            }
            writeAudioTracks(out, exportedAudio, converter);

            out.println("    </media>");
            out.println("  </sequence>");
            out.println("</xmeml>");
        }
        catch (IOException e)
        {
            LOGGER.error("[premiere] failed to write XML file", e);
        }
    }

    private void writeEmptyVideoFormat(PrintWriter out, int totalFrames, int width, int height,
                                        TickToFrameConverter converter)
    {
        out.println("      <video>");
        out.println("        <format>");
        out.println("          <samplecharacteristics>");
        out.println("            <rate>");
        out.println("              <timebase>" + converter.getTimebase() + "</timebase>");
        out.println("              <ntsc>" + converter.getNtscString() + "</ntsc>");
        out.println("            </rate>");
        out.println("            <width>" + width + "</width>");
        out.println("            <height>" + height + "</height>");
        out.println("          </samplecharacteristics>");
        out.println("        </format>");
        out.println("        <track>");
        out.println("        </track>");
        out.println("      </video>");
    }

    private void writeVideoTrack(PrintWriter out, File videoFile, String videoUri,
                                  int totalFrames, int width, int height,
                                  TickToFrameConverter converter)
    {
        out.println("      <video>");
        out.println("        <format>");
        out.println("          <samplecharacteristics>");
        out.println("            <rate>");
        out.println("              <timebase>" + converter.getTimebase() + "</timebase>");
        out.println("              <ntsc>" + converter.getNtscString() + "</ntsc>");
        out.println("            </rate>");
        out.println("            <width>" + width + "</width>");
        out.println("            <height>" + height + "</height>");
        out.println("          </samplecharacteristics>");
        out.println("        </format>");
        out.println("        <track>");
        out.println("          <clipitem id=\"clipitem-video-1\">");
        out.println("            <name>" + escapeXml(videoFile.getName()) + "</name>");
        out.println("            <enabled>TRUE</enabled>");
        out.println("            <duration>" + totalFrames + "</duration>");
        out.println("            <rate>");
        out.println("              <timebase>" + converter.getTimebase() + "</timebase>");
        out.println("              <ntsc>" + converter.getNtscString() + "</ntsc>");
        out.println("            </rate>");
        out.println("            <start>0</start>");
        out.println("            <end>" + totalFrames + "</end>");
        out.println("            <in>0</in>");
        out.println("            <out>" + totalFrames + "</out>");
        out.println("            <masterclipid>masterclip-video-1</masterclipid>");
        out.println("            <file id=\"file-video-1\">");
        out.println("              <name>" + escapeXml(videoFile.getName()) + "</name>");
        out.println("              <pathurl>" + escapeXml(videoUri) + "</pathurl>");
        out.println("              <rate>");
        out.println("                <timebase>" + converter.getTimebase() + "</timebase>");
        out.println("                <ntsc>" + converter.getNtscString() + "</ntsc>");
        out.println("              </rate>");
        out.println("              <duration>" + totalFrames + "</duration>");
        /* 视频 file 仅声明 video 特征,不写硬编码 audio —— 与真实 MP4 音轨不符会导致 PR 脱机 */
        out.println("              <media>");
        out.println("                <video>");
        out.println("                  <samplecharacteristics>");
        out.println("                    <rate>");
        out.println("                      <timebase>" + converter.getTimebase() + "</timebase>");
        out.println("                      <ntsc>" + converter.getNtscString() + "</ntsc>");
        out.println("                    </rate>");
        out.println("                    <width>" + width + "</width>");
        out.println("                    <height>" + height + "</height>");
        out.println("                  </samplecharacteristics>");
        out.println("                </video>");
        out.println("              </media>");
        out.println("            </file>");
        out.println("          </clipitem>");
        out.println("        </track>");
        out.println("      </video>");
    }

    private void writeAudioTracks(PrintWriter out,
                                   List<IndividualAudioExporter.ExportedClip> exportedAudio,
                                   TickToFrameConverter converter)
    {
        out.println("      <audio>");
        out.println("        <numOutputChannels>2</numOutputChannels>");

        if (exportedAudio == null || exportedAudio.isEmpty())
        {
            out.println("      </audio>");
            return;
        }

        List<AudioTrackPacker.PackedItem<IndividualAudioExporter.ExportedClip>> items = new ArrayList<>();
        for (IndividualAudioExporter.ExportedClip ec : exportedAudio)
        {
            items.add(new AudioTrackPacker.PackedItem<>(ec.getClip(), ec));
        }

        List<List<AudioTrackPacker.PackedItem<IndividualAudioExporter.ExportedClip>>> tracks =
            AudioTrackPacker.pack(items);

        LOGGER.info("[premiere] packed {} audio clips into {} track(s)",
            exportedAudio.size(), tracks.size());

        int clipIndex = 0;
        for (int t = 0; t < tracks.size(); t++)
        {
            List<AudioTrackPacker.PackedItem<IndividualAudioExporter.ExportedClip>> track = tracks.get(t);
            out.println("        <track>");

            for (AudioTrackPacker.PackedItem<IndividualAudioExporter.ExportedClip> packed : track)
            {
                clipIndex++;
                IndividualAudioExporter.ExportedClip ec = packed.payload;
                AudioClip clip = ec.getClip();
                File audioFile = ec.getWavFile();
                String audioUri = PremierePathUrl.fromFile(audioFile);

                int clipStartFrames = converter.ticksToFrames(clip.tick.get());
                /* 优先用真实 WAV 时长,避免 tick 换算与采样误差 */
                int clipDurationFrames = Math.max(1, converter.secondsToFrames(ec.getDurationSeconds()));
                int tickDurationFrames = converter.ticksToFrames(clip.duration.get());
                if (tickDurationFrames > 0)
                {
                    /* 取较大值,防止 PR 认为媒体不足 */
                    clipDurationFrames = Math.max(clipDurationFrames, tickDurationFrames);
                }
                int clipEndFrames = clipStartFrames + clipDurationFrames;

                /* WAV 已 excerpt,in 必须从 0 开始 */
                int inFrames = 0;
                int outFrames = clipDurationFrames;

                int sampleRate = ec.getSampleRate() > 0 ? ec.getSampleRate() : 48000;
                int channels = ec.getChannels() > 0 ? ec.getChannels() : 1;
                int depth = ec.getBitsPerSample() > 0 ? ec.getBitsPerSample() : 16;

                LOGGER.info("[premiere] audio clipitem-{} track={} file={} uri={} start={} end={} {}Hz {}ch",
                    clipIndex, t + 1, audioFile.getName(), audioUri,
                    clipStartFrames, clipEndFrames, sampleRate, channels);

                String clipName = ec.getDisplayName() != null && !ec.getDisplayName().isEmpty()
                    ? ec.getDisplayName()
                    : audioFile.getName();

                out.println("          <clipitem id=\"clipitem-audio-" + clipIndex + "\">");
                out.println("            <name>" + escapeXml(clipName) + "</name>");
                out.println("            <enabled>TRUE</enabled>");
                out.println("            <duration>" + clipDurationFrames + "</duration>");
                out.println("            <rate>");
                out.println("              <timebase>" + converter.getTimebase() + "</timebase>");
                out.println("              <ntsc>" + converter.getNtscString() + "</ntsc>");
                out.println("            </rate>");
                out.println("            <start>" + clipStartFrames + "</start>");
                out.println("            <end>" + clipEndFrames + "</end>");
                out.println("            <in>" + inFrames + "</in>");
                out.println("            <out>" + outFrames + "</out>");
                out.println("            <masterclipid>masterclip-audio-" + clipIndex + "</masterclipid>");
                out.println("            <file id=\"file-audio-" + clipIndex + "\">");
                out.println("              <name>" + escapeXml(clipName) + "</name>");
                out.println("              <pathurl>" + escapeXml(audioUri) + "</pathurl>");
                out.println("              <rate>");
                out.println("                <timebase>" + converter.getTimebase() + "</timebase>");
                out.println("                <ntsc>" + converter.getNtscString() + "</ntsc>");
                out.println("              </rate>");
                out.println("              <duration>" + clipDurationFrames + "</duration>");
                out.println("              <media>");
                out.println("                <audio>");
                out.println("                  <samplecharacteristics>");
                out.println("                    <depth>" + depth + "</depth>");
                out.println("                    <samplerate>" + sampleRate + "</samplerate>");
                out.println("                  </samplecharacteristics>");
                out.println("                  <channelcount>" + channels + "</channelcount>");
                out.println("                </audio>");
                out.println("              </media>");
                out.println("            </file>");
                out.println("          </clipitem>");
            }

            out.println("        </track>");
        }

        out.println("      </audio>");
    }

    private static String stripExtension(String name)
    {
        if (name == null || name.isEmpty())
        {
            return "sequence";
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0)
        {
            return name.substring(0, dot);
        }
        return name;
    }

    private String escapeXml(String input)
    {
        if (input == null) return "";
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
