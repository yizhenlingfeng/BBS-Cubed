package bbslezy.mixin.client;

import bbslezy.ui.LezyIrisHelper;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ShaderCurves.class, remap = false)
public abstract class ShaderCurvesMixin
{
    @Inject(method = "finishLoading", at = @At("TAIL"))
    private static void bbslezy$onShaderpackLoaded(CallbackInfo ci)
    {
        LezyIrisHelper.onShaderpackLoaded();
    }
}
