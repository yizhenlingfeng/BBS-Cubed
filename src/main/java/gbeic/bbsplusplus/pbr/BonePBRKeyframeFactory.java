package gbeic.bbsplusplus.pbr;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

import java.util.HashSet;
import java.util.Set;

public class BonePBRKeyframeFactory implements IKeyframeFactory<BonePBRData>
{
    @Override
    public BonePBRData fromData(BaseType data)
    {
        BonePBRData result = new BonePBRData();

        if (data.isMap())
        {
            result.fromData(data.asMap());
        }

        return result;
    }

    @Override
    public BaseType toData(BonePBRData value)
    {
        return value.toData();
    }

    @Override
    public BonePBRData createEmpty()
    {
        return new BonePBRData();
    }

    @Override
    public BonePBRData copy(BonePBRData value)
    {
        return value.copy();
    }

    @Override
    public BonePBRData interpolate(BonePBRData preA, BonePBRData a, BonePBRData b, BonePBRData postB, IInterp interpolation, float x)
    {
        BonePBRData result = new BonePBRData();
        Set<String> bones = new HashSet<>(a.getAll().keySet());
        bones.addAll(b.getAll().keySet());

        for (String bone : bones)
        {
            PBRChannel p = preA.get(bone);
            PBRChannel av = a.get(bone);
            PBRChannel bv = b.get(bone);
            PBRChannel q = postB.get(bone);
            PBRChannel value = new PBRChannel(
                interpolate(interpolation, p.sR, av.sR, bv.sR, q.sR, x),
                interpolate(interpolation, p.sG, av.sG, bv.sG, q.sG, x),
                interpolate(interpolation, p.sB, av.sB, bv.sB, q.sB, x),
                interpolate(interpolation, p.sA, av.sA, bv.sA, q.sA, x),
                interpolate(interpolation, p.n, av.n, bv.n, q.n, x),
                interpolate(interpolation, p.emissionMultiplier, av.emissionMultiplier, bv.emissionMultiplier, q.emissionMultiplier, x)
            );
            value.clamp();
            result.put(bone, value);
        }

        return result;
    }

    private static float interpolate(IInterp interpolation, float p, float a, float b, float q, float x)
    {
        return (float) interpolation.interpolate(IInterp.context.set(p, a, b, q, x));
    }
}
