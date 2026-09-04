package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.ui.miniwindow.UIResizeHandles;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为全部设置类/导出类 {@link UIOverlayPanel} 补上边缘与角缩放。
 */
@Mixin(value = UIOverlayPanel.class, remap = false)
public abstract class UIOverlayPanelResizeMixin
{
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$attachResizeHandles(CallbackInfo ci)
    {
        UIResizeHandles.attach((UIOverlayPanel) (Object) this);
    }
}
