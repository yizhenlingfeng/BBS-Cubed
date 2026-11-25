package gbeic.bbsplusplus.particles.components;

import gbeic.bbsplusplus.api.ParticlePlusParticle;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.molang.MolangException;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.particles.emitter.Particle;

/**
 * Serialization and visibility test shared by the two collision appearance components.
 *
 * <p>
 * The two components extend different upstream appearance components, so they cannot share a
 * base class; the logic they do have in common is collected here instead.
 * </p>
 */
public final class ParticleCollisionSupport
{
    private ParticleCollisionSupport()
    {}

    /**
     * Adds the {@code enabled} expression to the data produced by the parent component.
     *
     * <p>
     * The map is passed in rather than fetched from the component, because reading it through
     * the component would call the overriding {@code toData()} again and recurse.</p>
     */
    public static BaseType decorate(BaseType data, MolangExpression enabled)
    {
        MapType map = (MapType) data;

        if (!MolangExpression.isZero(enabled))
        {
            map.put("enabled", enabled.toData());
        }

        return map;
    }

    /**
     * Reads the {@code enabled} expression after the parent component restored its own data.
     */
    public static MolangExpression parseEnabled(BaseType data, MolangParser parser, MolangExpression current) throws MolangException
    {
        if (data.isMap() && data.asMap().has("enabled"))
        {
            return parser.parseDataSilently(data.asMap().get("enabled"));
        }

        return current;
    }

    /**
     * Whether the particle should be drawn: it must have collided with something, and the
     * {@code enabled} expression must be absent or evaluate to a positive value.
     */
    public static boolean shouldRender(Particle particle, MolangExpression enabled)
    {
        boolean intersected = ((ParticlePlusParticle) particle).bbspp_cml$isIntersected();
        boolean condition = MolangExpression.isZero(enabled) || enabled.get() > 0D;

        return intersected && condition;
    }
}
