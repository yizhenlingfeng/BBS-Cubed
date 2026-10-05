package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenuBar;
import mchorse.bbs_mod.ui.utils.context.MenuIcon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * 暴露 {@link UIContextMenuBar} 的 icons/buttons 内部列表。
 */
@Mixin(UIContextMenuBar.class)
public interface UIContextMenuBarAccessor
{
    @Accessor("icons")
    List<MenuIcon> bbspp$getIcons();

    @Accessor("buttons")
    List<UIIcon> bbspp$getButtons();
}
