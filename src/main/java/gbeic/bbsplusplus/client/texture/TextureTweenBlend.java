package gbeic.bbsplusplus.client.texture;

import java.nio.ByteBuffer;
import static gbeic.bbsplusplus.keyframes.ITextureTweenKeyframe.TEXTURE_TWEEN_DISSIPATE;
import static gbeic.bbsplusplus.keyframes.ITextureTweenKeyframe.TEXTURE_TWEEN_PIXEL_DISSOLVE;

/**
 * Pixel blending math for texture tweening: channel extraction, byte lerp/clamp and the
 * diffuse/normal/specular blend modes, extracted from {@link TextureTweenManager}.
 */
final class TextureTweenBlend
{
    private static final int TEXTURE_TWEEN_DISSIPATE_REVERSE = 4;
    private static final int DEFAULT_NORMAL = 0xff7f7fff;
    private static final int DEFAULT_SPECULAR = 0x00000000;

    public static int getDiffuseAlpha(int mode, boolean visibleA, boolean visibleB, boolean useB)
    {
        boolean visible = mode == TEXTURE_TWEEN_DISSIPATE ? !useB && visibleA
            : mode == TEXTURE_TWEEN_DISSIPATE_REVERSE ? useB && visibleB
            : visibleA == visibleB ? visibleA : useB ? visibleB : visibleA;

        return visible ? 255 : 0;
    }

    public static int blendPbr(int mode, int a, int b, float progress, boolean useB, boolean normal)
    {
        if (mode == TEXTURE_TWEEN_DISSIPATE)
        {
            /* Diffuse 消失后，必须同步清空原始 PBR 发光数据，避免已消散像素继续发光。 */
            return useB ? normal ? DEFAULT_NORMAL : DEFAULT_SPECULAR : a;
        }

        if (mode == TEXTURE_TWEEN_DISSIPATE_REVERSE)
        {
            return useB ? b : normal ? DEFAULT_NORMAL : DEFAULT_SPECULAR;
        }

        if (mode == TEXTURE_TWEEN_PIXEL_DISSOLVE)
        {
            return useB ? b : a;
        }

        return normal ? blendNormal(a, b, progress) : blendSpecular(a, b, progress, useB);
    }

    public static int blendNormal(int a, int b, float progress)
    {
        float ax = channel(a, 16) / 127.5F - 1F;
        float ay = channel(a, 8) / 127.5F - 1F;
        float az = (float) Math.sqrt(Math.max(0F, 1F - ax * ax - ay * ay));
        float bx = channel(b, 16) / 127.5F - 1F;
        float by = channel(b, 8) / 127.5F - 1F;
        float bz = (float) Math.sqrt(Math.max(0F, 1F - bx * bx - by * by));
        float nx = lerp(ax, bx, progress);
        float ny = lerp(ay, by, progress);
        float nz = lerp(az, bz, progress);
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);

        if (length > 0F)
        {
            nx /= length;
            ny /= length;
        }

        int r = clampByte(Math.round((nx * 0.5F + 0.5F) * 255F));
        int g = clampByte(Math.round((ny * 0.5F + 0.5F) * 255F));
        int ao = lerpByte(channel(a, 0), channel(b, 0), progress);
        int height = lerpByte(channel(a, 24), channel(b, 24), progress);

        return height << 24 | r << 16 | g << 8 | ao;
    }

    public static int blendSpecular(int a, int b, float progress, boolean useB)
    {
        float roughnessA = square(1F - channel(a, 16) / 255F);
        float roughnessB = square(1F - channel(b, 16) / 255F);
        int smoothness = clampByte(Math.round((1F - (float) Math.sqrt(lerp(roughnessA, roughnessB, progress))) * 255F));
        int f0A = channel(a, 8);
        int f0B = channel(b, 8);
        int material = f0A <= 229 && f0B <= 229 ? lerpByte(f0A, f0B, progress) : useB ? f0B : f0A;
        int blueA = channel(a, 0);
        int blueB = channel(b, 0);
        boolean sameBlueDomain = blueA <= 64 && blueB <= 64 || blueA >= 65 && blueB >= 65;
        int porosityOrSss = sameBlueDomain ? lerpByte(blueA, blueB, progress) : useB ? blueB : blueA;
        int emissionA = channel(a, 24);
        int emissionB = channel(b, 24);
        int emission = emissionA < 255 && emissionB < 255 ? lerpByte(emissionA, emissionB, progress) : useB ? emissionB : emissionA;

        return emission << 24 | smoothness << 16 | material << 8 | porosityOrSss;
    }

    public static int blendRgb(int a, int b, float progress)
    {
        int r = lerpByte(channel(a, 16), channel(b, 16), progress);
        int g = lerpByte(channel(a, 8), channel(b, 8), progress);
        int blue = lerpByte(channel(a, 0), channel(b, 0), progress);

        return r << 16 | g << 8 | blue;
    }

    public static void putRgba(ByteBuffer buffer, int rgb, int alpha)
    {
        buffer.put((byte) channel(rgb, 16));
        buffer.put((byte) channel(rgb, 8));
        buffer.put((byte) channel(rgb, 0));
        buffer.put((byte) alpha);
    }

    public static void putArgb(ByteBuffer buffer, int argb)
    {
        putRgba(buffer, argb, channel(argb, 24));
    }

    public static int channel(int color, int shift)
    {
        return color >> shift & 0xff;
    }

    public static int lerpByte(int a, int b, float progress)
    {
        return clampByte(Math.round(lerp(a, b, progress)));
    }

    public static int clampByte(int value)
    {
        return Math.max(0, Math.min(255, value));
    }

    public static float lerp(float a, float b, float progress)
    {
        return a + (b - a) * progress;
    }

    public static float square(float value)
    {
        return value * value;
    }

    public static float hash01(int index, int seed)
    {
        int hash = index ^ seed;

        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        hash *= 0x846ca68b;
        hash ^= hash >>> 16;

        return (hash & 0x7fffffff) / (float) Integer.MAX_VALUE;
    }

    public static int normalizeBlockSize(int size)
    {
        return switch (size)
        {
            case 3, 5, 7, 9 -> size;
            default -> 1;
        };
    }

    public static int normalizePbrGlowStrength(int strength)
    {
        return Math.max(0, Math.min(100, strength));
    }

    public static int normalizeDissipateIntensity(int intensity)
    {
        return Math.max(0, Math.min(100, intensity));
    }

    public static int getPbrEmissionAlpha(int strength)
    {
        /* LabPBR 保留 255 表示忽略发光，因此玩家看到的 100% 必须映射到 254。 */
        return Math.max(0, Math.min(254, Math.round(normalizePbrGlowStrength(strength) / 100F * 254F)));
    }
    private TextureTweenBlend()
    {
    }
}
