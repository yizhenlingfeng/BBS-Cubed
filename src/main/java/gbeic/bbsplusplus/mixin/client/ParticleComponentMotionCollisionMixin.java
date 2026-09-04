package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.ParticlePlusParticle;
import mchorse.bbs_mod.particles.components.motion.ParticleComponentMotionCollision;
import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(value = ParticleComponentMotionCollision.class, remap = false)
public class ParticleComponentMotionCollisionMixin
{
    @Inject(
        method = "update",
        at = @At(
            value = "FIELD",
            target = "Lmchorse/bbs_mod/particles/emitter/Particle;position:Lorg/joml/Vector3d;",
            shift = At.Shift.BEFORE
        )
    )
    private void bbspp_cml$markCollision(ParticleEmitter emitter, Particle particle, CallbackInfo ci)
    {
        ((ParticlePlusParticle) particle).bbspp_cml$setIntersected(true);
    }
}
