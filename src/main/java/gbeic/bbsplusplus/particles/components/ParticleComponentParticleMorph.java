package gbeic.bbsplusplus.particles.components;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.math.molang.MolangException;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.particles.components.IComponentParticleInitialize;
import mchorse.bbs_mod.particles.components.IComponentParticleRender;
import mchorse.bbs_mod.particles.components.ParticleComponentBase;
import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class ParticleComponentParticleMorph extends ParticleComponentBase implements IComponentParticleRender, IComponentParticleInitialize
{
    public boolean enabled = true;
    public boolean renderTexture;
    public boolean billboard;
    public MolangExpression scale = MolangParser.ONE;
    public Form form;

    public float getScale()
    {
        try
        {
            return (float) this.scale.get();
        }
        catch (Exception exception)
        {
            return 1F;
        }
    }

    @Override
    public BaseType toData()
    {
        MapType data = new MapType();

        data.putBool("enabled", this.enabled);
        data.putBool("render_texture", this.renderTexture);
        data.putBool("billboard", this.billboard);

        if (!MolangExpression.isOne(this.scale))
        {
            data.put("scale", this.scale.toData());
        }

        if (this.form != null)
        {
            data.put("form", BBSMod.getForms().toData(this.form));
        }

        return data;
    }

    @Override
    public ParticleComponentBase fromData(BaseType data, MolangParser parser) throws MolangException
    {
        if (!data.isMap())
        {
            return this;
        }

        MapType map = data.asMap();

        if (map.has("enabled")) this.enabled = map.getBool("enabled");
        if (map.has("render_texture")) this.renderTexture = map.getBool("render_texture");
        if (map.has("billboard")) this.billboard = map.getBool("billboard");
        if (map.has("scale")) this.scale = parser.parseDataSilently(map.get("scale"));
        if (map.has("form")) this.form = BBSMod.getForms().fromData(map.getMap("form"));

        return this;
    }

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

    @Override
    public int getSortingIndex()
    {
        return 99;
    }
}
