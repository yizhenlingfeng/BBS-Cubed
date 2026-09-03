package gbeic.bbsplusplus.client.texture;

import gbeic.bbsplusplus.BBSPPPModClient;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.resources.Pixels;

import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 缓存纹理补间使用的源像素与 LabPBR 伴随贴图。
 *
 * <p>高分辨率贴图最昂贵的步骤不是最终混色，而是反复读取 PNG、建立透明遮罩并为隐藏像素补全周围颜色。
 * 本类按源 {@link Link} 保存一次性预处理结果，同时按内存字节预算淘汰长期未使用的数据。透明区补色使用
 * 多源队列扩散，每个像素最多入队一次，避免旧实现随图片尺寸增加而反复扫描整张纹理。</p>
 */
final class TextureTweenSourceCache
{
    private static final long MAX_CACHE_BYTES = 512L * 1024L * 1024L;
    private static final int DEFAULT_NORMAL = 0xff7f7fff;
    private static final int DEFAULT_SPECULAR = 0x00000000;

    private static final Map<Link, SourceTexture> CACHE = new LinkedHashMap<>(16, 0.75F, true);
    private static final Set<Link> FAILED = new HashSet<>();

    private static long cacheBytes;

    public static SourceTexture get(Link link)
    {
        if (link == null)
        {
            return null;
        }

        synchronized (CACHE)
        {
            SourceTexture cached = CACHE.get(link);

            if (cached != null || FAILED.contains(link))
            {
                return cached;
            }
        }

        SourceTexture loaded = load(link);

        synchronized (CACHE)
        {
            SourceTexture concurrent = CACHE.get(link);

            if (concurrent != null)
            {
                return concurrent;
            }

            if (loaded == null)
            {
                FAILED.add(link);
                return null;
            }

            CACHE.put(link, loaded);
            cacheBytes += loaded.byteSize();
        }

        return loaded;
    }

    /**
     * 在一组源贴图加载完成后按内存预算淘汰旧数据。
     *
     * <p>当前 A/B 源会被保护，即使单组超出预算也不会在下一帧互相挤出并重复解码。</p>
     */
    public static void trim(Set<Link> protectedLinks)
    {
        synchronized (CACHE)
        {
            Iterator<Map.Entry<Link, SourceTexture>> iterator = CACHE.entrySet().iterator();

            while (cacheBytes > MAX_CACHE_BYTES && iterator.hasNext())
            {
                Map.Entry<Link, SourceTexture> entry = iterator.next();

                if (protectedLinks.contains(entry.getKey()))
                {
                    continue;
                }

                cacheBytes -= entry.getValue().byteSize();
                iterator.remove();
            }
        }
    }

    public static void invalidateAll()
    {
        synchronized (CACHE)
        {
            CACHE.clear();
            FAILED.clear();
            cacheBytes = 0L;
        }
    }

    public static Link sidecar(Link base, String suffix)
    {
        int dot = base.path.lastIndexOf('.');
        String path = (dot < 0 ? base.path : base.path.substring(0, dot)) + suffix + ".png";

        return new Link(base.source, path);
    }

    private static SourceTexture load(Link link)
    {
        Pixels diffusePixels = null;

        try
        {
            diffusePixels = BBSModClient.getTextures().getPixels(link);

            if (diffusePixels == null)
            {
                return null;
            }

            FilledPixels diffuse = FilledPixels.from(diffusePixels);
            PackedPixels normal = null;
            PackedPixels specular = null;

            if (!Link.COLOR.equals(link.source))
            {
                normal = loadSidecar(sidecar(link, "_n"), diffuse.width(), diffuse.height());
                specular = loadSidecar(sidecar(link, "_s"), diffuse.width(), diffuse.height());
            }

            long bytes = diffuse.byteSize()
                + (normal == null ? 0L : normal.byteSize())
                + (specular == null ? 0L : specular.byteSize());

            return new SourceTexture(link, diffuse, normal, specular, bytes);
        }
        catch (Exception e)
        {
            BBSPPPModClient.LOGGER.warn("无法读取纹理补间源贴图：{}", link, e);
            return null;
        }
        finally
        {
            if (diffusePixels != null)
            {
                diffusePixels.delete();
            }
        }
    }

    private static PackedPixels loadSidecar(Link link, int width, int height)
    {
        Pixels pixels = null;

        try
        {
            pixels = BBSModClient.getTextures().getPixels(link);

            if (pixels == null)
            {
                return null;
            }

            if (pixels.width != width || pixels.height != height)
            {
                BBSPPPModClient.LOGGER.warn("忽略尺寸不匹配的 PBR 伴随贴图：{}（{}x{}，预期 {}x{}）",
                    link, pixels.width, pixels.height, width, height);
                return null;
            }

            return PackedPixels.from(pixels);
        }
        catch (Exception ignored)
        {
            /* PBR 伴随贴图是可选资源，文件不存在时直接使用 Iris 默认材质。 */
            return null;
        }
        finally
        {
            if (pixels != null)
            {
                pixels.delete();
            }
        }
    }

    public record SourceTexture(Link link, FilledPixels diffuse, PackedPixels normal, PackedPixels specular, long byteSize)
    {
        public boolean hasNormal()
        {
            return this.normal != null;
        }

        public boolean hasSpecular()
        {
            return this.specular != null;
        }

        public int normal(int index)
        {
            return this.normal == null ? DEFAULT_NORMAL : this.normal.colors()[index];
        }

        public int specular(int index)
        {
            return this.specular == null ? DEFAULT_SPECULAR : this.specular.colors()[index];
        }
    }

    public record FilledPixels(int width, int height, int[] colors, BitSet visible)
    {
        private static final int ALPHA_THRESHOLD = 128;

        public static FilledPixels from(Pixels pixels)
        {
            int count = pixels.getCount();
            int[] colors = new int[count];
            BitSet visible = new BitSet(count);
            ByteBuffer buffer = pixels.getBuffer().duplicate();

            for (int i = 0; i < count; i++)
            {
                int offset = i * pixels.bits;
                int r = buffer.get(offset) & 0xff;
                int g = buffer.get(offset + 1) & 0xff;
                int b = buffer.get(offset + 2) & 0xff;
                int a = pixels.bits >= 4 ? buffer.get(offset + 3) & 0xff : 255;

                colors[i] = 0xff000000 | r << 16 | g << 8 | b;

                if (a >= ALPHA_THRESHOLD)
                {
                    visible.set(i);
                }
            }

            fillHiddenColors(colors, visible, pixels.width, pixels.height);

            return new FilledPixels(pixels.width, pixels.height, colors, visible);
        }

        public boolean isVisible(int index)
        {
            return this.visible.get(index);
        }

        public long byteSize()
        {
            return (long) this.colors.length * Integer.BYTES + (this.colors.length + 7L) / 8L;
        }

        private static void fillHiddenColors(int[] colors, BitSet visible, int width, int height)
        {
            if (visible.isEmpty())
            {
                return;
            }

            BitSet filled = (BitSet) visible.clone();
            int[] queue = new int[colors.length];
            int head = 0;
            int tail = 0;

            for (int index = visible.nextSetBit(0); index >= 0; index = visible.nextSetBit(index + 1))
            {
                queue[tail++] = index;
            }

            while (head < tail)
            {
                int index = queue[head++];
                int x = index % width;
                int y = index / width;

                for (int oy = -1; oy <= 1; oy++)
                {
                    int yy = y + oy;

                    if (yy < 0 || yy >= height)
                    {
                        continue;
                    }

                    for (int ox = -1; ox <= 1; ox++)
                    {
                        int xx = x + ox;

                        if ((ox == 0 && oy == 0) || xx < 0 || xx >= width)
                        {
                            continue;
                        }

                        int neighbor = xx + yy * width;

                        if (filled.get(neighbor))
                        {
                            continue;
                        }

                        colors[neighbor] = colors[index];
                        filled.set(neighbor);
                        queue[tail++] = neighbor;
                    }
                }
            }
        }
    }

    public record PackedPixels(int[] colors)
    {
        public static PackedPixels from(Pixels pixels)
        {
            int count = pixels.getCount();
            int[] colors = new int[count];
            ByteBuffer buffer = pixels.getBuffer().duplicate();

            for (int i = 0; i < count; i++)
            {
                int offset = i * pixels.bits;
                int r = buffer.get(offset) & 0xff;
                int g = buffer.get(offset + 1) & 0xff;
                int b = buffer.get(offset + 2) & 0xff;
                int a = pixels.bits >= 4 ? buffer.get(offset + 3) & 0xff : 255;

                colors[i] = a << 24 | r << 16 | g << 8 | b;
            }

            return new PackedPixels(colors);
        }

        public long byteSize()
        {
            return (long) this.colors.length * Integer.BYTES;
        }
    }
}
