package gbeic.bbsplusplus.pbr.render;

import java.util.Map;

public final class PBRTextureContext
{
    private static final ThreadLocal<Map<String, Map<String, Integer>>> CONTEXT = new ThreadLocal<>();

    private PBRTextureContext()
    {}

    public static void set(Map<String, Map<String, Integer>> values)
    {
        CONTEXT.set(values);
    }

    public static Map<String, Map<String, Integer>> get()
    {
        return CONTEXT.get();
    }

    public static void clear()
    {
        CONTEXT.remove();
    }
}
