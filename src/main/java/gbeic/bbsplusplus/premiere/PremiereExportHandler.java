package gbeic.bbsplusplus.premiere;

import gbeic.bbsplusplus.premiere.audio.IndividualAudioExporter;
import gbeic.bbsplusplus.premiere.xml.SrtGenerator;
import gbeic.bbsplusplus.premiere.xml.XmemlGenerator;
import gbeic.bbsplusplus.settings.CMLSettings;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.VideoRecorder;
import mchorse.bbs_mod.utils.clips.Clips;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 等视频录制结束后,在打包目录内生成 XML/SRT + 独立音频。
 * <p>
 * 视频录制阶段已把 {@code videoExportPath} 指到打包子目录,因此 mp4/log
 * 不会再残留在父目录;结束后恢复原路径。完整打包后再按 BBS 设置
 * 播放提示音 / 打开文件夹,并弹出成功通知。
 */
public class PremiereExportHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PremiereExportHandler.class);
    private static final PremiereExportHandler INSTANCE = new PremiereExportHandler();
    private static final Link RENDER_COMPLETE_SOUND = Link.assets("sounds/render_complete.ogg");

    private volatile boolean wasRecording = false;
    private volatile boolean premiereExportPending = false;
    private volatile Film pendingFilm;
    private volatile File pendingPackageDir;
    private volatile String previousExportPath = "";
    private volatile boolean prevOpenFolder = false;
    private volatile boolean prevPlaySound = true;
    private int tickCount = 0;

    public static PremiereExportHandler getInstance()
    {
        return INSTANCE;
    }

    /**
     * 开始等待视频录制结束。
     *
     * @param packageDir         最终打包目录(同时也是当前视频写出目录)
     * @param previousExportPath 修改前的 BBS videoExportPath,结束后恢复
     * @param openFolderAfter    用户原「导出后打开文件夹」设置
     * @param playSoundAfter     用户原「导出后播放提示音」设置
     */
    public void startPremiereExport(Film film, File packageDir, String previousExportPath,
                                    boolean openFolderAfter, boolean playSoundAfter)
    {
        if (CMLSettings.premiereExportEnabled == null || !CMLSettings.premiereExportEnabled.get())
        {
            return;
        }

        this.premiereExportPending = true;
        this.pendingFilm = film;
        this.pendingPackageDir = packageDir;
        this.previousExportPath = previousExportPath == null ? "" : previousExportPath;
        this.prevOpenFolder = openFolderAfter;
        this.prevPlaySound = playSoundAfter;
        this.wasRecording = false;
        this.tickCount = 0;
        LOGGER.info("[premiere] export pending, packageDir={}, previousPath={}, open={}, sound={}",
            packageDir, this.previousExportPath, openFolderAfter, playSoundAfter);
    }

    public void cancelPending()
    {
        this.premiereExportPending = false;
        this.pendingFilm = null;
        this.pendingPackageDir = null;
        this.wasRecording = false;
        this.previousExportPath = "";
        this.prevOpenFolder = false;
        this.prevPlaySound = true;
    }

    /**
     * 仅音频模式:不录视频,直接写 WAV + XML/SRT。
     */
    public void exportAudioOnly(Film film, File packageDir)
    {
        if (film == null || packageDir == null)
        {
            LOGGER.warn("[premiere] audio-only export missing film/packageDir");
            return;
        }

        packageDir.mkdirs();
        LOGGER.info("[premiere] audio-only export to {}", packageDir.getAbsolutePath());

        boolean ok = false;
        try
        {
            this.writePackage(film, packageDir, null);
            ok = true;
        }
        catch (Exception e)
        {
            LOGGER.error("[premiere] audio-only export failed", e);
        }

        notifyExportResult(ok);
        if (ok)
        {
            playSoundIfNeeded(BBSSettings.videoPlaySoundAfterExport.get());
            openFolderIfNeeded(packageDir, BBSSettings.videoOpenFolderAfterExport.get());
        }
    }

    public void onClientTick()
    {
        if (!this.premiereExportPending)
        {
            return;
        }

        this.tickCount++;

        try
        {
            VideoRecorder recorder = BBSModClient.getVideoRecorder();
            boolean isRecording = recorder.isRecording();

            if (this.tickCount % 100 == 0)
            {
                LOGGER.info("[premiere] tick #{}: wasRecording={}, isRecording={}",
                    this.tickCount, this.wasRecording, isRecording);
            }

            if (this.wasRecording && !isRecording)
            {
                LOGGER.info("[premiere] detected video export completion at tick #{}", this.tickCount);

                final Film film = this.pendingFilm;
                final File packageDir = this.pendingPackageDir;
                final String restorePath = this.previousExportPath;
                final boolean openFolder = this.prevOpenFolder;
                final boolean playSound = this.prevPlaySound;

                this.premiereExportPending = false;
                this.pendingFilm = null;
                this.pendingPackageDir = null;
                this.previousExportPath = "";
                this.prevOpenFolder = false;
                this.prevPlaySound = true;

                MinecraftClient.getInstance().execute(() ->
                {
                    try
                    {
                        PremiereExportActions.restoreExportPath(restorePath);
                        PremiereExportActions.restoreFeedbackSettings(openFolder, playSound);
                        this.onExportComplete(film, packageDir, openFolder, playSound);
                    }
                    catch (Exception e)
                    {
                        LOGGER.error("[premiere] error in onExportComplete", e);
                        PremiereExportActions.restoreExportPath(restorePath);
                        PremiereExportActions.restoreFeedbackSettings(openFolder, playSound);
                        notifyExportResult(false);
                    }
                });
            }

            this.wasRecording = isRecording;
        }
        catch (Exception e)
        {
            LOGGER.error("[premiere] error in onClientTick", e);
        }
    }

    private void onExportComplete(Film film, File packageDir, boolean openFolder, boolean playSound)
    {
        if (film == null)
        {
            LOGGER.warn("[premiere] no pending Film data, skipping");
            notifyExportResult(false);
            return;
        }

        if (packageDir == null || !packageDir.isDirectory())
        {
            LOGGER.error("[premiere] package directory missing: {}", packageDir);
            notifyExportResult(false);
            return;
        }

        LOGGER.info("[premiere] package directory: {}", packageDir.getAbsolutePath());

        File videoFile = this.findLatestMp4(packageDir);
        LOGGER.info("[premiere] latest mp4 in package: {}", videoFile);

        if (videoFile == null || !videoFile.exists())
        {
            LOGGER.warn("[premiere] could not find exported video in package dir, generating audio/XML only");
            videoFile = null;
        }

        boolean ok = false;
        try
        {
            this.writePackage(film, packageDir, videoFile);
            ok = true;
        }
        catch (Exception e)
        {
            LOGGER.error("[premiere] writePackage failed", e);
        }

        notifyExportResult(ok);
        if (ok)
        {
            playSoundIfNeeded(playSound);
            openFolderIfNeeded(packageDir, openFolder);
        }
    }

    private void writePackage(Film film, File packageDir, File videoFile)
    {
        int frameRate = BBSSettings.videoFrameRate.get();
        int width = BBSSettings.videoWidth.get();
        int height = BBSSettings.videoHeight.get();

        Clips camera = film.camera;
        List<AudioClip> audioClips = camera.getClips(AudioClip.class);
        int totalDurationTicks = camera.calculateDuration();

        List<IndividualAudioExporter.ExportedClip> exported = new ArrayList<>();

        boolean wantAudio = CMLSettings.premiereExportIndividualAudio == null
            || CMLSettings.premiereExportIndividualAudio.get();
        /* 仅音频模式(无视频)强制导出独立音频 */
        if (videoFile == null)
        {
            wantAudio = true;
        }

        if (wantAudio && !audioClips.isEmpty())
        {
            LOGGER.info("[premiere] exporting {} audio clips", audioClips.size());
            IndividualAudioExporter audioExporter = new IndividualAudioExporter();
            exported = audioExporter.export(audioClips, packageDir);
            LOGGER.info("[premiere] exported {} audio files", exported.size());
        }

        String baseName;
        if (videoFile != null)
        {
            baseName = videoFile.getName().replaceAll("\\.mp4$", "");
        }
        else
        {
            baseName = packageDir.getName();
            if (baseName.endsWith("_premiere"))
            {
                baseName = baseName.substring(0, baseName.length() - "_premiere".length());
            }
            if (baseName.isEmpty())
            {
                baseName = "bbs_audio_export";
            }
        }

        if (CMLSettings.premiereExportSrt != null && CMLSettings.premiereExportSrt.get())
        {
            File srtFile = new File(packageDir, baseName + ".srt");
            LOGGER.info("[premiere] generating SRT: {}", srtFile.getAbsolutePath());
            SrtGenerator srtGenerator = new SrtGenerator();
            List<AudioClip> forSrt = new ArrayList<>();
            if (!exported.isEmpty())
            {
                for (IndividualAudioExporter.ExportedClip ec : exported)
                {
                    forSrt.add(ec.getClip());
                }
            }
            else
            {
                forSrt.addAll(audioClips);
            }
            List<File> srtFiles = srtGenerator.generate(srtFile, forSrt, frameRate);
            LOGGER.info("[premiere] generated {} SRT file(s)", srtFiles.size());
        }
        else
        {
            File xmlFile = new File(packageDir, baseName + ".xml");
            LOGGER.info("[premiere] generating XML: {}", xmlFile.getAbsolutePath());
            XmemlGenerator generator = new XmemlGenerator();
            generator.generate(xmlFile, videoFile, exported,
                totalDurationTicks, frameRate, width, height);
            LOGGER.info("[premiere] generated XML at {}", xmlFile.getAbsolutePath());
        }
    }

    private void notifyExportResult(boolean success)
    {
        try
        {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null)
            {
                return;
            }
            client.execute(() ->
            {
                try
                {
                    if (client.currentScreen instanceof UIScreen)
                    {
                        UIBaseMenu menu = ((UIScreen) client.currentScreen).getMenu();
                        if (menu != null && menu.context != null)
                        {
                            if (success)
                            {
                                menu.context.notifySuccess(PremiereUIKeys.EXPORT_SUCCESS);
                            }
                            else
                            {
                                menu.context.notifyError(PremiereUIKeys.EXPORT_FAILED);
                            }
                            return;
                        }
                    }
                }
                catch (Exception e)
                {
                    LOGGER.debug("[premiere] notify via UIScreen failed", e);
                }
            });
        }
        catch (Exception e)
        {
            LOGGER.warn("[premiere] failed to show export notification", e);
        }
    }

    private void playSoundIfNeeded(boolean playSound)
    {
        if (!playSound)
        {
            return;
        }

        try
        {
            if (BBSModClient.getSounds().play(RENDER_COMPLETE_SOUND) == null)
            {
                UIUtils.playClick(0.5F);
            }
        }
        catch (Exception e)
        {
            LOGGER.warn("[premiere] failed to play export sound", e);
            try
            {
                UIUtils.playClick(0.5F);
            }
            catch (Exception ignored)
            {}
        }
    }

    private void openFolderIfNeeded(File packageDir, boolean openFolder)
    {
        if (!openFolder || packageDir == null)
        {
            return;
        }

        try
        {
            MinecraftClient.getInstance().execute(() -> UIUtils.openFolder(packageDir));
        }
        catch (Exception e)
        {
            LOGGER.warn("[premiere] failed to open output folder via UIUtils, fallback Desktop", e);
            try
            {
                java.awt.Desktop.getDesktop().open(packageDir);
            }
            catch (Exception e2)
            {
                LOGGER.warn("[premiere] failed to open output folder", e2);
            }
        }
    }

    private File findLatestMp4(File folder)
    {
        if (folder == null || !folder.isDirectory())
        {
            return null;
        }

        File[] mp4Files = folder.listFiles((dir, name) -> name.endsWith(".mp4"));
        if (mp4Files == null || mp4Files.length == 0)
        {
            return null;
        }

        File latest = null;
        long latestTime = Long.MIN_VALUE;

        for (File f : mp4Files)
        {
            if (f.lastModified() > latestTime)
            {
                latestTime = f.lastModified();
                latest = f;
            }
        }

        return latest;
    }
}
