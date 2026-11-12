package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.settings.ValueSectionHeader;
import gbeic.bbsplusplus.ui.settings.UISettingsRowReset;
import mchorse.bbs_mod.settings.ui.UIValueMap;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 让本模组设置里的标准行在「值等于默认值」时也带右键「重置数值」。
 *
 * <p>原版 {@code UIValueMap.create()} 只给非默认值挂菜单项，刚装好、还没动过的设置
 * 右键是空的。这里在 {@code create} 返回后补上同样的菜单项，与原版那条互斥——
 * 非默认时原版给，默认时这里给，菜单里始终只有一项。</p>
 *
 * <p>只覆盖标准行；自定义行不经过这里，由 {@link UISettingsRowReset} 处理。</p>
 */
@Mixin(value = UIValueMap.class, remap = false)
public class UIValueMapResetMixin
{
    @Inject(method = "create", at = @At("RETURN"))
    private static void bbspp$resettableAtDefault(BaseValue value, UIElement host, CallbackInfoReturnable<List<UIElement>> cir)
    {
        if (value instanceof ValueSectionHeader || !UISettingsRowReset.isProjectValue(value))
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
            UISettingsRowReset.attach(created, value, host);
        }
    }
}
