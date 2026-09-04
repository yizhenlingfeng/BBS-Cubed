package gbeic.bbsplusplus.pbr;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.FloatType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PBRKeyframeFactory implements IKeyframeFactory<PBRData>
{
    @Override
    public PBRData fromData(BaseType data)
    {
        PBRData result = new PBRData();

        if (data.isMap())
        {
            MapType map = data.asMap();

            for (String material : map.keys())
            {
                BaseType value = map.get(material);

                if (value.isList() && value.asList().size() >= 5)
                {
                    ListType list = value.asList();
                    result.put(material, new PBRChannel(list.getFloat(0), list.getFloat(1), list.getFloat(2), list.getFloat(3), list.getFloat(4)));
                }
            }
        }

        return result;
    }

    @Override
    public BaseType toData(PBRData value)
    {
        MapType map = new MapType();

        for (Map.Entry<String, PBRChannel> entry : value.getAll().entrySet())
        {
            PBRChannel channel = entry.getValue();

            if (!channel.isDefault())
            {
                ListType list = new ListType();
                list.add(new FloatType(channel.sR));
                list.add(new FloatType(channel.sG));
                list.add(new FloatType(channel.sB));
                list.add(new FloatType(channel.sA));
                list.add(new FloatType(channel.n));
                map.put(entry.getKey(), list);
            }
        }

        return map;
    }

    @Override
    public PBRData createEmpty()
    {
        return new PBRData();
    }

    @Override
    public PBRData copy(PBRData value)
    {
        return value.copy();
    }

    @Override
    public PBRData interpolate(PBRData preA, PBRData a, PBRData b, PBRData postB, IInterp interpolation, float x)
    {
        PBRData result = new PBRData();
        Set<String> materials = new HashSet<>(a.getAll().keySet());
        materials.addAll(b.getAll().keySet());

        for (String material : materials)
        {
            PBRChannel p = preA.get(material);
            PBRChannel av = a.get(material);
            PBRChannel bv = b.get(material);
            PBRChannel q = postB.get(material);
            PBRChannel value = new PBRChannel(
                interpolate(interpolation, p.sR, av.sR, bv.sR, q.sR, x),
                interpolate(interpolation, p.sG, av.sG, bv.sG, q.sG, x),
                interpolate(interpolation, p.sB, av.sB, bv.sB, q.sB, x),
                interpolate(interpolation, p.sA, av.sA, bv.sA, q.sA, x),
                interpolate(interpolation, p.n, av.n, bv.n, q.n, x)
            );
            value.clamp();
            result.put(material, value);
        }

        return result;
    }

    private static float interpolate(IInterp interpolation, float p, float a, float b, float q, float x)
    {
        return (float) interpolation.interpolate(IInterp.context.set(p, a, b, q, x));
    }
}
