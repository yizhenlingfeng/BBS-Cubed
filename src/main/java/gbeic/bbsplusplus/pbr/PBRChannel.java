package gbeic.bbsplusplus.pbr;

import java.util.HashMap;
import java.util.Map;

public class PBRChannel
{
    public float sR;
    public float sG;
    public float sB;
    public float sA;
    public float n;
    public float emissionMultiplier;

    public PBRChannel()
    {
        this(0F, 0F, 0F, 0F, 0F, 1F);
    }

    public PBRChannel(float sR, float sG, float sB, float sA, float n)
    {
        this(sR, sG, sB, sA, n, 1F);
    }

    public PBRChannel(float sR, float sG, float sB, float sA, float n, float emissionMultiplier)
    {
        this.sR = sR;
        this.sG = sG;
        this.sB = sB;
        this.sA = sA;
        this.n = n;
        this.emissionMultiplier = emissionMultiplier;
    }

    public PBRChannel copy()
    {
        return new PBRChannel(this.sR, this.sG, this.sB, this.sA, this.n, this.emissionMultiplier);
    }

    public void clamp()
    {
        this.sR = clamp(this.sR, 0F, 255F);
        this.sG = clamp(this.sG, 0F, 255F);
        this.sB = clamp(this.sB, 0F, 255F);
        this.sA = clamp(this.sA, 0F, 254F);
        this.n = clamp(this.n, 0F, 255F);
        this.emissionMultiplier = Math.max(0F, this.emissionMultiplier);
    }

    public boolean isDefault()
    {
        return this.sR == 0F && this.sG == 0F && this.sB == 0F && this.sA == 0F && this.n == 0F && this.emissionMultiplier == 1F;
    }

    public Map<String, Integer> toIntMap()
    {
        Map<String, Integer> map = new HashMap<>();

        map.put("pbr_s_r", Math.round(this.sR));
        map.put("pbr_s_g", Math.round(this.sG));
        map.put("pbr_s_b", Math.round(this.sB));
        map.put("pbr_s_a", Math.round(this.sA));
        map.put("pbr_n", Math.round(this.n));
        map.put("emission_multiplier", Math.round(this.emissionMultiplier * 100F));

        return map;
    }

    private static float clamp(float value, float min, float max)
    {
        return Math.max(min, Math.min(max, value));
    }
}
