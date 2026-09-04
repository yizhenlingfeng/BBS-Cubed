package gbeic.bbsplusplus.pbr;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.FloatType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.LinkedHashMap;
import java.util.Map;

public class BonePBRData
{
    private final Map<String, PBRChannel> bones = new LinkedHashMap<>();

    public PBRChannel get(String bone)
    {
        return this.bones.computeIfAbsent(bone, key -> new PBRChannel());
    }

    public void put(String bone, PBRChannel channel)
    {
        this.bones.put(bone, channel);
    }

    public Map<String, PBRChannel> getAll()
    {
        return this.bones;
    }

    public BonePBRData copy()
    {
        BonePBRData copy = new BonePBRData();

        for (Map.Entry<String, PBRChannel> entry : this.bones.entrySet())
        {
            copy.put(entry.getKey(), entry.getValue().copy());
        }

        return copy;
    }

    public Map<String, Map<String, Integer>> toIntMap()
    {
        Map<String, Map<String, Integer>> result = new LinkedHashMap<>();

        for (Map.Entry<String, PBRChannel> entry : this.bones.entrySet())
        {
            if (!entry.getValue().isDefault())
            {
                result.put(entry.getKey(), entry.getValue().toIntMap());
            }
        }

        return result;
    }

    public MapType toData()
    {
        MapType map = new MapType();

        for (Map.Entry<String, PBRChannel> entry : this.bones.entrySet())
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
                list.add(new FloatType(channel.emissionMultiplier));
                map.put(entry.getKey(), list);
            }
        }

        return map;
    }

    public void fromData(MapType data)
    {
        this.bones.clear();

        for (String bone : data.keys())
        {
            BaseType value = data.get(bone);

            if (value.isList() && value.asList().size() >= 5)
            {
                ListType list = value.asList();
                float emission = list.size() >= 6 ? list.getFloat(5) : 1F;

                this.bones.put(bone, new PBRChannel(list.getFloat(0), list.getFloat(1), list.getFloat(2), list.getFloat(3), list.getFloat(4), emission));
            }
        }
    }
}
