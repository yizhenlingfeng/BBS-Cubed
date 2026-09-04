package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.layout.UIDockLayout;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * 暴露 {@link UIDockLayout} 的面板槽与图标注册表给运行时代码使用。
 *
 * <p>2.5 起 UIDockLayout 以 {@code slotById}(UIDockSlot 包装器,继承 UIElement)
 * 注册面板、以布局树为唯一事实来源;布局根改走公开 API
 * {@code getLayoutRoot()/applyLayoutRoot()},不再需要 @Invoker。</p>
 */
@Mixin(value = UIDockLayout.class, remap = false)
public interface UIDockLayoutAccessor
{
    /** 值实为 UIDockSlot(UIElement 子类),仅作槽容器枚举/重挂使用;面板本体走 {@code getPanel(id)}。 */
    @Accessor(value = "slotById", remap = false)
    Map<String, UIElement> bbspp_cml$getSlotById();

    @Accessor(value = "iconById", remap = false)
    Map<String, Icon> bbspp_cml$getIconById();
}
