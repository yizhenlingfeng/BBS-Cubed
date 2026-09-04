package gbeic.bbsplusplus.pbr.render;

import java.util.Map;

public final class BonePBRContext
{
    private static final ThreadLocal<String> CURRENT_BONE = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Map<String, Integer>>> OVERRIDES = new ThreadLocal<>();

    private BonePBRContext()
    {}

    public static void setOverrides(Map<String, Map<String, Integer>> overrides)
    {
        OVERRIDES.set(overrides);
    }

    public static void clearOverrides()
    {
        OVERRIDES.remove();
    }

    public static void setCurrentBone(String bone)
    {
        CURRENT_BONE.set(bone);
    }

    public static void clearCurrentBone()
    {
        CURRENT_BONE.remove();
    }

    public static Map<String, Integer> getCurrent()
    {
        String bone = CURRENT_BONE.get();
        Map<String, Map<String, Integer>> overrides = OVERRIDES.get();

        return bone == null || overrides == null ? null : overrides.get(bone);
    }
}
