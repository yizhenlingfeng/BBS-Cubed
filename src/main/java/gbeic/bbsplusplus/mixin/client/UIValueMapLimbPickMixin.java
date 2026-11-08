package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.ui.forms.SnowUIKeys;
import mchorse.bbs_mod.settings.ui.UIValueMap;
import mchorse.bbs_mod.settings.value.ValueKeyCombo;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 为「肢体选择」的按键绑定行提供分步教程的悬停描述。
 *
 * <p>BBS 给这一行设的 tooltip 就是快捷键自己的名字，这里换成多行教程文案，只影响
 * {@code ignore_picking_override} 一个选项。不挂在 {@code UIKeybind} 上的原因：{@code setKeyCombo}
 * 内部走 {@code KeyCombo.copy}，只复制按键组合、不复制 id，元素上拿不到选项身份。</p>
 */
@Mixin(value = UIValueMap.class, remap = false)
public class UIValueMapLimbPickMixin
{
    /** 教程描述的提示框宽度（像素），够放一行完整的操作步骤。 */
    private static final int DESCRIPTION_WIDTH = 260;

    @Inject(method = "create", at = @At("RETURN"))
    private static void bbspp$limbPickDescription(BaseValue value, UIElement element, CallbackInfoReturnable<List<UIElement>> cir)
    {
        if (!(value instanceof ValueKeyCombo combo) || !SnowUIKeys.IGNORE_PICKING_OVERRIDE_KEY.id.equals(combo.getId()))
        {
            return;
        }

        List<UIElement> elements = cir.getReturnValue();

        if (elements == null)
        {
            return;
        }

        for (UIElement created : elements)
        {
            created.tooltip(SnowUIKeys.IGNORE_PICKING_OVERRIDE_DESC, DESCRIPTION_WIDTH, Direction.BOTTOM);
        }
    }
}
