package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.particles.ParticlePlusClient;
import gbeic.bbsplusplus.ui.particles.ParticlePlusUIKeys;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcons;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeGeneralSection;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(value = UIParticleSchemeGeneralSection.class, priority = 1002, remap = false)
public class UIParticleSchemeGeneralSectionMixin
{
    @Shadow
    public UIIcons material;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bbspp_cml$addAdditiveMaterial(UIParticleSchemePanel parent, CallbackInfo ci)
    {
        if (ParticlePlusClient.hasAdditiveMaterial())
        {
            Link texture = Link.assets("bbspp/textures/additive.png");

            this.material.add(new Icon(texture, "additive", 0, 0, 16, 16, 16, 16), ParticlePlusUIKeys.ADDITIVE);
        }
    }
}
