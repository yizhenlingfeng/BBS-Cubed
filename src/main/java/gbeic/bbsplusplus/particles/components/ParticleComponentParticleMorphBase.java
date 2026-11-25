package gbeic.bbsplusplus.particles.components;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.math.molang.MolangException;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.particles.components.ParticleComponentBase;

/**
 * Shared state and serialization for the Particle Plus particle morph component.
 *
 * <p>
 * The component itself has to stay version specific because the render interface it implements
 * takes a {@code VertexFormat}, whose package moved between 1.20.x and 1.21.11. Everything that
 * does not depend on that type lives here so both targets share a single copy.
 * </p>
 */
public abstract class ParticleComponentParticleMorphBase extends ParticleComponentBase
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

    /**
     * Sorting index used by the render pipeline. It is not annotated with {@code @Override}
     * because the render interface is implemented by the version specific subclass, not here.
     */
    public int getSortingIndex()
    {
        return 99;
    }
}
