package bbslezy.mixin.client;

import bbslezy.ui.LezyIrisHelper;
import mchorse.bbs_mod.client.BBSRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BBSRendering.class, remap = false)
public abstract class BBSRenderingMixin
{
    @Inject(method = "toggleFramebuffer(Z)V", at = @At("TAIL"))
    private static void bbslezy$onToggleFramebuffer(boolean toggleFramebuffer, CallbackInfo ci)
    {
        LezyIrisHelper.syncPipelineDepthTarget();
    }
}
