package gbeic.bbsplusplus.particles.components;

import mchorse.bbs_mod.particles.components.IComponentParticleInitialize;
import mchorse.bbs_mod.particles.components.IComponentParticleRender;
import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import org.joml.Matrix4f;

/**
 * Particle Plus particle morph component for 1.20.x.
 *
 * <p>
 * The state, serialization and sorting index are shared through
 * {@link ParticleComponentParticleMorphBase}. This class only supplies the render interface
 * methods, whose signatures depend on the {@code VertexFormat} package of this Minecraft
 * version; they are intentionally empty because the morph is applied by the emitter mixins.
 * </p>
 */
@Environment(EnvType.CLIENT)
public class ParticleComponentParticleMorph extends ParticleComponentParticleMorphBase implements IComponentParticleRender, IComponentParticleInitialize
{
    @Override
    public void apply(ParticleEmitter emitter, Particle particle)
    {}

    @Override
    public void preRender(ParticleEmitter emitter, float transition)
    {}

    @Override
    public void render(ParticleEmitter emitter, VertexFormat format, Particle particle, BufferBuilder builder, Matrix4f matrix, int overlay, float transition)
    {}

    @Override
    public void renderUI(Particle particle, BufferBuilder builder, Matrix4f matrix, float transition)
    {}

    @Override
    public void postRender(ParticleEmitter emitter, float transition)
    {}
}
