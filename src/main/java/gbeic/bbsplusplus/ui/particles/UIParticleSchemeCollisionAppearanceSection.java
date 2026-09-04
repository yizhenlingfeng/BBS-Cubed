package gbeic.bbsplusplus.ui.particles;

import gbeic.bbsplusplus.particles.components.ParticleComponentCollisionAppearance;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeComponentSection;
import mchorse.bbs_mod.ui.particles.utils.UIMolangExpression;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class UIParticleSchemeCollisionAppearanceSection extends UIParticleSchemeComponentSection<ParticleComponentCollisionAppearance>
{
    public final UIMolangExpression enabled = new UIMolangExpression(
        () -> this.component.enabled,
        (button) ->
        {
            this.ensureInScheme();
            this.editMoLang("collision_appearance.enabled", (value) -> this.component.enabled = this.parse(value, this.component.enabled), this.component.enabled);
        }
    );
    public final UIMolangExpression sizeW;
    public final UIMolangExpression sizeH;

    public UIParticleSchemeCollisionAppearanceSection(UIParticleSchemePanel parent)
    {
        super(parent);

        this.enabled.icon(Icons.VISIBLE).tooltip(ParticlePlusUIKeys.COLLISION_CONDITION);
        this.sizeW = new UIMolangExpression(
            () -> this.component.sizeW,
            (button) ->
            {
                this.ensureInScheme();
                this.editMoLang("collision_appearance.size_w", (value) -> this.component.sizeW = this.parse(value, this.component.sizeW), this.component.sizeW);
            }
        );
        this.sizeW.icon(Icons.HORIZONTAL).tooltip(ParticlePlusUIKeys.WIDTH);
        this.sizeH = new UIMolangExpression(
            () -> this.component.sizeH,
            (button) ->
            {
                this.ensureInScheme();
                this.editMoLang("collision_appearance.size_h", (value) -> this.component.sizeH = this.parse(value, this.component.sizeH), this.component.sizeH);
            }
        );
        this.sizeH.icon(Icons.VERTICAL).tooltip(ParticlePlusUIKeys.HEIGHT);
        this.fields.add(new IUIElement[] {this.enabled, this.sizeW, this.sizeH});
    }

    private void ensureInScheme()
    {
        if (this.scheme.get(ParticleComponentCollisionAppearance.class) == null)
        {
            this.scheme.addComponent(this.component);
            this.scheme.setup();
        }
    }

    @Override
    public IKey getTitle()
    {
        return ParticlePlusUIKeys.COLLISION_APPEARANCE_TITLE;
    }

    @Override
    public void setScheme(ParticleScheme scheme)
    {
        this.scheme = scheme;

        ParticleComponentCollisionAppearance existing = scheme.get(ParticleComponentCollisionAppearance.class);

        this.component = existing == null ? new ParticleComponentCollisionAppearance() : existing;
        this.fillData();
    }

    @Override
    protected ParticleComponentCollisionAppearance getComponent(ParticleScheme scheme)
    {
        return scheme.get(ParticleComponentCollisionAppearance.class);
    }
}
