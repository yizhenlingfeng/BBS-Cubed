package gbeic.bbsplusplus.ui.settings;

import mchorse.bbs_mod.settings.ui.UISettingsLayout;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

import java.util.Collections;
import java.util.List;

/**
 * 给本模组设置页里的自定义行套一层，补上右键「重置数值」。
 *
 * <p>BBS 的设置页对每个值先问 {@code UISettingsLayout.getRow(value)}：返回 null 走标准行
 * （由 {@code UIValueMap.create()} 挂菜单），非 null 就直接用自定义行的 {@code create()}，
 * 完全绕开那条路径。本类包在 {@code getRow()} 的返回值外面，其它设置模块的行原样返回。</p>
 */
public final class UIProjectSettingsRow implements UISettingsLayout.IValueRow
{
    private final UISettingsLayout.IValueRow row;

    private final BaseValue value;

    private UIProjectSettingsRow(UISettingsLayout.IValueRow row, BaseValue value)
    {
        this.row = row;
        this.value = value;
    }

    /** 包住一行；行不属于本模组时原样返回，调用方无需自己判断。 */
    public static UISettingsLayout.IValueRow wrap(UISettingsLayout.IValueRow row)
    {
        if (row == null)
        {
            return null;
        }

        List<BaseValue> values = row.getValues();

        if (values.size() != 1 || !UISettingsRowReset.isProjectValue(values.get(0)))
        {
            return row;
        }

        return new UIProjectSettingsRow(row, values.get(0));
    }

    @Override
    public List<BaseValue> getValues()
    {
        return Collections.singletonList(this.value);
    }

    @Override
    public List<UIElement> create(UIElement ui)
    {
        List<UIElement> elements = this.row.create(ui);

        for (UIElement element : elements)
        {
            UISettingsRowReset.attach(element, this.value, ui);
        }

        return elements;
    }
}
