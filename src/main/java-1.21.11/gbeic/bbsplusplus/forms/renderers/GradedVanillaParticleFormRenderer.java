package gbeic.bbsplusplus.forms.renderers;

import gbeic.bbsplusplus.client.particle.VanillaParticleGrade121;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.forms.renderers.VanillaParticleFormRenderer;

/**
 * 1.21.11 版原版粒子着色渲染器：在一个 form 的 tick 期间保持它的颜色状态，
 * 期间发射出来的原版粒子会被 {@code BillboardParticleGradeMixin121} 上色。
 */
public class GradedVanillaParticleFormRenderer extends VanillaParticleFormRenderer
{
    public GradedVanillaParticleFormRenderer(VanillaParticleForm form)
    {
        super(form);
    }

    @Override
    public void tick(IEntity entity)
    {
        VanillaParticleGrade121.push(this.getForm());

        try
        {
            super.tick(entity);
        }
        finally
        {
            VanillaParticleGrade121.pop();
        }
    }
}
