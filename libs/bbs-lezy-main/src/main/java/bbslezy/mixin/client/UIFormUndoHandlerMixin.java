package bbslezy.mixin.client;

import bbslezy.ui.LezyUndoHelper;
import mchorse.bbs_mod.ui.forms.editors.UIFormUndoHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents intermediate frame-by-frame undo submissions while batch replay
 * operations (like Look At baking) are in progress, ensuring all changes are
 * collected into a single compound undo entry.
 */
@Mixin(value = UIFormUndoHandler.class, remap = false)
public abstract class UIFormUndoHandlerMixin
{
    @Inject(method = "submitUndo(Z)V", at = @At("HEAD"), cancellable = true)
    private void bbslezy$onCancelSubmitUndo(boolean force, CallbackInfo ci)
    {
        if (LezyUndoHelper.isBatchLocked() && !force)
        {
            ci.cancel();
        }
    }
}
