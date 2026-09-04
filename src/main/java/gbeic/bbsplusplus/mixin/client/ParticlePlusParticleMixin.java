package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.ParticlePlusParticle;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Environment(EnvType.CLIENT)
@Mixin(value = Particle.class, remap = false)
public class ParticlePlusParticleMixin implements ParticlePlusParticle
{
    @Unique
    private boolean bbspp_cml$intersected;

    @Unique
    private StubEntity bbspp_cml$dummy;

    @Override
    public boolean bbspp_cml$isIntersected()
    {
        return this.bbspp_cml$intersected;
    }

    @Override
    public void bbspp_cml$setIntersected(boolean intersected)
    {
        this.bbspp_cml$intersected = intersected;
    }

    @Override
    public StubEntity bbspp_cml$getDummy(ParticleEmitter emitter)
    {
        if (this.bbspp_cml$dummy == null)
        {
            this.bbspp_cml$dummy = new StubEntity(emitter.world);
        }
        else if (this.bbspp_cml$dummy.getWorld() != emitter.world)
        {
            this.bbspp_cml$dummy.setWorld(emitter.world);
        }

        return this.bbspp_cml$dummy;
    }
}
