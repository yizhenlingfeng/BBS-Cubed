package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.TextureGradeProvider;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.forms.forms.TrailForm;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.colors.Color;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds replay-editable texture grading to the renderable forms in Miscellaneous. */
@Mixin(value = {
    BillboardForm.class,
    BlockForm.class,
    ExtrudedForm.class,
    ItemForm.class,
    LabelForm.class,
    TrailForm.class,
    VanillaParticleForm.class
}, remap = false)
public abstract class MiscFormTextureGradeMixin implements TextureGradeProvider
{
    @Unique
    private ValueColor bbspp_cml$textureTint;

    @Unique
    private ValueFloat bbspp_cml$textureWhiten;

    @Inject(method = "<init>()V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$registerTextureGrade(CallbackInfo ci)
    {
        Form form = (Form) (Object) this;

        this.bbspp_cml$textureTint = new ValueColor("texture_tint", new Color(1F, 1F, 1F, 0F));
        this.bbspp_cml$textureWhiten = new ValueFloat("texture_whiten", 0F, 0F, 1F);
        form.add(this.bbspp_cml$textureTint);
        form.add(this.bbspp_cml$textureWhiten);
    }

    @Override
    public ValueColor bbspp_cml$getTextureTint()
    {
        return this.bbspp_cml$textureTint;
    }

    @Override
    public ValueFloat bbspp_cml$getTextureWhiten()
    {
        return this.bbspp_cml$textureWhiten;
    }
}
