package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.ParticlePlusParticle;
import gbeic.bbsplusplus.particles.components.ParticleComponentCollisionAppearance;
import gbeic.bbsplusplus.particles.components.ParticleComponentParticleMorph;
import mchorse.bbs_mod.particles.components.appearance.ParticleComponentAppearanceBillboard;
import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(value = ParticleComponentAppearanceBillboard.class, priority = 1001, remap = false)
public class ParticleComponentAppearanceBillboardMixin
{
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void bbspp_cml$beforeRender(ParticleEmitter emitter, VertexFormat format, Particle particle, BufferBuilder builder, Matrix4f matrix, int overlay, float transition, CallbackInfo ci)
    {
        if (!this.getClass().equals(ParticleComponentAppearanceBillboard.class) || emitter.scheme == null)
        {
            return;
        }

        ParticleComponentCollisionAppearance collision = emitter.scheme.get(ParticleComponentCollisionAppearance.class);

        if (collision != null && ((ParticlePlusParticle) particle).bbspp_cml$isIntersected())
        {
            ci.cancel();

            return;
        }

        ParticleComponentParticleMorph morph = emitter.scheme.get(ParticleComponentParticleMorph.class);

        if (morph != null && morph.enabled && !morph.renderTexture)
        {
            ci.cancel();
        }
    }
}
