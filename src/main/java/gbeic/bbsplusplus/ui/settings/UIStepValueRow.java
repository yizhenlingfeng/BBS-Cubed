package gbeic.bbsplusplus.ui.settings;

import mchorse.bbs_mod.settings.ui.UISettingsLayout;
import mchorse.bbs_mod.settings.ui.UIValueFactory;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;

import java.util.Collections;
import java.util.List;

/**
 * 数值设置的普通行（标签 + 数值框），只是数值框的拖动步长按设置自身的量级单独给。
 *
 * <p>BBS 默认的 {@code UIValueFactory.floatUI} 新轨道板用原版步长（普通拖动 0.25/像素），
 * 对 0.062 这种小数值几乎没法靠拖动调准，只能手输；这里把 {@code values}/{@code increment}
 * 换成与该项量级匹配的值。剩余的取值/回写、范围限制、注释提示、
 * {@link UIValueFactory#column(UIElement, BaseValue)} 的排布都与原版一致；
 * 右键「重置数值」由 {@link UIProjectSettingsRow} 统一补上。</p>
 */
public class UIStepValueRow implements UISettingsLayout.IValueRow
{
    private final ValueFloat value;

    private final double normal;

    private final double weak;

    private final double strong;

    private final double increment;

    private UIStepValueRow(ValueFloat value, double normal, double weak, double strong, double increment)
    {
        this.value = value;
        this.normal = normal;
        this.weak = weak;
        this.strong = strong;
        this.increment = increment;
    }

    /**
     * @param normal    普通拖动时每像素改变的量
     * @param weak      Alt 拖动时每像素改变的量
     * @param strong    Shift 拖动时每像素改变的量
     * @param increment 点按两侧箭头（以及 Ctrl 拖动）时每次改变的量
     */
    public static UIStepValueRow of(ValueFloat value, double normal, double weak, double strong, double increment)
    {
        return new UIStepValueRow(value, normal, weak, strong, increment);
    }

    @Override
    public List<BaseValue> getValues()
    {
        return Collections.singletonList(this.value);
    }

    @Override
    public List<UIElement> create(UIElement ui)
    {
        UITrackpad trackpad = new UITrackpad((value) -> this.value.set(value.floatValue()));

        trackpad.limit(this.value.getMin(), this.value.getMax());
        trackpad.values(this.normal, this.weak, this.strong);
        trackpad.increment(this.increment);
        trackpad.delayedInput();
        trackpad.valueBinding(() -> trackpad.setValue(this.value.get()));

        return Collections.singletonList(UIValueFactory.column(trackpad, this.value));
    }
}
