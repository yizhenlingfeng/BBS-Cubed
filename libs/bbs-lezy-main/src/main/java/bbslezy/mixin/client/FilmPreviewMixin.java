package bbslezy.mixin.client;

import bbslezy.ui.video.UIGpuCodecWarningOverlayPanel;
import bbslezy.video.LezyVideoSettingsHelper;
import bbslezy.ui.UILodContextMenu;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import java.util.function.Consumer;
import bbslod.LodSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.film.UIFilmPreview;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UIFilmPreview.class, remap = false)
public abstract class FilmPreviewMixin
{
    @Inject(method = "<init>", at = @At("TAIL"))
    private void bbslezy$init(CallbackInfo ci)
    {
        UIFilmPreview self = (UIFilmPreview) (Object) this;
        UIIcon lodButton = new UIIcon(Icons.VISIBLE, (b) ->
        {
            b.getContext().replaceContextMenu(new UILodContextMenu());
        });

        lodButton.highlight(LodSettings.enabled::get, Direction.BOTTOM);
        lodButton.tooltip(L10n.lang("bbslezy.ui.lod.tooltip"));
        self.icons.addAfter(self.motionPath, lodButton);

        Consumer<UIIcon> originalRecord = self.recordVideo.callback;
        self.recordVideo.callback = (b) ->
        {
            if (LezyVideoSettingsHelper.isHwAccelUnsupported())
            {
                String gpuName = LezyVideoSettingsHelper.getGpuName();
                String codecName = LezyVideoSettingsHelper.getCodecName();

                UIGpuCodecWarningOverlayPanel panel = new UIGpuCodecWarningOverlayPanel(
                    gpuName,
                    codecName,
                    () -> {
                        LezyVideoSettingsHelper.forceCpuOnce = true;
                        if (originalRecord != null)
                        {
                            originalRecord.accept(b);
                        }
                    }
                );

                UIOverlay.addOverlay(b.getContext(), panel);
                return;
            }

            if (originalRecord != null)
            {
                originalRecord.accept(b);
            }
        };
    }
}
