package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.export.AudioSubtitleExporter;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UIFilmRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 捕获视频导出开始时的影片音频轨道上下文。
 * <p>
 * 底层视频录制器只能拿到输出文件名，无法直接访问影片编辑器里的音频片段。
 * 因此这里在 UI 录制器开始导出时先暂存镜头轨道和循环选区起点，供导出完成后生成 SRT。
 * </p>
 */
@Mixin(UIFilmRecorder.class)
public class UIFilmRecorderMixin
{
    @Shadow
    public UIFilmPanel editor;

    /**
     * 注入目标：{@link UIFilmRecorder#startRecording(int, int, int, int)} 方法入口。
     * 注入原因：此时还能访问影片编辑器数据，可以取得音频片段和循环选区范围。
     * 修改行为：暂存镜头轨道，并在启用循环导出时记录导出的起始 tick。
     */
    @Inject(method = "startRecording(IIII)V", at = @At("HEAD"), remap = false)
    private void bbsppp$captureCameraClipsForExport(int duration, int id, int w, int h, CallbackInfo ci)
    {
        AudioSubtitleExporter.currentCameraClips = this.editor.getData().camera;

        int min = this.editor.cameraEditor.clips.loopMin;
        int max = this.editor.cameraEditor.clips.loopMax;

        AudioSubtitleExporter.currentMinTick = BBSSettings.editorLoop.get() ? Math.min(min, max) : 0;
    }
}
