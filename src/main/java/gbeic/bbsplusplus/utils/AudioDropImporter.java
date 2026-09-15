package gbeic.bbsplusplus.utils;

import gbeic.bbsplusplus.api.UIClipsAccessor;
import gbeic.bbsplusplus.ui.forms.SnowUIKeys;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.audio.SoundBuffer;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.FFMpegUtils;
import mchorse.bbs_mod.utils.IOUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * 音频文件拖入的异步导入器:替代原版拖入转换的同步 ffmpeg 调用(会阻塞
 * 渲染线程数秒),转换全程后台线程执行,完成后回主线程通知/建剪辑。
 *
 * <p>鼠标释放点落在剪辑时间线({@link UIClips})上时,转换完成后直接在该
 * tick/层上创建音频剪辑(时长取自解码后的 SoundBuffer,多个文件按时长依次
 * 排开);否则仅导入到音频目录(与原版行为一致,含防重名)。wav/ogg 直接
 * 复制,mp3/mp4/flac/aiff 经 ffmpeg 转单声道 wav。</p>
 */
public class AudioDropImporter
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioDropImporter.class);

    private static final String[] CONVERT_EXTENSIONS = {".mp3", ".mp4", ".flac", ".aiff"};
    private static final String[] COPY_EXTENSIONS = {".wav", ".ogg"};

    /** 是否全部为可导入的音频文件(至少一个文件) */
    public static boolean isAllAudio(String[] paths)
    {
        if (paths == null || paths.length == 0)
        {
            return false;
        }

        for (String path : paths)
        {
            String lower = path.toLowerCase(Locale.ROOT);

            if (!matches(lower, CONVERT_EXTENSIONS) && !matches(lower, COPY_EXTENSIONS))
            {
                return false;
            }
        }

        return true;
    }

    private static boolean matches(String path, String[] extensions)
    {
        for (String extension : extensions)
        {
            if (path.endsWith(extension))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * 后台转换并按需建剪辑。
     *
     * @param timeline 释放点命中的时间线(null = 仅导入不建剪辑)
     * @param mouseX/mouseY 释放点 UI 坐标(timeline 非 null 时用于定位 tick/层)
     */
    public static void importAudio(UIContext context, List<File> files, File directory, boolean openFolder, UIClips timeline, int mouseX, int mouseY)
    {
        context.notifyInfo(SnowUIKeys.AUDIO_IMPORT_STARTED.format(files.size()));

        CompletableFuture.runAsync(() ->
        {
            List<String> imported = new ArrayList<>();

            for (File file : files)
            {
                String name = convert(file, directory);

                if (name != null)
                {
                    imported.add(name);
                }
            }

            MinecraftClient.getInstance().execute(() -> finish(context, imported, files.size(), directory, openFolder, timeline, mouseX, mouseY));
        });
    }

    /** 转换/复制单个文件到目标目录,返回目录内的文件名(失败 null);与原版一致防重名 */
    private static String convert(File file, File directory)
    {
        try
        {
            directory.mkdirs();

            String lower = file.getName().toLowerCase(Locale.ROOT);

            if (matches(lower, COPY_EXTENSIONS))
            {
                File destination = IOUtils.findNonExistingFile(new File(directory, file.getName()));

                Files.copy(file.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);

                return destination.getName();
            }

            String name = IOUtils.findNonExistingFile(new File(directory, StringUtils.removeExtension(file.getName()) + ".wav")).getName();

            /* 对齐原版 ToWAVImporter:强制单声道 wav */
            return FFMpegUtils.execute(directory, "-y", "-i", file.getAbsolutePath(), "-ac", "1", name) ? name : null;
        }
        catch (Exception e)
        {
            LOGGER.warn("[FSloveCML] 音频导入转换失败: {}", file.getName(), e);

            return null;
        }
    }

    private static void finish(UIContext context, List<String> imported, int total, File directory, boolean openFolder, UIClips timeline, int mouseX, int mouseY)
    {
        if (imported.isEmpty())
        {
            context.notifyError(SnowUIKeys.AUDIO_IMPORT_FAILED);

            return;
        }

        File audioFolder = BBSMod.getAudioFolder();

        if (timeline != null && directory.equals(audioFolder))
        {
            int tick = (int) Math.max(0, timeline.fromGraphX(mouseX));
            int layer = Math.max(0, timeline.fromLayerY(mouseY));

            for (String name : imported)
            {
                /* 必须经该时间线的工厂创建:客户端注册的实际是 AudioClientClip
                 * 子类,UIClip.FACTORIES 的编辑面板映射也只认它 */
                Clip created = ((UIClipsAccessor) timeline).bbspp_cml$getFactory().create(Link.bbs("audio"));

                if (!(created instanceof AudioClip clip))
                {
                    break;
                }

                Link link = Link.assets("audio/" + name);
                SoundBuffer buffer = BBSModClient.getSounds().get(link, true);
                int duration = buffer != null ? Math.max(1, (int) (buffer.getDuration() * 20F)) : 100;

                clip.audio.set(link);
                ((UIClipsAccessor) timeline).bbspp_cml$invokeAddClip(clip, tick, layer, duration);

                tick += duration;
            }
        }
        else if (openFolder)
        {
            mchorse.bbs_mod.ui.utils.UIUtils.openFolder(directory);
        }

        if (imported.size() < total)
        {
            context.notifyError(SnowUIKeys.AUDIO_IMPORT_PARTIAL.format(imported.size(), total));
        }
        else
        {
            context.notifySuccess(SnowUIKeys.AUDIO_IMPORT_DONE.format(imported.size()));
        }
    }
}
