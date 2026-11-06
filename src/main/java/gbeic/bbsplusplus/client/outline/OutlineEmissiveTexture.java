package gbeic.bbsplusplus.client.outline;

import gbeic.bbsplusplus.api.OutlineGradient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.lwjgl.stb.STBImageWrite;
import org.lwjgl.stb.STBIWriteCallbackI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime-generated LabPBR textures for the outline glow.
 *
 * <p>Iris discovers {@code _s}/{@code _n} companions through its file-based
 * {@code SimplePBRLoader}: it only accepts a {@link ResourceTexture} (Yarn's
 * {@code SimpleTexture}) with a real resource location and then asks the resource
 * manager for {@code foo_s.png}/{@code foo_n.png} in the same folder. The bytes are
 * served by {@link gbeic.bbsplusplus.mixin.ReloadableResourceManagerImplOutlineMixin}.
 * Using the vanilla texture manager here is deliberate: BBS's own texture manager
 * keeps raw GL ids without resource locations, so Iris cannot track them.</p>
 *
 * <p>A solid colour is a 1x1 diffuse; a gradient is a {@value #RAMP_WIDTH}x1 diffuse
 * holding one texel per ramp step, so the shell can carry a real per-vertex ramp
 * through the emissive pass instead of collapsing it to a single colour. The two
 * kinds live in different file names: {@code outline_<argb>_<glow>.png} versus
 * {@code outline_g<hash>_<glow>.png}.</p>
 */
public final class OutlineEmissiveTexture
{
    private static final String NAMESPACE = "bbsplusplus";
    private static final String FOLDER = "textures/outline/";
    private static final String NAME_PREFIX = "outline_";
    private static final String RAMP_TAG = "g";
    private static final int SPECULAR_RGB = 0x3c3c3c;
    private static final int MAX_PNG_CACHE = 512;

    /** Number of texels in a ramp diffuse. Enough that a 64-pixel tall bone has no visible steps. */
    public static final int RAMP_WIDTH = 64;

    private static final Set<String> TEXTURE_PATHS = ConcurrentHashMap.newKeySet();
    private static final Map<String, byte[]> PNG_CACHE = new ConcurrentHashMap<>();
    /** Ramp hash (hex) -> stops, so the resource manager can rebuild a ramp texture by name. */
    private static final Map<String, OutlineGradient> RAMP_STOPS = new ConcurrentHashMap<>();
    private static final ThreadLocal<ByteArrayOutputStream> PNG_SINK =
        ThreadLocal.withInitial(() -> new ByteArrayOutputStream(4096));

    private static final STBIWriteCallbackI PNG_WRITER = (context, data, size) ->
    {
        byte[] chunk = new byte[size];
        MemoryUtil.memByteBuffer(data, size).get(chunk);
        PNG_SINK.get().write(chunk, 0, size);
    };

    private OutlineEmissiveTexture()
    {
    }

    /** Returns generated PNG bytes for an outline resource, or null for unrelated resources. */
    public static byte[] getPng(Identifier location)
    {
        if (location == null || !NAMESPACE.equals(location.getNamespace()))
        {
            return null;
        }

        String path = location.getPath();

        if (!path.startsWith(FOLDER))
        {
            return null;
        }

        String name = path.substring(FOLDER.length());

        if (!name.startsWith(NAME_PREFIX))
        {
            return null;
        }

        int type;

        if (name.endsWith("_s.png"))
        {
            type = 1;
            name = name.substring(0, name.length() - 6);
        }
        else if (name.endsWith("_n.png"))
        {
            type = 2;
            name = name.substring(0, name.length() - 6);
        }
        else if (name.endsWith(".png"))
        {
            type = 0;
            name = name.substring(0, name.length() - 4);
        }
        else
        {
            return null;
        }

        String numbers = name.substring(NAME_PREFIX.length());
        int separator = numbers.lastIndexOf('_');

        if (separator <= 0)
        {
            return null;
        }

        String value = numbers.substring(0, separator);
        int glow;

        try
        {
            glow = Integer.parseInt(numbers.substring(separator + 1));
        }
        catch (NumberFormatException e)
        {
            return null;
        }

        byte[] cached = PNG_CACHE.get(path);

        if (cached != null)
        {
            return cached;
        }

        byte[] encoded = value.startsWith(RAMP_TAG)
            ? encodeRamp(path, type, value.substring(RAMP_TAG.length()), glow)
            : encodeSolid(path, type, value, glow);

        return encoded;
    }

    private static byte[] encodeSolid(String path, int type, String value, int glow)
    {
        int argb;

        try
        {
            argb = Integer.parseInt(value);
        }
        catch (NumberFormatException e)
        {
            return null;
        }

        int r;
        int g;
        int b;
        int a;

        switch (type)
        {
            case 1 -> {
                r = (SPECULAR_RGB >> 16) & 0xFF;
                g = (SPECULAR_RGB >> 8) & 0xFF;
                b = SPECULAR_RGB & 0xFF;
                /* LabPBR reserves 255 for "no emission" (that is what a texture without alpha
                 * reads as), so strength 0 has to be written as 255, not 0. */
                a = glow <= 0 ? 255 : Math.min(glow, 254);
            }
            case 2 -> {
                r = 128;
                g = 128;
                b = 255;
                a = 255;
            }
            default -> {
                r = (argb >> 16) & 0xFF;
                g = (argb >> 8) & 0xFF;
                b = argb & 0xFF;
                a = (argb >> 24) & 0xFF;
            }
        }

        return store(path, encodePng(1, 1, new int[] { r, g, b, a }, 1));
    }

    /**
     * A ramp diffuse is one texel column per ramp position. The hash in the file name is enough to
     * rebuild it, but not to recover the stops, so the stops are resolved through
     * {@link #RAMP_STOPS}, filled by {@link #getGradientTexture} before the resource manager asks.
     */
    private static byte[] encodeRamp(String path, int type, String value, int glow)
    {
        OutlineGradient gradient = RAMP_STOPS.get(value);

        if (gradient == null)
        {
            return null;
        }

        int[] pixels = new int[RAMP_WIDTH * 4];
        float[] rgba = new float[4];
        int specularA = glow <= 0 ? 255 : Math.min(glow, 254);

        for (int x = 0; x < RAMP_WIDTH; x++)
        {
            gradient.sample(x / (float) (RAMP_WIDTH - 1), rgba);

            int r = toByte(rgba[0]);
            int g = toByte(rgba[1]);
            int b = toByte(rgba[2]);

            switch (type)
            {
                case 1 -> {
                    /* Emission strength is per texel, so one side of the ramp can glow and the other
                     * stay dark; the stop alpha scales it. */
                    int a = toByte(rgba[3]);

                    pixels[x * 4] = (SPECULAR_RGB >> 16) & 0xFF;
                    pixels[x * 4 + 1] = (SPECULAR_RGB >> 8) & 0xFF;
                    pixels[x * 4 + 2] = SPECULAR_RGB & 0xFF;
                    pixels[x * 4 + 3] = Math.round(specularA * (a / 255F));
                }
                case 2 -> {
                    pixels[x * 4] = 128;
                    pixels[x * 4 + 1] = 128;
                    pixels[x * 4 + 2] = 255;
                    pixels[x * 4 + 3] = 255;
                }
                default -> {
                    pixels[x * 4] = r;
                    pixels[x * 4 + 1] = g;
                    pixels[x * 4 + 2] = b;
                    pixels[x * 4 + 3] = toByte(rgba[3]);
                }
            }
        }

        return store(path, encodePng(RAMP_WIDTH, 1, pixels, 4));
    }

    private static byte[] store(String path, byte[] encoded)
    {
        if (encoded == null)
        {
            return null;
        }

        if (PNG_CACHE.size() >= MAX_PNG_CACHE)
        {
            PNG_CACHE.clear();
        }

        PNG_CACHE.put(path, encoded);

        return encoded;
    }

    private static int toByte(float value)
    {
        return Math.max(0, Math.min(255, Math.round(value * 255F)));
    }

    /**
     * Gets the exact ResourceTexture registered for this colour/glow pair. Iris only
     * recognizes that class, so a missing/mis-typed texture returns null and the
     * caller falls back to the solid rim.
     */
    public static ResourceTexture getDiffuseTexture(int argb, int glow)
    {
        return getResource(location(FOLDER + NAME_PREFIX + argb + "_" + glow + ".png"));
    }

    /** Ramp equivalent of {@link #getDiffuseTexture}: a 64x1 diffuse sampled along {@code u}. */
    public static ResourceTexture getGradientTexture(OutlineGradient gradient, int glow)
    {
        if (gradient == null)
        {
            return null;
        }

        long hash = gradient.hash();
        String tag = Long.toHexString(hash);

        /* The resource manager is asked for the PNG by name only, so the stops have to be reachable
         * from the hash when the bytes are served. */
        RAMP_STOPS.put(tag, gradient);

        return getResource(location(FOLDER + NAME_PREFIX + RAMP_TAG + tag + "_" + glow + ".png"));
    }

    private static ResourceTexture getResource(Identifier location)
    {
        TextureManager textures = MinecraftClient.getInstance().getTextureManager();

        try
        {
            AbstractTexture texture = textures.getTexture(location);
            ResourceTexture resource = texture instanceof ResourceTexture rt ? rt : null;

            if (resource == null)
            {
                textures.destroyTexture(location);
                texture = textures.getTexture(location);
                resource = texture instanceof ResourceTexture rt ? rt : null;
            }

            if (resource != null)
            {
                TEXTURE_PATHS.add(location.getPath());
            }

            return resource;
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    /** Releases all generated textures. Iris drops the PBR holder from its GL-delete hook. */
    public static void invalidate()
    {
        PNG_CACHE.clear();
        RAMP_STOPS.clear();

        if (TEXTURE_PATHS.isEmpty())
        {
            return;
        }

        TextureManager textures = MinecraftClient.getInstance().getTextureManager();

        for (String path : TEXTURE_PATHS)
        {
            try
            {
                textures.destroyTexture(identifier(NAMESPACE, path));
            }
            catch (Throwable ignored)
            {
                // The texture may already have been released by a resource reload.
            }
        }

        TEXTURE_PATHS.clear();
    }

    /** 1.20.1 uses the public constructor; 1.21.11 made it private and exposes {@code of}. */
    private static Identifier identifier(String namespace, String path)
    {
        try
        {
            Method of = Identifier.class.getMethod("of", String.class, String.class);
            return (Identifier) of.invoke(null, namespace, path);
        }
        catch (NoSuchMethodException ignored)
        {
            try
            {
                Constructor<Identifier> constructor = Identifier.class.getDeclaredConstructor(String.class, String.class);
                constructor.setAccessible(true);
                return constructor.newInstance(namespace, path);
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException("Cannot create Identifier", e);
            }
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException("Cannot create Identifier", e);
        }
    }

    private static Identifier location(String path)
    {
        return identifier(NAMESPACE, path);
    }

    private static byte[] encodePng(int width, int height, int[] rgba, int channels)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            ByteBuffer pixels = stack.malloc(width * height * 4);

            for (int i = 0; i < width * height; i++)
            {
                pixels.put((byte) rgba[i * 4]);
                pixels.put((byte) rgba[i * 4 + 1]);
                pixels.put((byte) rgba[i * 4 + 2]);
                pixels.put((byte) rgba[i * 4 + 3]);
            }

            pixels.flip();

            ByteArrayOutputStream out = PNG_SINK.get();
            out.reset();
            boolean ok = STBImageWrite.stbi_write_png_to_func(PNG_WRITER, 0L, width, height, channels,
                pixels, width * 4);

            return ok ? out.toByteArray() : null;
        }
    }
}
