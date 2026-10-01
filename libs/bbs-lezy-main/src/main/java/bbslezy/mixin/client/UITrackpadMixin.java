package bbslezy.mixin.client;

import bbslezy.utils.LezyOS;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UINumericInput;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = UITrackpad.class, remap = false)
public abstract class UITrackpadMixin extends UINumericInput<UITrackpad>
{
    @Shadow
    private boolean dragging;

    private boolean bbslezy$holdingCursor;

    @Inject(method = "render(Lmchorse/bbs_mod/ui/framework/UIContext;)V", at = @At("HEAD"))
    private void bbslezy$beforeRender(UIContext context, CallbackInfo ci)
    {
        this.bbslezy$holdingCursor = LezyOS.updateCursorHold(this, this.bbslezy$holdingCursor, this.isDraggingTime() && Window.isMouseButtonPressed(0));
    }

    @Inject(method = "subMouseClicked(Lmchorse/bbs_mod/ui/framework/UIContext;)Z", at = @At("RETURN"))
    private void bbslezy$afterMouseClicked(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        if (!this.dragging)
        {
            this.bbslezy$holdingCursor = LezyOS.updateCursorHold(this, this.bbslezy$holdingCursor, false);
        }
    }

    @Inject(method = "subMouseReleased(Lmchorse/bbs_mod/ui/framework/UIContext;)Z", at = @At("RETURN"))
    private void bbslezy$afterMouseReleased(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        this.bbslezy$holdingCursor = LezyOS.updateCursorHold(this, this.bbslezy$holdingCursor, false);
    }
}
