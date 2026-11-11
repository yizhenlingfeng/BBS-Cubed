package gbeic.bbsplusplus.forms.renderers;

import gbeic.bbsplusplus.client.screen.VanillaParticleGradeContext;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.forms.renderers.VanillaParticleFormRenderer;

/** Keeps one form's grade active while its vanilla particles are created. */
public class GradedVanillaParticleFormRenderer extends VanillaParticleFormRenderer
{
    public GradedVanillaParticleFormRenderer(VanillaParticleForm form)
    {
        super(form);
    }

    @Override
    public void tick(IEntity entity)
    {
        VanillaParticleGradeContext.push(this.getForm());

        try
        {
            super.tick(entity);
        }
        finally
        {
            VanillaParticleGradeContext.pop();
        }
    }
}
