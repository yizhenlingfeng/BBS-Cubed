package gbeic.bbsplusplus.pbr;

import java.util.LinkedHashMap;
import java.util.Map;

public class PBRData
{
    private final Map<String, PBRChannel> materials = new LinkedHashMap<>();

    public PBRChannel get(String material)
    {
        return this.materials.computeIfAbsent(material, key -> new PBRChannel());
    }

    public void put(String material, PBRChannel channel)
    {
        this.materials.put(material, channel);
    }

    public Map<String, PBRChannel> getAll()
    {
        return this.materials;
    }

    public PBRData copy()
    {
        PBRData copy = new PBRData();

        for (Map.Entry<String, PBRChannel> entry : this.materials.entrySet())
        {
            copy.put(entry.getKey(), entry.getValue().copy());
        }

        return copy;
    }
}
