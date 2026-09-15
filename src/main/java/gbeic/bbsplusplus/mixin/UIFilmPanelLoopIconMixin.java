package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为录像编辑器操作栏补一个可见的循环模式图标（2.6 原生仅有循环按键）。
 */
@Mixin(UIFilmPanel.class)
public class UIFilmPanelLoopIconMixin
{
    @Inject(method = "<init>(Lmchorse/bbs_mod/ui/dashboard/UIDashboard;)V", at = @At("RETURN"), remap = true)
    private void bbsplusplus$addLoopIcon(UIDashboard dashboard, CallbackInfo ci)
    {
        UIFilmPanel self = (UIFilmPanel) (Object) this;

        gbeic.bbsplusplus.client.ui.utils.UILoopIconUtils.addLoopIcon(self);
    }
}
