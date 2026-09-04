package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UISection.class, remap = false)
public abstract class UISectionMixin
{
    @Inject(method = "resizeParent", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp_cml$resizeContainingScrollView(CallbackInfo ci)
    {
        UIElement parent = ((UISection) (Object) this).getParent();

        if (parent == null)
        {
            ci.cancel();

            return;
        }

        UIElement element = parent;

        while (element != null && !(element instanceof UIScrollView))
        {
            element = element.getParent();
        }

        (element == null ? parent : element).resize();
        ci.cancel();
    }
}
