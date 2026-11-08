package routu.bbsuirabbit.mixin;

import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.function.Consumer;

/** BBS 2.7 dropped UIElement#resetContext; the film library swaps a list's context menu with this. */
@Mixin(value = UIElement.class, remap = false)
public interface UIElementContextAccessor
{
    @Accessor(value = "contextOptions", remap = false)
    List<Consumer<ContextMenuManager>> rabbit$getContextOptions();

    @Accessor(value = "contextOptions", remap = false)
    void rabbit$setContextOptions(List<Consumer<ContextMenuManager>> options);
}
