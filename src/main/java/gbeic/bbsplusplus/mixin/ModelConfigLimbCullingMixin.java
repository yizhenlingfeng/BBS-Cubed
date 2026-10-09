package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.LimbCullingConfigHolder;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.settings.values.ui.ValueStringKeys;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.Set;

@Mixin(value = ModelConfig.class, remap = false)
public abstract class ModelConfigLimbCullingMixin implements LimbCullingConfigHolder
{
    @Unique
    private ValueStringKeys bbspp$limbCullingBones;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void bbspp$initLimbCullingBones(CallbackInfo ci)
    {
        this.bbspp$limbCullingBones = new ValueStringKeys("limbCullingBones");
        ((ModelConfig) (Object) this).add(this.bbspp$limbCullingBones);
    }

    @Override
    public Set<String> bbspp$getLimbCullingBones()
    {
        if (this.bbspp$limbCullingBones == null)
        {
            return Collections.emptySet();
        }
        Set<String> values = this.bbspp$limbCullingBones.get();
        return values == null ? Collections.emptySet() : values;
    }

    @Override
    public ValueStringKeys bbspp$getLimbCullingBonesValue()
    {
        return this.bbspp$limbCullingBones;
    }
}
