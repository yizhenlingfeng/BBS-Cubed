package gbeic.bbsplusplus;

import mchorse.bbs_mod.api.BBSAddonMod;
import mchorse.bbs_mod.api.Subscribe;
import mchorse.bbs_mod.api.client.events.RegisterClientSettingsEvent;
import mchorse.bbs_mod.api.client.events.RegisterDashboardPanelsEvent;
import mchorse.bbs_mod.api.client.events.RegisterL10nEvent;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.utils.keys.Keybind;
import mchorse.bbs_mod.ui.utils.keys.KeybindManager;
import gbeic.bbsplusplus.forms.sections.BBSPPFormSection;
import gbeic.bbsplusplus.ui.film.FilmVisibilityController;
import gbeic.bbsplusplus.ui.forms.SnowUIKeys;

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

    /**
     * 注册 SnowUIKeys 快捷键类（原 {@code KeybindSettingsMixin} 的官方事件版）。
     *
     * <p>事件在 {@code KeybindSettings.registerClasses()}（只加 BBS 自己的 Keys）
     * 之后、keybinds.json 构建之前派发，{@code event.register(Class)} 与原 Mixin
     * 往同一个扫描列表追加类，行为 1:1 等价。</p>
     */
    @Subscribe
    public void onRegisterKeybinds(mchorse.bbs_mod.api.client.events.RegisterKeybindsEvent event) {
        event.register(SnowUIKeys.class);
    }

    /**
     * 注册 BBS++ 表单拾取器分区（原 {@code ExtraFormSectionMixin} 的官方事件版）。
     *
     * <p>BBS 每次资源重载都会重建整个调色板并重新询问 factory，因此重载安全；
     * 旧 Mixin 挂在 {@code ExtraFormSection.initiate()} TAIL 上也是为此。
     * 行为差异：表单从"杂项"分类移入新的"BBS++"顶层分区（分类可见性 ID
     * {@code bbspp}）——BBS 2.7 的 {@code ExtraFormSection.extra} 是私有的，
     * 官方事件无法往已有分区里追加。</p>
     */
    @Subscribe
    public void onRegisterFormSections(mchorse.bbs_mod.api.client.events.RegisterFormSectionsEvent event) {
        event.register(BBSPPFormSection::new);
    }

    @Subscribe
    public void onRegisterDashboardPanels(RegisterDashboardPanelsEvent event) {
        UIDashboard dashboard = event.dashboard;
        KeybindManager keys = dashboard.overlay.keys();

        /* 影片可见性菜单快捷键（默认 F5，归入按键绑定的「影片控制器」分类，可由用户改绑）。
         * 仅在当前为影片面板时激活；其他面板按 F5 仍走原生的切换 IK/物理调试。
         *
         * BBS 2.7 起，原生 TOGGLE_DEBUG(F5) 在 UIDashboard 构造函数里同步注册，而本事件
         * 被推迟到 buildSteps 末尾才触发；KeybindManager 对分数相同的多个按键取先注册者，
         * 因此单纯 register 会被原生默认键压住。这里把注册后的 Keybind 挪到列表最前，
         * 保证影片面板里按 F5 触发可见性菜单而不是调试开关。
         *
         * 改绑还原：ValueKeyCombo 改绑是原地写回 KeyCombo.keys，Keybind.check() 又以
         * combo.getMainKey() 为准。所以一旦用户把本键改绑到非 F5 的按键，它就不再匹配 F5，
         * 影片面板下按 F5 自动落回原生的切换调试——无需额外 if，置顶只在双方都按同一键时才抢前。 */
        Keybind visibility = keys.register(SnowUIKeys.FILM_VISIBILITY_MENU_KEY, () -> {
            if (dashboard.getPanels().panel instanceof UIFilmPanel panel && panel.getData() != null) {
                FilmVisibilityController.openMenu(panel.getContext());
            }
        });
        /* 可见性菜单只对已打开的影片有意义：Landing 影片库主页（data == null）不激活，
         * 避免主页面按 F5 弹出操作不到任何影片的菜单。 */
        visibility.active(() -> dashboard.getPanels().panel instanceof UIFilmPanel panel && panel.getData() != null);
        visibility.category(UIKeys.FILM_CONTROLLER_KEYS_CATEGORY);

        keys.keybinds.remove(visibility);
        keys.keybinds.add(0, visibility);
    }
}
