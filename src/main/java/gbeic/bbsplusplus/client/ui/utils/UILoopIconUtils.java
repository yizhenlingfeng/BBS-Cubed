package gbeic.bbsplusplus.client.ui.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;

/**
 * 为录像编辑器添加循环模式图标。
 * <p>
 * BBS 2.6 已把循环切换做成原生按键（Keys.LOOPING），这里仍在面板操作栏补一个
 * 可见的循环按钮，点击后切换循环模式开关并弹出提示。2.6 取消了旧的 tabBar，
 * 改为经 {@code panel.actions()} 的操作栏统一布局。
 * </p>
 */
public class UILoopIconUtils
{
    public static void addLoopIcon(UIFilmPanel panel)
    {
        UILoopIcon loopIcon = new UILoopIcon(Icons.REFRESH, (b) ->
        {
            BBSSettings.editorLoop.set(!BBSSettings.editorLoop.get());
            panel.getContext().notifyInfo(UIKeys.CAMERA_EDITOR_KEYS_LOOPING_TOGGLE_NOTIFICATION);
        });

        loopIcon.tooltip(L10n.lang("bbs.ui.film.looping_enabled"), Direction.BOTTOM);

        panel.actions().action(loopIcon);
    }
}
