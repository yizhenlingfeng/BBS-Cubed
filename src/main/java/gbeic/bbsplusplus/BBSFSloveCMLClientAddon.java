package gbeic.bbsplusplus;

import mchorse.bbs_mod.api.BBSAddonMod;
import mchorse.bbs_mod.api.Subscribe;
import mchorse.bbs_mod.api.client.events.RegisterClientSettingsEvent;
import mchorse.bbs_mod.api.client.events.RegisterDashboardPanelsEvent;
import mchorse.bbs_mod.api.client.events.RegisterL10nEvent;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import gbeic.bbsplusplus.ui.film.FilmVisibilityController;

/**
 * BBS FSloveCML Client Addon - 客户端事件订阅占位。
 *
 * <p>BBS(2.5) 经 Fabric "bbs-client-addon" 入口加载本类（见 fabric.mod.json）。
 * 翻译/源包在 {@link BBSFSloveCMLClient#onInitializeClient()} 另有双保险注册，
 * 与这里的 RegisterL10nEvent 订阅互不冲突。</p>
 */
public class BBSFSloveCMLClientAddon implements BBSAddonMod {

    @Subscribe
    public void onRegisterClientSettings(RegisterClientSettingsEvent event) {
        BBSFSloveCML.LOGGER.info("[FSloveCML] 客户端 addon 正在注册客户端设置...");
    }

    @Subscribe
    public void onRegisterL10n(RegisterL10nEvent event) {
        BBSFSloveCML.LOGGER.info("[FSloveCML] 收到 RegisterL10nEvent");
        BBSFSloveCMLClient.registerTranslations(event.l10n);
    }

    @Subscribe
    public void onRegisterDashboardPanels(RegisterDashboardPanelsEvent event) {
        /* Registered before BBS's default F5 action, so the film panel opens the
         * visibility menu while other dashboard panels keep the original toggle. */
        event.dashboard.overlay.keys().register(Keys.TOGGLE_DEBUG, () -> {
            if (event.dashboard.getPanels().panel instanceof UIFilmPanel panel) {
                FilmVisibilityController.openMenu(panel.getContext());
            } else {
                boolean enabled = !(BBSSettings.ikDebug.enabled.get()
                    || BBSSettings.physicsDebug.enabled.get());

                BBSSettings.ikDebug.enabled.set(enabled);
                BBSSettings.physicsDebug.enabled.set(enabled);
            }
        });
    }
}
