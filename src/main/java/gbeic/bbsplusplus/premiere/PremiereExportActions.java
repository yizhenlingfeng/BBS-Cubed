package gbeic.bbsplusplus.premiere;

import gbeic.bbsplusplus.settings.CMLSettings;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.utils.FFMpegUtils;
import mchorse.bbs_mod.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Premiere 导出按钮的点击 / 右键逻辑。
 */
public final class PremiereExportActions
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PremiereExportActions.class);

    private PremiereExportActions()
    {}

    public static void openSettings(UIFilmPanel filmPanel)
    {
        UIOverlay.addOverlay(filmPanel.getContext(), new UIPremiereExportSettingsOverlayPanel());
    }

    public static void onClick(UIFilmPanel filmPanel)
    {
        if (filmPanel.checkShowNoCamera())
        {
            return;
        }

        boolean audioOnly = CMLSettings.premiereExportAudioOnly != null
            && CMLSettings.premiereExportAudioOnly.get();

        /* 仅音频模式不需要 FFmpeg/视频录制 */
        if (!audioOnly && !FFMpegUtils.checkFFMPEG())
        {
            UIOverlay.addOverlay(filmPanel.getContext(),
                new UIMessageOverlayPanel(PremiereUIKeys.EXPORT, PremiereUIKeys.FFMPEG_MISSING));
            return;
        }

        if (CMLSettings.premiereExportEnabled == null || !CMLSettings.premiereExportEnabled.get())
        {
            UIOverlay.addOverlay(filmPanel.getContext(),
                new UIMessageOverlayPanel(PremiereUIKeys.EXPORT, PremiereUIKeys.DISABLED));
            return;
        }

        UIPremiereExportOverlayPanel dialog = new UIPremiereExportOverlayPanel();
        /* 对齐「导出为动画」的窄面板 */
        UIOverlay.addOverlay(filmPanel.getContext(), dialog, 240, 130);

        dialog.onClose((event) ->
        {
            if (!dialog.isConfirmed())
            {
                LOGGER.info("[premiere] user cancelled export dialog");
                return;
            }

            String folderName = dialog.getFolderName();
            String exportPath = dialog.getExportPath();
            LOGGER.info("[premiere] dialog ok folderName={}, exportPath={}, audioOnly={}",
                folderName, exportPath, audioOnly);

            File baseFolder = resolveBaseFolder(exportPath);
            if (baseFolder == null)
            {
                LOGGER.error("[premiere] could not resolve export base folder");
                return;
            }

            String packageName = resolvePackageName(folderName);
            File packageDir = new File(baseFolder, packageName);
            packageDir.mkdirs();
            LOGGER.info("[premiere] package directory: {}", packageDir.getAbsolutePath());

            if (audioOnly)
            {
                PremiereExportHandler.getInstance().exportAudioOnly(
                    filmPanel.getData(), packageDir);
                return;
            }

            /*
             * 1) videoExportPath → 打包子目录,避免父目录残留
             * 2) 暂时关闭 BBS 录制结束时的开文件夹/提示音,改由 Handler 在
             *    XML/音频全部写完后统一处理(并弹成功提示)
             */
            String previousExportPath = safeGetExportPath();
            String restorePath = baseFolder.getAbsolutePath();
            boolean prevOpenFolder = BBSSettings.videoOpenFolderAfterExport.get();
            boolean prevPlaySound = BBSSettings.videoPlaySoundAfterExport.get();

            try
            {
                BBSSettings.videoExportPath.set(packageDir.getAbsolutePath());
                BBSSettings.videoOpenFolderAfterExport.set(false);
                BBSSettings.videoPlaySoundAfterExport.set(false);

                int duration = filmPanel.getData().camera.calculateDuration();
                UIFilmPanel.applyExportSizeToBBS();

                PremiereExportHandler.getInstance().startPremiereExport(
                    filmPanel.getData(), packageDir, restorePath, prevOpenFolder, prevPlaySound);

                BBSRendering.scheduleAfterNextExportFrame(() ->
                {
                    filmPanel.recorder.startRecording(duration,
                        BBSRendering.getTexture().id,
                        BBSRendering.getVideoWidth(),
                        BBSRendering.getVideoHeight());
                });
            }
            catch (Exception e)
            {
                LOGGER.error("[premiere] failed to start video export", e);
                restoreExportPath(restorePath != null ? restorePath : previousExportPath);
                restoreFeedbackSettings(prevOpenFolder, prevPlaySound);
                PremiereExportHandler.getInstance().cancelPending();
            }
        });
    }

    private static File resolveBaseFolder(String exportPath)
    {
        if (exportPath != null && !exportPath.trim().isEmpty())
        {
            File dir = new File(exportPath.trim());
            dir.mkdirs();
            if (dir.isDirectory())
            {
                return dir;
            }
            LOGGER.warn("[premiere] custom export path invalid, falling back: {}", exportPath);
        }

        try
        {
            return BBSRendering.getVideoFolder();
        }
        catch (Exception e)
        {
            LOGGER.error("[premiere] getVideoFolder failed", e);
            return null;
        }
    }

    private static String resolvePackageName(String customFolderName)
    {
        if (customFolderName != null && !customFolderName.trim().isEmpty())
        {
            return customFolderName.trim();
        }
        return StringUtils.createTimestampFilename() + "_premiere";
    }

    private static String safeGetExportPath()
    {
        try
        {
            String p = BBSSettings.videoExportPath.get();
            return p == null ? "" : p;
        }
        catch (Exception e)
        {
            return "";
        }
    }

    static void restoreExportPath(String previous)
    {
        try
        {
            if (previous == null)
            {
                previous = "";
            }
            BBSSettings.videoExportPath.set(previous);
            LOGGER.info("[premiere] restored videoExportPath to '{}'", previous);
        }
        catch (Exception e)
        {
            LOGGER.warn("[premiere] failed to restore videoExportPath", e);
        }
    }

    static void restoreFeedbackSettings(boolean openFolder, boolean playSound)
    {
        try
        {
            BBSSettings.videoOpenFolderAfterExport.set(openFolder);
            BBSSettings.videoPlaySoundAfterExport.set(playSound);
            LOGGER.info("[premiere] restored feedback settings openFolder={}, playSound={}",
                openFolder, playSound);
        }
        catch (Exception e)
        {
            LOGGER.warn("[premiere] failed to restore feedback settings", e);
        }
    }
}
