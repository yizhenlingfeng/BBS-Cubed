package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.utils.VanillaSoundResource;
import mchorse.bbs_mod.ui.framework.elements.overlay.UISoundOverlayPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复 Minecraft 音效(ADD) 模式下选中条目后，回找已下载文件失败的问题。
 * 原实现只剥离 {@code Music:/Sound:} 前缀，而列表实际使用 {@code [Category]: }。
 */
@Mixin(value = UISoundOverlayPanel.class, remap = false)
public abstract class UISoundOverlayPanelMixin
{
    @Inject(method = "findDownloadedSoundInAddMode", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp_cml$findDownloadedSoundInAddMode(String displayName, CallbackInfoReturnable<String> cir)
    {
        cir.setReturnValue(VanillaSoundResource.findDownloadedPath(displayName));
    }
}
