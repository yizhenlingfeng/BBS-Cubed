package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.ui.miniwindow.IMiniWindowDockHost;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.framework.elements.layout.UIDockLayout;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 离开粒子面板时清理 dock 小窗。
 */
@Mixin(value = UIDashboardPanel.class, remap = false)
public abstract class UIDashboardPanelMiniWindowMixin
{
    @Inject(method = "disappear", at = @At("HEAD"), remap = false)
    private void bbspp_cml$clearParticleDockMiniWindows(CallbackInfo ci)
    {
        Object self = this;

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
