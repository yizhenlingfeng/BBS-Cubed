package gbeic.bbsplusplus.mixin.client.accessor;

import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.layout.UIDockLayout;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * 暴露 {@link UIDockLayout} 的私有映射表，供小窗布局、面板槽与图标访问使用。
 *
 * <p>图标只能通过 {@code iconById} 读取，不能用 {@code @Invoker("getDockPanelIcon")}：
 * 宿主接口已有同名方法，注入后会形成无限递归。布局根走公有的
 * {@code getLayoutRoot()} 与 {@code applyLayoutRoot()}。</p>
 */
@Mixin(value = UIDockLayout.class, remap = false)
public interface UIDockLayoutAccess
{
    @Accessor(value = "slotById", remap = false)
    Map<String, UIElement> bbspp_cml$getSlotById();

    @Accessor(value = "iconById", remap = false)
    Map<String, Icon> bbspp_cml$getIconById();
}
