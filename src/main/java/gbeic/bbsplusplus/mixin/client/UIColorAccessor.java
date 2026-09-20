package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(UIColor.class)
public interface UIColorAccessor
{
    @Invoker("openPresets")
    void bbspp$openPresets(UIContext context);
}
