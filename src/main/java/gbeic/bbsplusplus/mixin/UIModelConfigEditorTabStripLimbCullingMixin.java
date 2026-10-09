package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.LimbCullingTabAccessor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.model_editor.UIModelConfigEditor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "mchorse.bbs_mod.ui.model_editor.UIModelConfigEditor$1", remap = false)
public abstract class UIModelConfigEditorTabStripLimbCullingMixin
{
    @Shadow
    @Final
    UIModelConfigEditor this$0;

    @Inject(method = "pressTab(ILmchorse/bbs_mod/ui/framework/UIContext;)Z", at = @At("HEAD"), cancellable = true)
    private void bbspp$interceptLimbCullingTab(int index, UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        if (index == 8 && this.this$0 instanceof LimbCullingTabAccessor accessor)
        {
            accessor.bbspp$showLimbCullingTab();
            cir.setReturnValue(true);
        }
    }
}
