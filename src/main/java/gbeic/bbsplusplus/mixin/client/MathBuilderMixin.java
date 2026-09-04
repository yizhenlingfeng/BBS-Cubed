package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.math.Variable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Molang expressions can register variables while model previews are rendered.
 * Keep the public variable table weakly consistent so render-time iteration and
 * Together/replay updates cannot invalidate each other.
 */
@Mixin(value = MathBuilder.class, remap = false)
public abstract class MathBuilderMixin
{
    @Shadow(remap = false)
    public Map<String, Variable> variables;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void bbspp_cml$useConcurrentVariables(CallbackInfo ci)
    {
        this.variables = new ConcurrentHashMap<>(this.variables);
    }
}
