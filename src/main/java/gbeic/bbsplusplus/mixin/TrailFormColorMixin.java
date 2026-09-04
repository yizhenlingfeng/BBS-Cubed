package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.FormColorProvider;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.TrailForm;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.utils.colors.Color;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Supplies the color keyframe missing from Trail and vanilla-particle forms. */
@Mixin(value = {TrailForm.class, VanillaParticleForm.class}, remap = false)
public abstract class TrailFormColorMixin implements FormColorProvider
{
    @Unique
    private ValueColor bbspp_cml$color;

    @Inject(method = "<init>()V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$registerColor(CallbackInfo ci)
    {
        this.bbspp_cml$color = new ValueColor("color", Color.white());
        ((Form) (Object) this).add(this.bbspp_cml$color);
    }

    @Override
    public ValueColor bbspp_cml$getColor()
    {
        return this.bbspp_cml$color;
    }
}
