package gbeic.bbsplusplus.ui.particles;

import gbeic.bbsplusplus.particles.components.ParticleComponentCollisionTinting;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeComponentSection;
import mchorse.bbs_mod.ui.particles.utils.UIMolangExpression;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class UIParticleSchemeCollisionTintingSection extends UIParticleSchemeComponentSection<ParticleComponentCollisionTinting>
{
    public final UIMolangExpression enabled = new UIMolangExpression(
        () -> this.component.enabled,
        (button) ->
        {
            this.ensureInScheme();
            this.editMoLang("collision_tinting.enabled", (value) -> this.component.enabled = this.parse(value, this.component.enabled), this.component.enabled);
        }
    );

    public UIParticleSchemeCollisionTintingSection(UIParticleSchemePanel parent)
    {
        super(parent);

        this.enabled.icon(Icons.VISIBLE).tooltip(ParticlePlusUIKeys.COLLISION_CONDITION);
        this.fields.add(this.enabled);
    }

    private void ensureInScheme()
    {
        if (this.scheme.get(ParticleComponentCollisionTinting.class) == null)
        {
            this.scheme.addComponent(this.component);
            this.scheme.setup();
        }
    }

    @Override
    public IKey getTitle()
    {
        return ParticlePlusUIKeys.COLLISION_TINTING_TITLE;
    }

    @Override
    public void setScheme(ParticleScheme scheme)
    {
        this.scheme = scheme;

        ParticleComponentCollisionTinting existing = scheme.get(ParticleComponentCollisionTinting.class);

        this.component = existing == null ? new ParticleComponentCollisionTinting() : existing;
        this.fillData();
    }

    @Override
    protected ParticleComponentCollisionTinting getComponent(ParticleScheme scheme)
    {
        return scheme.get(ParticleComponentCollisionTinting.class);
    }
}
