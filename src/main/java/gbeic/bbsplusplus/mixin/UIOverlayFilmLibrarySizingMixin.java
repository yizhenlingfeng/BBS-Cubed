package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.BBSAddonsSettings;
import mchorse.bbs_mod.ui.film.UIFilmOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 为影片库浮窗提供更大的初始尺寸。
 * <p>
 * BBS 2.7 的「影片列表」默认通过 {@code addOverlay(..., 200, 0.9F)} 打开，宽度只有 200px，
 * 左侧再放文件夹树会把右侧影片列表挤得无法使用。这里只对影片列表浮窗、
 * 且「全新影片库界面」开启时改用像素尺寸重开一次；其它浮窗保持原版行为。
 * </p>
 */
@Mixin(value = UIOverlay.class, remap = true)
public abstract class UIOverlayFilmLibrarySizingMixin
{
    @Inject(
        method = "addOverlay(Lmchorse/bbs_mod/ui/framework/UIContext;Lmchorse/bbs_mod/ui/framework/elements/overlay/UIOverlayPanel;IF)Lmchorse/bbs_mod/ui/framework/elements/overlay/UIOverlay;",
        at = @At("HEAD"),
        cancellable = true,
        remap = true
    )
    private static void bbspp$sizeFilmLibraryOverlay(UIContext context, UIOverlayPanel panel, int width, float height, CallbackInfoReturnable<UIOverlay> cir)
    {
        if (!(panel instanceof UIFilmOverlayPanel))
        {
            return;
        }

        if (BBSAddonsSettings.newFilmLibraryUi == null || !BBSAddonsSettings.newFilmLibraryUi.get())
        {
            return;
        }

        if (context == null || context.menu == null)
        {
            return;
        }

        int screenWidth = context.menu.width;
        int screenHeight = context.menu.height;

        int preferredWidth = Math.max(360, Math.min(760, Math.max(360, screenWidth - 40)));
        int preferredHeight = Math.max(260, Math.min(480, Math.max(260, screenHeight - 40)));

        cir.setReturnValue(UIOverlay.addOverlay(context, panel, preferredWidth, preferredHeight));
    }
}
