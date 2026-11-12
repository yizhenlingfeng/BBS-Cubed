package gbeic.bbsplusplus.ui.settings;

import mchorse.bbs_mod.settings.Settings;
import mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.values.UIValues;

/**
 * 给 BBS Cubed 自己的设置项挂上原版右键「重置数值」。
 *
 * <p>原版只在值不等于默认值时才提供该项——{@code UIValues.resettable()} 里的
 * {@code isDefault()} 直接返回——所以刚装好、还没改过的设置右键是空的；而走
 * {@code UISettingsLayout} 自定义行的设置连这条路径都不经过。这里把两种情况补齐：
 * 值非默认时交给原版（颜色块还能顺便拿到默认色），值等于默认值时补一条同名项，
 * 点击是空操作。两条路径互斥，菜单里始终只有一项。</p>
 */
public final class UISettingsRowReset
{
    /** 本模组设置模块的 id，与 {@code BBSPlusPlusModClient} 里 {@code setupConfig(..., "bbspp", ...)} 一致。 */
    private static final String SETTINGS_ID = "bbspp";

    private UISettingsRowReset()
    {}

    /** 这个值是否属于本模组的设置模块（{@code bbspp.json}）。 */
    public static boolean isProjectValue(BaseValue value)
    {
        return value != null && value.getRoot() instanceof Settings settings
            && SETTINGS_ID.equals(settings.getId());
    }

    /**
     * 给一行设置挂上右键「重置数值」。
     *
     * @param element 承载右键菜单的元素（整行）
     * @param value  这一行编辑的值
     * @param host   {@code IValueRow.create()} 收到的宿主元素，一般是设置页本身，用于重置后刷新
     */
    public static <T extends UIElement> T attach(T element, BaseValue value, UIElement host)
    {
        if (value == null)
        {
            return element;
        }

        Runnable refresh = host instanceof UISettingsOverlayPanel panel ? panel::refresh : null;

        UIValues.resettable(element, value, refresh);

        /* 默认值时原版不提供该项，这里补一条，使每一行都有相同的右键菜单 */
        element.context((menu) ->
        {
            if (value.isDefault())
            {
                menu.action(Icons.UNDO, UIKeys.VALUE_RESET, value::reset);
            }
        });

        return element;
    }
}
