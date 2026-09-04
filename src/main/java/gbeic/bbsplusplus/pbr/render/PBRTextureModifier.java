package gbeic.bbsplusplus.pbr.render;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class PBRTextureModifier
{
    private static final Map<Long, Integer> CACHE = new HashMap<>();

    private PBRTextureModifier()
    {}

    public static int getModifiedTextureId(int originalId, boolean normal, Map<String, Integer> values)
    {
        if (values == null || values.isEmpty())
        {
            return originalId;
        }

        int sR = values.getOrDefault("s_r", 0);
        int sG = values.getOrDefault("s_g", 0);
        int sB = values.getOrDefault("s_b", 0);
        int sA = values.getOrDefault("s_a", 0);
        int n = values.getOrDefault("n", 0);
        int emission = values.getOrDefault("emission_multiplier", 100);

        if (normal ? n == 0 || n == 255 : sR == 0 && sG == 0 && sB == 0 && sA == 0 && emission == 100)
        {
            return originalId;
        }

        long cacheKey = ((long) originalId << 32) | (Objects.hash(sR, sG, sB, sA, n, emission) & 0xffffffffL);
        Integer cached = CACHE.get(cacheKey);

        if (cached != null && GL11.glIsTexture(cached))
        {
            return cached;
        }

        CACHE.remove(cacheKey);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, originalId);
        int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);

        if (width <= 0 || height <= 0)
        {
            return originalId;
        }

        int count = width * height;
        ByteBuffer source = BufferUtils.createByteBuffer(count * 4);
        ByteBuffer output = BufferUtils.createByteBuffer(count * 4);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, source);

        for (int i = 0; i < count; i++)
        {
            int offset = i * 4;
            int r = source.get(offset) & 0xff;
            int g = source.get(offset + 1) & 0xff;
            int b = source.get(offset + 2) & 0xff;
            int a = source.get(offset + 3) & 0xff;

            if (normal)
            {
                output.put((byte) clamp(r * n / 255, 0, 255));
                output.put((byte) clamp(g * n / 255, 0, 255));
                output.put((byte) b);
                output.put((byte) a);
            }
            else
            {
                output.put((byte) clamp(r * sR / 255, 0, 255));
                output.put((byte) clamp(g * sG / 255, 0, 255));
                output.put((byte) clamp(b * sB / 255, 0, 255));

                int emissive = sA > 0 ? clamp(a * sA / 254, 0, 254) : a;

                if (emission > 100)
                {
                    emissive = Math.max(1, emissive);
                    emissive = (int) Math.min(254L, (long) emissive * emission / 100L);
                }
                else if (emission == 0)
                {
                    emissive = 255;
                }
                else if (emission < 100 && emissive > 0 && emissive <= 254)
                {
                    emissive = emissive * emission / 100;
                    emissive = emissive == 0 ? 255 : emissive;
                }

                output.put((byte) emissive);
            }
        }

        output.flip();
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, output);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        CACHE.put(cacheKey, id);

        return id;
    }

    private static int clamp(int value, int min, int max)
    {
        return Math.max(min, Math.min(max, value));
    }
}
