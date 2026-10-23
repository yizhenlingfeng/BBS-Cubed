package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.ui.miniwindow.IMiniWindowDockHost;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.layout.UIDockLayout;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 离开 dashboard 面板时清理 dock 小窗。
 * <p>BBS 2.6 起 {@code disappear} 只声明在 {@link UIDashboardPanel}，影片面板与粒子面板
 * 都在这里统一处理，避免在子类上做无效注入。</p>
 */
@Mixin(value = UIDashboardPanel.class, remap = false)
public abstract class UIDashboardPanelMiniWindowMixin
{
    @Inject(method = "disappear", at = @At("HEAD"), remap = false, require = 1)
    private void bbspp_cml$clearDockMiniWindows(CallbackInfo ci)
    {
        Object self = this;

        if (self instanceof UIFilmPanel && self instanceof IMiniWindowDockHost host)
        {
            host.hostClearMiniWindows();

            return;
        }

        if (self instanceof UIParticleSchemePanel panel)
        {
            UIDockLayout dock = panel.dock;

            if (dock instanceof IMiniWindowDockHost host)
            {
                host.hostClearMiniWindows();
            }
        }
    }
}
