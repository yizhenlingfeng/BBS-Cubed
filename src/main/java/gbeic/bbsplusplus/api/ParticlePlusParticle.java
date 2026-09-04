package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface ParticlePlusParticle
{
    boolean bbspp_cml$isIntersected();

    void bbspp_cml$setIntersected(boolean intersected);

    StubEntity bbspp_cml$getDummy(ParticleEmitter emitter);
}
