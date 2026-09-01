package gbeic.bbsppp.mixin;

import gbeic.bbsppp.export.AudioSubtitleExporter;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.VideoRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 捕获视频导出文件名，并在录制结束后触发 SRT 生成。
 * <p>
 * BBS 的导出文件名由 {@link StringUtils#createTimestampFilename()} 生成。
 * BBSPPP 复用同一个名字生成旁边的 {@code .srt} 文件，避免用户手动匹配视频和字幕。
 * </p>
 */
@Mixin(VideoRecorder.class)
public class VideoRecorderMixin
{
    @Shadow
    private boolean recording;

    @Unique
    private boolean bbsppp$shouldExportAudioSubtitle;

    /**
     * 注入目标：{@link VideoRecorder#startRecording(String, java.io.File, int, int, int)} 的文件名参数。
     * 注入原因：2.4 版录制流程会在上层导出会话里生成文件名，SRT 文件需要复用同一个基础文件名。
     * 修改行为：保留已有文件名；缺省时补齐时间戳文件名，并把最终结果暂存给字幕导出器。
     */
    @ModifyVariable(
        method = "startRecording(Ljava/lang/String;Ljava/io/File;III)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        remap = false
    )
    private String bbsppp$captureMovieName(String movieName)
    {
        if (movieName == null || movieName.isEmpty())
        {
            movieName = StringUtils.createTimestampFilename();
        }

        AudioSubtitleExporter.currentMovieName = movieName;

        return movieName;
    }

    /**
     * 注入目标：{@link VideoRecorder#stopRecording(boolean)} 方法入口。
     * 注入原因：2.4 版把录制停止逻辑集中到了带参数的重载，导出字幕前需要确认这次调用确实结束了一次录制。
     * 修改行为：暂存进入停止流程前的录制状态，供方法尾部判断是否生成 SRT。
     */
    @Inject(method = "stopRecording(Z)V", at = @At("HEAD"), remap = false)
    private void bbsppp$captureRecordingState(boolean finishEffects, CallbackInfo ci)
    {
        this.bbsppp$shouldExportAudioSubtitle = this.recording;
    }

    /**
     * 注入目标：{@link VideoRecorder#stopRecording(boolean)} 方法尾部。
     * 注入原因：录制停止时视频文件名和轨道上下文都已经齐备，适合生成 SRT 文件。
     * 修改行为：根据暂存的音频片段生成同名字幕，然后清理暂存状态。
     */
    @Inject(
        method = "stopRecording(Z)V",
        at = @At("TAIL"),
        remap = false
    )
    private void bbsppp$exportAudioSubtitle(boolean finishEffects, CallbackInfo ci)
    {
        if (this.bbsppp$shouldExportAudioSubtitle)
        {
            AudioSubtitleExporter.export();
        }

        this.bbsppp$shouldExportAudioSubtitle = false;
    }
}
