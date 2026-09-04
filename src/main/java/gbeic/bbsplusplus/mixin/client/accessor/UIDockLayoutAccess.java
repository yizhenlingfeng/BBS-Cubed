package gbeic.bbsplusplus.mixin.client.accessor;

import gbeic.bbsplusplus.api.UIDockLayoutAccessor;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.layout.UIDockLayout;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * 注意:不要 @Invoker("getDockPanelIcon")——会与宿主接口同名方法形成递归。
 * 图标直接读 iconById;布局根走 2.5 公有 getLayoutRoot()/applyLayoutRoot()。
 */
@Mixin(value = UIDockLayout.class, remap = false)
public interface UIDockLayoutAccess extends UIDockLayoutAccessor
{
    @Override
    @Accessor(value = "slotById", remap = false)
    Map<String, UIElement> bbspp_cml$getSlotById();

    @Override
    @Accessor(value = "iconById", remap = false)
    Map<String, Icon> bbspp_cml$getIconById();
}
