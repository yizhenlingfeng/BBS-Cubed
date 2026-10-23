package gbeic.bbsplusplus.mixin.client.accessor;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@code UIList#getIndexAtCursor(UIContext)}，供通用 {@code UIElement} 注入在
 * 实际列表类型上解析被点中的行。
 */
@Mixin(value = UIList.class, remap = false)
public interface UIListAccessor
{
    @Invoker(value = "getIndexAtCursor", remap = false)
    int bbspp$getIndexAtCursor(UIContext context);
}
