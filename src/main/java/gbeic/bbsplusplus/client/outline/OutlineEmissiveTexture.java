package gbeic.bbsplusplus.client.outline;

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
 * Runtime-generated 1x1 LabPBR textures for the outline glow.
 *
 * <p>Iris discovers {@code _s}/{@code _n} companions through its file-based
 * {@code SimplePBRLoader}: it only accepts a {@link ResourceTexture} (Yarn's
 * {@code SimpleTexture}) with a real resource location and then asks the resource
 * manager for {@code foo_s.png}/{@code foo_n.png} in the same folder. The bytes are
 * served by {@link gbeic.bbsplusplus.mixin.ReloadableResourceManagerImplOutlineMixin}.
 * Using the vanilla texture manager here is deliberate: BBS's own texture manager
 * keeps raw GL ids without resource locations, so Iris cannot track them.</p>
 */
public final class OutlineEmissiveTexture
{
    private static final String NAMESPACE = "bbsplusplus";
    private static final String FOLDER = "textures/outline/";
    private static final String NAME_PREFIX = "outline_";
    private static final int SPECULAR_RGB = 0x3c3c3c;
    private static final int MAX_PNG_CACHE = 512;

    private static final Set<String> TEXTURE_PATHS = ConcurrentHashMap.newKeySet();
    private static final Map<String, byte[]> PNG_CACHE = new ConcurrentHashMap<>();
    private static final ThreadLocal<ByteArrayOutputStream> PNG_SINK =
        ThreadLocal.withInitial(() -> new ByteArrayOutputStream(64));

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

        int argb;
        int glow;

        try
        {
            argb = Integer.parseInt(numbers.substring(0, separator));
            glow = Integer.parseInt(numbers.substring(separator + 1));
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

        byte[] cached = PNG_CACHE.get(path);

        if (cached != null)
        {
            return cached;
        }

        byte[] encoded = encodePng(r, g, b, a);

        if (encoded != null)
        {
            if (PNG_CACHE.size() >= MAX_PNG_CACHE)
            {
                PNG_CACHE.clear();
            }

            PNG_CACHE.put(path, encoded);
        }

        return encoded;
    }

    /**
     * Gets the exact ResourceTexture registered for this colour/glow pair. Iris only
     * recognizes that class, so a missing/mis-typed texture returns null and the
     * caller falls back to the solid rim.
     */
    public static ResourceTexture getDiffuseTexture(int argb, int glow)
    {
        Identifier location = location(argb, glow);
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

    private static Identifier location(int argb, int glow)
    {
        return identifier(NAMESPACE, FOLDER + NAME_PREFIX + argb + "_" + glow + ".png");
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

    private static byte[] encodePng(int r, int g, int b, int a)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            ByteBuffer pixels = stack.malloc(4);
            pixels.put((byte) r).put((byte) g).put((byte) b).put((byte) a).flip();
            ByteArrayOutputStream out = PNG_SINK.get();
            out.reset();
            boolean ok = STBImageWrite.stbi_write_png_to_func(PNG_WRITER, 0L, 1, 1, 4, pixels, 4);
            return ok ? out.toByteArray() : null;
        }
    }
}
