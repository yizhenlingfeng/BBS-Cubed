package bbslezy.mixin.client;

import bbslezy.ui.video.UIGpuCodecWarningOverlayPanel;
import bbslezy.video.LezyVideoSettingsHelper;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UIFilmRecorder;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts video export initiation when hardware acceleration is enabled
 * but the selected codec (such as VP9 on NVIDIA/AMD GPUs) is not supported
 * by the GPU hardware encoder, offering the user a choice to encode via CPU
 * or cancel the export.
 */
@Mixin(value = UIFilmRecorder.class, remap = false)
public abstract class UIFilmRecorderMixin
{
    @Shadow
    public UIFilmPanel editor;

    @Shadow
    public abstract void startRecording(int duration, int id, int w, int h);

    @Inject(method = "startRecording(IIII)V", at = @At("HEAD"), cancellable = true)
    private void bbslezy$checkGpuCodecSupport(int duration, int id, int w, int h, CallbackInfo ci)
    {
        if (LezyVideoSettingsHelper.isHwAccelUnsupported())
        {
            ci.cancel();

            UIContext context = this.editor != null ? this.editor.getContext() : null;

            if (context == null)
            {
                mchorse.bbs_mod.ui.framework.UIBaseMenu menu = mchorse.bbs_mod.ui.framework.UIScreen.getCurrentMenu();

                if (menu != null)
                {
                    context = menu.context;
                }
            }

            if (context == null)
            {
                return;
            }
            String gpuName = LezyVideoSettingsHelper.getGpuName();
            String codecName = LezyVideoSettingsHelper.getCodecName();
            boolean[] confirmed = new boolean[1];

            UIGpuCodecWarningOverlayPanel panel = new UIGpuCodecWarningOverlayPanel(
                gpuName,
                codecName,
                () -> {
                    confirmed[0] = true;
                    LezyVideoSettingsHelper.forceCpuOnce = true;
                    this.startRecording(duration, id, w, h);
                }
            );

            panel.onClose((e) -> {
                if (!confirmed[0] && this.editor != null)
                {
                    this.editor.restorePreviewSize();
                }
            });

            UIOverlay.addOverlay(context, panel);
        }
    }
}
