package gbeic.bbsplusplus.particles.components;

import gbeic.bbsplusplus.api.ParticlePlusParticle;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.molang.MolangException;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.particles.components.ParticleComponentBase;
import mchorse.bbs_mod.particles.components.appearance.ParticleComponentAppearanceTinting;
import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class ParticleComponentCollisionTinting extends ParticleComponentAppearanceTinting
{
    public MolangExpression enabled = MolangParser.ZERO;

    @Override
    public BaseType toData()
    {
        MapType data = (MapType) super.toData();

        if (!MolangExpression.isZero(this.enabled))
        {
            data.put("enabled", this.enabled.toData());
        }

        return data;
    }

    @Override
    public ParticleComponentBase fromData(BaseType data, MolangParser parser) throws MolangException
    {
        super.fromData(data, parser);

        if (data.isMap() && data.asMap().has("enabled"))
        {
            this.enabled = parser.parseDataSilently(data.asMap().get("enabled"));
        }

        return this;
    }

    @Override
    public void render(ParticleEmitter emitter, VertexFormat format, Particle particle, BufferBuilder builder, Matrix4f matrix, int overlay, float transition)
    {
        boolean intersected = ((ParticlePlusParticle) particle).bbspp_cml$isIntersected();
        boolean condition = MolangExpression.isZero(this.enabled) || this.enabled.get() > 0D;

        if (intersected && condition)
        {
            super.render(emitter, format, particle, builder, matrix, overlay, transition);
        }
    }

    @Override
    public int getSortingIndex()
    {
        return -5;
    }
}
