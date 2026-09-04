package gbeic.bbsplusplus.ui.particles;

import gbeic.bbsplusplus.particles.components.ParticleComponentParticleMorph;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.forms.UINestedEdit;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeComponentSection;
import mchorse.bbs_mod.ui.particles.utils.UIMolangExpression;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class UIParticleSchemeMorphSection extends UIParticleSchemeComponentSection<ParticleComponentParticleMorph>
{
    public final UIToggle enabled = new UIToggle(ParticlePlusUIKeys.MORPH_ENABLED, (button) ->
    {
        if (button.getValue())
        {
            this.ensureInScheme();
            this.component.enabled = true;
        }
        else
        {
            this.component.enabled = false;
            this.scheme.remove(ParticleComponentParticleMorph.class);
            this.scheme.setup();
        }

        this.editor.dirty();
    });
    public final UINestedEdit pickMorph = new UINestedEdit((editing) -> UIFormPalette.open(this.editor, editing, this.component.form, (form) ->
    {
        this.ensureInScheme();
        this.component.form = form;
        this.pickMorph.setForm(form);
        this.editor.dirty();
    }));
    public final UIMolangExpression scale = new UIMolangExpression(
        () -> this.component.scale,
        (button) ->
        {
            this.ensureInScheme();
            this.editMoLang("morph.scale", (value) -> this.component.scale = this.parse(value, this.component.scale), this.component.scale);
        }
    );
    public final UIToggle billboard;
    public final UIToggle renderTexture;

    public UIParticleSchemeMorphSection(UIParticleSchemePanel parent)
    {
        super(parent);

        this.scale.icon(Icons.FULLSCREEN).tooltip(ParticlePlusUIKeys.MORPH_SCALE);
        this.billboard = new UIToggle(ParticlePlusUIKeys.MORPH_BILLBOARD, (button) ->
        {
            this.ensureInScheme();
            this.component.billboard = button.getValue();
            this.editor.dirty();
        });
        this.renderTexture = new UIToggle(ParticlePlusUIKeys.MORPH_RENDER_TEXTURE, (button) ->
        {
            this.ensureInScheme();
            this.component.renderTexture = button.getValue();
            this.editor.dirty();
        });
        this.fields.add(new IUIElement[] {this.enabled, this.pickMorph, this.scale, this.billboard, this.renderTexture});
    }

    private void ensureInScheme()
    {
        if (this.scheme.get(ParticleComponentParticleMorph.class) == null)
        {
            this.scheme.addComponent(this.component);
            this.scheme.setup();
            this.component.enabled = true;
            this.enabled.setValue(true);
        }
    }

    @Override
    public IKey getTitle()
    {
        return ParticlePlusUIKeys.MORPH_TITLE;
    }

    @Override
    public void setScheme(ParticleScheme scheme)
    {
        this.scheme = scheme;

        ParticleComponentParticleMorph existing = scheme.get(ParticleComponentParticleMorph.class);

        if (existing == null)
        {
            this.component = new ParticleComponentParticleMorph();
            this.component.enabled = false;
        }
        else
        {
            this.component = existing;
        }

        this.fillData();
    }

    @Override
    protected ParticleComponentParticleMorph getComponent(ParticleScheme scheme)
    {
        return scheme.get(ParticleComponentParticleMorph.class);
    }

    @Override
    protected void fillData()
    {
        boolean inScheme = this.scheme != null && this.scheme.get(ParticleComponentParticleMorph.class) != null;

        this.enabled.setValue(inScheme && this.component.enabled);
        this.billboard.setValue(this.component.billboard);
        this.renderTexture.setValue(this.component.renderTexture);
        this.pickMorph.setForm(this.component.form);
    }
}
