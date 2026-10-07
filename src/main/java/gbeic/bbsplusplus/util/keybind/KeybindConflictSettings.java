package gbeic.bbsplusplus.util.keybind;

import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;

/**
 * 快捷键冲突提示开关。
 *
 * <p>注册于 BBS++ 独立设置模块的「BBS 增强」分类，默认关闭。
 * 关闭时按键绑定页面不做任何额外绘制，开启后重复按键会被标黄并显示叹号。</p>
 */
public final class KeybindConflictSettings
{
    public static ValueBoolean showBadges;

    private KeybindConflictSettings() {}

    public static boolean isEnabled()
    {
        return showBadges != null && showBadges.get();
    }
}
