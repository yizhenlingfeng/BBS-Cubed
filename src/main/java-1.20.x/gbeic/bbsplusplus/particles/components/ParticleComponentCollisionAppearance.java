package gbeic.bbsplusplus.particles.components;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.math.molang.MolangException;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.particles.components.ParticleComponentBase;
import mchorse.bbs_mod.particles.components.appearance.ParticleComponentAppearanceBillboard;
import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import org.joml.Matrix4f;

/**
 * Collision appearance component for 1.20.x.
 *
 * <p>
 * Serialization and the collision test are shared through {@link ParticleCollisionSupport};
 * only the render method is version specific because its signature depends on the
 * {@code VertexFormat} package of this Minecraft version.
 * </p>
 */
@Environment(EnvType.CLIENT)
public class ParticleComponentCollisionAppearance extends ParticleComponentAppearanceBillboard
{
    public MolangExpression enabled = MolangParser.ZERO;

    @Override
    public BaseType toData()
    {
        return ParticleCollisionSupport.decorate(super.toData(), this.enabled);
    }

    @Override
    public ParticleComponentBase fromData(BaseType data, MolangParser parser) throws MolangException
    {
        super.fromData(data, parser);

        this.enabled = ParticleCollisionSupport.parseEnabled(data, parser, this.enabled);

        return this;
    }

    @Override
    public void render(ParticleEmitter emitter, VertexFormat format, Particle particle, BufferBuilder builder, Matrix4f matrix, int overlay, float transition)
    {
        if (ParticleCollisionSupport.shouldRender(particle, this.enabled))
        {
            super.render(emitter, format, particle, builder, matrix, overlay, transition);
        }
    }

    @Override
    public int getSortingIndex()
    {
        return 90;
    }
}
