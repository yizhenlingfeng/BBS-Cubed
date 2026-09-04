package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.ui.particles.UIParticleSchemeCollisionAppearanceSection;
import gbeic.bbsplusplus.ui.particles.UIParticleSchemeCollisionTintingSection;
import gbeic.bbsplusplus.ui.particles.ParticleEditorFileControls;
import gbeic.bbsplusplus.ui.particles.UIParticleSchemeMorphSection;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeSection;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Environment(EnvType.CLIENT)
@Mixin(value = UIParticleSchemePanel.class, priority = 1002, remap = false)
public class UIParticleSchemePanelMixin
{
    @Shadow
    public List<UIParticleSchemeSection> sections;

    @Shadow
    public UIScrollView appearanceView;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bbspp_cml$addParticlePlusSections(UIDashboard dashboard, CallbackInfo ci)
    {
        UIParticleSchemePanel panel = (UIParticleSchemePanel) (Object) this;
        UIParticleSchemeMorphSection morph = new UIParticleSchemeMorphSection(panel);
        UIParticleSchemeCollisionAppearanceSection collisionAppearance = new UIParticleSchemeCollisionAppearanceSection(panel);
        UIParticleSchemeCollisionTintingSection collisionTinting = new UIParticleSchemeCollisionTintingSection(panel);

        this.sections.add(morph);
        this.appearanceView.add(morph);
        this.sections.add(collisionAppearance);
        this.appearanceView.add(collisionAppearance);
        this.sections.add(collisionTinting);
        this.appearanceView.add(collisionTinting);
        panel.openOverlay.callback = (button) -> UIOverlay.addOverlay(panel.getContext(), panel.overlay, 200, 0.9F);
        ParticleEditorFileControls.attach(panel);
    }
}
