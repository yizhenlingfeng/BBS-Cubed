package routu.bbsuirabbit.mixin;

import routu.bbsuirabbit.client.ui.home.FilmFrameCapture;
import routu.bbsuirabbit.client.ui.home.FilmThumbnails;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UIFilmPreview;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Film library thumbnails in the film monitor: "set as cover" in the record button's menu, the automatic
 * captures before the preview draws, and a short shutter flash after a manual one
 * (see {@link FilmFrameCapture}).
 */
@Mixin(value = UIFilmPreview.class, remap = false)
public abstract class UIFilmPreviewThumbnailMixin
{
    @Shadow(remap = false)
    private UIFilmPanel panel;

    @Shadow(remap = false)
    public abstract Area getViewport();

    /** In the record button's right-click menu, beside BBS's screenshot: the icon row is full enough. */
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void rabbit$addSetCover(UIFilmPanel panel, CallbackInfo ci)
    {
        UIFilmPreview self = (UIFilmPreview) (Object) this;

        self.recordVideo.context((menu) ->
        {
            if (FilmThumbnails.enabled())
            {
                menu.action(Icons.IMAGE, L10n.lang("rabbit.film.set_cover"), () -> FilmFrameCapture.requestManual(panel));
            }
        });
    }

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void rabbit$capture(UIContext context, CallbackInfo ci)
    {
        if (this.panel != null && FilmThumbnails.enabled())
        {
            FilmFrameCapture.onPreviewRender(this.panel);
        }
    }

    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void rabbit$flash(UIContext context, CallbackInfo ci)
    {
        float t = (System.currentTimeMillis() - FilmFrameCapture.getFlashAt()) / 380F;

        if (t < 0F || t >= 1F)
        {
            return;
        }

        Area area = this.getViewport();
        float a = (1F - t) * (1F - t) * 0.55F;

        context.batcher.box(area.x, area.y, area.ex(), area.ey(), ((int) (a * 255) << 24) | 0xffffff);
    }
}
