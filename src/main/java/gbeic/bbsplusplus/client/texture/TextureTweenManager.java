package gbeic.bbsplusplus.client.texture;

import gbeic.bbsplusplus.BBSPPPModClient;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureManager;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.resources.Pixels;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static gbeic.bbsplusplus.keyframes.ITextureTweenKeyframe.TEXTURE_TWEEN_DISSIPATE;
import static gbeic.bbsplusplus.keyframes.ITextureTweenKeyframe.TEXTURE_TWEEN_PIXEL_DISSOLVE;

/**
 * 运行时纹理补间生成器。
 *
 * <p>每个模型实例与补间段只持有一组稳定的 diffuse、normal 和 specular 纹理。播放进度变化时直接更新
 * 现有 OpenGL 纹理内容，不再为 256 个进度分别创建并缓存整张高分辨率图片。源 PNG、透明区补色和
 * LabPBR 伴随贴图由 {@link TextureTweenSourceCache} 复用，运行时纹理则按实际显存字节预算淘汰。</p>
 */
public class TextureTweenManager
{
    private static final String SOURCE = "bbsppp";
    private static final String PATH_PREFIX = "texture_tween/runtime/";
    private static final int STEPS = 256;
    private static final int FLASH_STEPS = Math.round(STEPS * 0.08F);
    private static final int TEXTURE_TWEEN_DISSIPATE_REVERSE = 4;
    private static final int DEFAULT_FLASH_COLOR = 0xffffffff;
    private static final int DEFAULT_NORMAL = 0xff7f7fff;
    private static final int DEFAULT_SPECULAR = 0x00000000;
    private static final long MAX_RUNTIME_BYTES = 256L * 1024L * 1024L;

    private static final Map<RuntimeKey, RuntimeEntry> CACHE = new LinkedHashMap<>(16, 0.75F, true);
    private static final AtomicLong NEXT_ID = new AtomicLong();

    private static long runtimeBytes;

    public static Link getTween(Link a, Link b, float x, int mode, float originX, float originY, float originZ, boolean flash,
                                int flashColor, int blockSize, boolean pbrGlow, int pbrGlowStrength, int dissipateIntensity)
    {
        ModelForm form = TextureTweenContext.getForm();
        Link formTexture = form == null ? null : form.texture.getOriginalValue();
        Link originalA = a;
        Link originalB = b;

        a = recoverPersistentSource(originalA, originalB, formTexture);
        b = recoverPersistentSource(originalB, originalA, formTexture);

        if (a == null || b == null)
        {
            return a;
        }

        if (a.equals(b) && mode != TEXTURE_TWEEN_DISSIPATE && mode != TEXTURE_TWEEN_DISSIPATE_REVERSE)
        {
            return a;
        }

        int step = Math.max(0, Math.min(STEPS, Math.round(x * STEPS)));

        /* 非透明终点直接复用真实资源，既省一次上传，也让 Iris 继续使用原始 PBR 伴随贴图。 */
        if (step == 0 && mode != TEXTURE_TWEEN_DISSIPATE_REVERSE)
        {
            return a;
        }

        if (step == STEPS && mode != TEXTURE_TWEEN_DISSIPATE)
        {
            return b;
        }

        int originStepX = Math.round(originX * 100F);
        int originStepY = Math.round(originY * 100F);
        int originStepZ = Math.round(originZ * 100F);
        int normalizedBlockSize = normalizeBlockSize(blockSize);
        int normalizedFlashColor = flash ? flashColor : DEFAULT_FLASH_COLOR;
        int normalizedDissipateIntensity = normalizeDissipateIntensity(dissipateIntensity);
        String modelId = form == null ? "" : form.model.get();
        RuntimeKey key = new RuntimeKey(form, a, b, mode, flash, normalizedBlockSize,
            pbrGlow, normalizePbrGlowStrength(pbrGlowStrength), normalizedDissipateIntensity, modelId);
        TextureManager manager = BBSModClient.getTextures();
        RuntimeEntry entry;

        synchronized (CACHE)
        {
            entry = CACHE.get(key);

            if (entry != null && !entry.isValid(manager))
            {
                removeEntry(key, entry, manager);
                entry = null;
            }
        }

        TextureTweenSourceCache.SourceTexture sourceA = TextureTweenSourceCache.get(a);
        TextureTweenSourceCache.SourceTexture sourceB = mode == TEXTURE_TWEEN_DISSIPATE ? sourceA : TextureTweenSourceCache.get(b);

        if (!isCompatible(sourceA, sourceB))
        {
            return a;
        }

        Set<Link> protectedSources = new HashSet<>();

        protectedSources.add(a);

        if (mode != TEXTURE_TWEEN_DISSIPATE)
        {
            protectedSources.add(b);
        }

        synchronized (CACHE)
        {
            for (RuntimeEntry cached : CACHE.values())
            {
                if (cached.isBoundToForm())
                {
                    protectedSources.add(cached.key.a);

                    if (cached.key.mode != TEXTURE_TWEEN_DISSIPATE)
                    {
                        protectedSources.add(cached.key.b);
                    }
                }
            }
        }

        TextureTweenSourceCache.trim(protectedSources);

        if (entry == null)
        {
            ModelTextureCoordinateMap coordinateMap = ModelTextureCoordinateMap.get(form, sourceA.diffuse().width(), sourceA.diffuse().height());

            entry = RuntimeEntry.create(key, sourceA, sourceB, coordinateMap, normalizedFlashColor,
                originStepX, originStepY, originStepZ);

            synchronized (CACHE)
            {
                CACHE.put(key, entry);
                runtimeBytes += entry.byteSize;
            }
        }
        else if (!entry.hasOrigin(originStepX, originStepY, originStepZ))
        {
            ModelTextureCoordinateMap coordinateMap = ModelTextureCoordinateMap.get(form, sourceA.diffuse().width(), sourceA.diffuse().height());

            entry.updateOrigin(originStepX, originStepY, originStepZ, sourceA.diffuse().width(), sourceA.diffuse().height(), coordinateMap);
        }

        entry.updateFlashColor(normalizedFlashColor);

        try
        {
            entry.update(manager, sourceA, sourceB, step);
        }
        catch (Exception e)
        {
            BBSPPPModClient.LOGGER.warn("生成纹理补间运行时纹理失败：{} -> {}", a, b, e);

            synchronized (CACHE)
            {
                removeEntry(key, entry, manager);
            }

            return a;
        }

        synchronized (CACHE)
        {
            trimRuntimeCache(key, manager);
        }

        return entry.diffuseLink;
    }

    /**
     * 判断 Link 是否指向只在当前进程内存在的补间结果。
     *
     * <p>这类 Link 不能作为关键帧值保存，也不能在重启后作为源贴图读取。</p>
     */
    public static boolean isRuntimeLink(Link link)
    {
        return link != null && SOURCE.equals(link.source) && link.path.startsWith(PATH_PREFIX);
    }

    private static Link recoverPersistentSource(Link source, Link counterpart, Link formTexture)
    {
        if (!isRuntimeLink(source))
        {
            return source;
        }

        if (!isRuntimeLink(counterpart))
        {
            return counterpart;
        }

        return isRuntimeLink(formTexture) ? null : formTexture;
    }

    /**
     * 使所有源像素、模型坐标和运行时纹理失效。
     *
     * <p>F6 或文件监视器重载后，原 Link 对应的像素内容可能已经变化，因此三层缓存必须一起清空。</p>
     */
    public static void invalidateAll()
    {
        TextureManager manager = BBSModClient.getTextures();

        synchronized (CACHE)
        {
            for (RuntimeEntry entry : CACHE.values())
            {
                entry.delete(manager);
            }

            CACHE.clear();
            runtimeBytes = 0L;
        }

        TextureTweenSourceCache.invalidateAll();
        ModelTextureCoordinateMap.invalidateAll();
    }

    private static boolean isCompatible(TextureTweenSourceCache.SourceTexture a, TextureTweenSourceCache.SourceTexture b)
    {
        return a != null && b != null
            && a.diffuse().width() == b.diffuse().width()
            && a.diffuse().height() == b.diffuse().height();
    }

    private static void trimRuntimeCache(RuntimeKey protectedKey, TextureManager manager)
    {
        Iterator<Map.Entry<RuntimeKey, RuntimeEntry>> iterator = CACHE.entrySet().iterator();

        while (runtimeBytes > MAX_RUNTIME_BYTES && iterator.hasNext())
        {
            Map.Entry<RuntimeKey, RuntimeEntry> entry = iterator.next();

            if (entry.getKey().equals(protectedKey))
            {
                continue;
            }

            if (entry.getValue().isBoundToForm())
            {
                continue;
            }

            runtimeBytes -= entry.getValue().byteSize;
            entry.getValue().delete(manager);
            iterator.remove();
        }
    }

    private static void removeEntry(RuntimeKey key, RuntimeEntry entry, TextureManager manager)
    {
        if (CACHE.remove(key, entry))
        {
            runtimeBytes -= entry.byteSize;
        }

        entry.delete(manager);
    }

    private static void generate(RuntimeEntry entry, TextureTweenSourceCache.SourceTexture sourceA,
                                 TextureTweenSourceCache.SourceTexture sourceB, int step)
    {
        int width = sourceA.diffuse().width();
        int height = sourceA.diffuse().height();
        float progress = step / (float) STEPS;
        ByteBuffer diffuseBuffer = entry.diffusePixels.getBuffer();
        ByteBuffer normalBuffer = entry.normalPixels == null ? null : entry.normalPixels.getBuffer();
        ByteBuffer specularBuffer = entry.specularPixels == null ? null : entry.specularPixels.getBuffer();

        diffuseBuffer.position(0);

        if (normalBuffer != null)
        {
            normalBuffer.position(0);
        }

        if (specularBuffer != null)
        {
            specularBuffer.position(0);
        }

        if (entry.key.blockSize == 1)
        {
            for (int i = 0; i < entry.thresholds.length; i++)
            {
                writePixel(entry, sourceA, sourceB, step, progress, i, (entry.thresholds[i] & 0xff) + 1,
                    diffuseBuffer, normalBuffer, specularBuffer);
            }
        }
        else
        {
            for (int y = 0; y < height; y++)
            {
                int rowOffset = y * width;
                int thresholdRow = y / entry.key.blockSize * entry.blocksX;

                for (int blockX = 0; blockX < entry.blocksX; blockX++)
                {
                    int threshold = (entry.thresholds[thresholdRow + blockX] & 0xff) + 1;
                    int startX = blockX * entry.key.blockSize;
                    int endX = Math.min(width, startX + entry.key.blockSize);

                    for (int x = startX; x < endX; x++)
                    {
                        writePixel(entry, sourceA, sourceB, step, progress, rowOffset + x, threshold,
                            diffuseBuffer, normalBuffer, specularBuffer);
                    }
                }
            }
        }

        entry.diffusePixels.rewindBuffer();

        if (entry.normalPixels != null)
        {
            entry.normalPixels.rewindBuffer();
        }

        if (entry.specularPixels != null)
        {
            entry.specularPixels.rewindBuffer();
        }
    }

    private static void writePixel(RuntimeEntry entry, TextureTweenSourceCache.SourceTexture sourceA,
                                   TextureTweenSourceCache.SourceTexture sourceB, int step, float progress, int index,
                                   int threshold, ByteBuffer diffuseBuffer, ByteBuffer normalBuffer, ByteBuffer specularBuffer)
    {
        boolean useB = step == STEPS || (step > 0 && step >= threshold);
        int argbA = sourceA.diffuse().colors()[index];
        int argbB = sourceB.diffuse().colors()[index];
        boolean visibleA = sourceA.diffuse().isVisible(index);
        boolean visibleB = sourceB.diffuse().isVisible(index);
        boolean flashing = entry.key.mode == TEXTURE_TWEEN_DISSIPATE && entry.key.flash && visibleA && !useB
            && step > 0 && step >= threshold - FLASH_STEPS;
        boolean reverseFlashing = entry.key.mode == TEXTURE_TWEEN_DISSIPATE_REVERSE && entry.key.flash && visibleB && useB
            && step < STEPS && step <= threshold + FLASH_STEPS;
        int diffuseColor;

        if (flashing || reverseFlashing)
        {
            int baseColor = entry.key.mode == TEXTURE_TWEEN_DISSIPATE ? argbA : argbB;
            float flashOpacity = channel(entry.flashColor, 24) / 255F;

            diffuseColor = blendRgb(baseColor, entry.flashColor, flashOpacity);
        }
        else if (entry.key.mode == TEXTURE_TWEEN_PIXEL_DISSOLVE || entry.key.mode == TEXTURE_TWEEN_DISSIPATE_REVERSE)
        {
            diffuseColor = useB ? argbB : argbA;
        }
        else if (entry.key.mode == TEXTURE_TWEEN_DISSIPATE)
        {
            /* 消散完成后同时清空 RGB，避免部分渲染路径只看颜色而忽略已归零的 Alpha。 */
            diffuseColor = useB ? 0x000000 : argbA;
        }
        else
        {
            diffuseColor = blendRgb(argbA, argbB, progress);
        }

        putRgba(diffuseBuffer, diffuseColor, getDiffuseAlpha(entry.key.mode, visibleA, visibleB, useB));

        if (normalBuffer != null)
        {
            putArgb(normalBuffer, blendPbr(entry.key.mode, sourceA.normal(index), sourceB.normal(index), progress, useB, true));
        }

        if (specularBuffer != null)
        {
            int specular = blendPbr(entry.key.mode, sourceA.specular(index), sourceB.specular(index), progress, useB, false);

            if (entry.key.pbrGlow && (flashing || reverseFlashing))
            {
                specular = specular & 0x00ffffff | getPbrEmissionAlpha(entry.key.pbrGlowStrength) << 24;
            }

            putArgb(specularBuffer, specular);
        }
    }

    private static int getDiffuseAlpha(int mode, boolean visibleA, boolean visibleB, boolean useB)
    {
        boolean visible = mode == TEXTURE_TWEEN_DISSIPATE ? !useB && visibleA
            : mode == TEXTURE_TWEEN_DISSIPATE_REVERSE ? useB && visibleB
            : visibleA == visibleB ? visibleA : useB ? visibleB : visibleA;

        return visible ? 255 : 0;
    }

    private static int blendPbr(int mode, int a, int b, float progress, boolean useB, boolean normal)
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

    private static int blendNormal(int a, int b, float progress)
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

    private static int blendSpecular(int a, int b, float progress, boolean useB)
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

    private static int blendRgb(int a, int b, float progress)
    {
        int r = lerpByte(channel(a, 16), channel(b, 16), progress);
        int g = lerpByte(channel(a, 8), channel(b, 8), progress);
        int blue = lerpByte(channel(a, 0), channel(b, 0), progress);

        return r << 16 | g << 8 | blue;
    }

    private static void putRgba(ByteBuffer buffer, int rgb, int alpha)
    {
        buffer.put((byte) channel(rgb, 16));
        buffer.put((byte) channel(rgb, 8));
        buffer.put((byte) channel(rgb, 0));
        buffer.put((byte) alpha);
    }

    private static void putArgb(ByteBuffer buffer, int argb)
    {
        putRgba(buffer, argb, channel(argb, 24));
    }

    private static int channel(int color, int shift)
    {
        return color >> shift & 0xff;
    }

    private static int lerpByte(int a, int b, float progress)
    {
        return clampByte(Math.round(lerp(a, b, progress)));
    }

    private static int clampByte(int value)
    {
        return Math.max(0, Math.min(255, value));
    }

    private static float lerp(float a, float b, float progress)
    {
        return a + (b - a) * progress;
    }

    private static float square(float value)
    {
        return value * value;
    }

    private static float hash01(int index, int seed)
    {
        int hash = index ^ seed;

        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        hash *= 0x846ca68b;
        hash ^= hash >>> 16;

        return (hash & 0x7fffffff) / (float) Integer.MAX_VALUE;
    }

    private static int normalizeBlockSize(int size)
    {
        return switch (size)
        {
            case 3, 5, 7, 9 -> size;
            default -> 1;
        };
    }

    private static int normalizePbrGlowStrength(int strength)
    {
        return Math.max(0, Math.min(100, strength));
    }

    private static int normalizeDissipateIntensity(int intensity)
    {
        return Math.max(0, Math.min(100, intensity));
    }

    private static int getPbrEmissionAlpha(int strength)
    {
        /* LabPBR 保留 255 表示忽略发光，因此玩家看到的 100% 必须映射到 254。 */
        return Math.max(0, Math.min(254, Math.round(normalizePbrGlowStrength(strength) / 100F * 254F)));
    }

    private static float getBlockThreshold(int blockX, int blockY, int blockSize, int width, int height,
                                            float originX, float originY, float originZ, int blockIndex, int seed,
                                            ModelTextureCoordinateMap coordinateMap, float intensity)
    {
        int startX = blockX * blockSize;
        int startY = blockY * blockSize;
        int endX = Math.min(width, startX + blockSize);
        int endY = Math.min(height, startY + blockSize);
        float modelX = 0F;
        float modelY = 0F;
        float modelZ = 0F;
        int mappedCount = 0;

        if (coordinateMap != null)
        {
            for (int y = startY; y < endY; y++)
            {
                for (int x = startX; x < endX; x++)
                {
                    ModelTextureCoordinateMap.Point point = coordinateMap.get(x + y * width);

                    if (point != null)
                    {
                        modelX += point.x();
                        modelY += point.y();
                        modelZ += point.z();
                        mappedCount += 1;
                    }
                }
            }
        }

        float px = mappedCount > 0
            ? modelX / mappedCount
            : ((startX + endX - 1) * 0.5F) / Math.max(1, width - 1);
        float py = mappedCount > 0
            ? modelY / mappedCount
            : ((startY + endY - 1) * 0.5F) / Math.max(1, height - 1);
        float pz = mappedCount > 0 ? modelZ / mappedCount : 0.5F;
        float dx = px - originX;
        float dy = py - originY;
        float dz = pz - originZ;
        float maxDx = Math.max(originX, 1F - originX);
        float maxDy = Math.max(originY, 1F - originY);
        float maxDz = Math.max(originZ, 1F - originZ);
        float maxDistance = (float) Math.sqrt(maxDx * maxDx + maxDy * maxDy + maxDz * maxDz);
        float distance = maxDistance <= 0F ? 0F : (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / maxDistance;
        float baseWeight = mappedCount > 0 ? 0.72F : 0.28F;
        float spatialWeight = baseWeight + (1F - baseWeight) * intensity;

        return hash01(blockIndex, seed) * (1F - spatialWeight) + distance * spatialWeight;
    }

    private static final class RuntimeEntry
    {
        private final RuntimeKey key;
        private final Link diffuseLink;
        private final Link normalLink;
        private final Link specularLink;
        private final byte[] thresholds;
        private final int blocksX;
        private final boolean hasNormal;
        private final boolean hasSpecular;
        private final long byteSize;
        private final Pixels diffusePixels;
        private final Pixels normalPixels;
        private final Pixels specularPixels;

        private Texture diffuseTexture;
        private Texture normalTexture;
        private Texture specularTexture;
        private int originX;
        private int originY;
        private int originZ;
        private int flashColor;
        private int step = -1;

        private RuntimeEntry(RuntimeKey key, Link diffuseLink, byte[] thresholds, int blocksX, boolean hasNormal,
                             boolean hasSpecular, long byteSize, int width, int height, int flashColor,
                             int originX, int originY, int originZ)
        {
            this.key = key;
            this.diffuseLink = diffuseLink;
            this.normalLink = hasNormal ? TextureTweenSourceCache.sidecar(diffuseLink, "_n") : null;
            this.specularLink = hasSpecular ? TextureTweenSourceCache.sidecar(diffuseLink, "_s") : null;
            this.thresholds = thresholds;
            this.blocksX = blocksX;
            this.hasNormal = hasNormal;
            this.hasSpecular = hasSpecular;
            this.byteSize = byteSize;
            this.diffusePixels = Pixels.fromSize(width, height);
            this.normalPixels = hasNormal ? Pixels.fromSize(width, height) : null;
            this.specularPixels = hasSpecular ? Pixels.fromSize(width, height) : null;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.flashColor = flashColor;
        }

        public static RuntimeEntry create(RuntimeKey key, TextureTweenSourceCache.SourceTexture sourceA,
                                          TextureTweenSourceCache.SourceTexture sourceB, ModelTextureCoordinateMap coordinateMap,
                                          int flashColor, int originX, int originY, int originZ)
        {
            int width = sourceA.diffuse().width();
            int height = sourceA.diffuse().height();
            int count = width * height;
            int blocksX = (width + key.blockSize - 1) / key.blockSize;
            int blocksY = (height + key.blockSize - 1) / key.blockSize;
            byte[] thresholds = new byte[blocksX * blocksY];

            fillThresholds(key, thresholds, blocksX, width, height, originX, originY, originZ, coordinateMap);

            boolean hasNormal = sourceA.hasNormal() || sourceB.hasNormal();
            boolean hasSpecular = sourceA.hasSpecular() || sourceB.hasSpecular()
                || key.flash && key.pbrGlow && (key.mode == TEXTURE_TWEEN_DISSIPATE || key.mode == TEXTURE_TWEEN_DISSIPATE_REVERSE);
            int textureCount = 1 + (hasNormal ? 1 : 0) + (hasSpecular ? 1 : 0);
            /* 每张结果纹理同时占用一份 CPU 上传缓冲区和一份 GPU 纹理。 */
            long byteSize = (long) count * textureCount * 8L + thresholds.length;
            Link diffuseLink = new Link(SOURCE, PATH_PREFIX + NEXT_ID.incrementAndGet() + ".png");

            return new RuntimeEntry(key, diffuseLink, thresholds, blocksX, hasNormal, hasSpecular, byteSize, width, height,
                flashColor, originX, originY, originZ);
        }

        public void updateFlashColor(int flashColor)
        {
            if (this.flashColor == flashColor)
            {
                return;
            }

            this.flashColor = flashColor;
            /* 颜色变化只需重写当前步，复用已有 diffuse/PBR 纹理和缓存资源。 */
            this.step = -1;
        }

        public boolean hasOrigin(int originX, int originY, int originZ)
        {
            return this.originX == originX && this.originY == originY && this.originZ == originZ;
        }

        public void updateOrigin(int originX, int originY, int originZ, int width, int height, ModelTextureCoordinateMap coordinateMap)
        {
            if (this.hasOrigin(originX, originY, originZ))
            {
                return;
            }

            fillThresholds(this.key, this.thresholds, this.blocksX, width, height, originX, originY, originZ, coordinateMap);
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            /* 起点可能在播放进度不变时被拖动，必须强制重新生成当前步。 */
            this.step = -1;
        }

        private static void fillThresholds(RuntimeKey key, byte[] thresholds, int blocksX, int width, int height,
                                           int originX, int originY, int originZ, ModelTextureCoordinateMap coordinateMap)
        {
            int blocksY = (height + key.blockSize - 1) / key.blockSize;
            float normalizedOriginX = originX / 100F;
            float normalizedOriginY = originY / 100F;
            float normalizedOriginZ = originZ / 100F;
            int seed = Objects.hash(key.a, key.b, key.mode);

            for (int blockY = 0; blockY < blocksY; blockY++)
            {
                for (int blockX = 0; blockX < blocksX; blockX++)
                {
                    int index = blockX + blockY * blocksX;
                    int threshold = Math.max(1, Math.min(STEPS, Math.round(getBlockThreshold(
                        blockX, blockY, key.blockSize, width, height, normalizedOriginX, normalizedOriginY, normalizedOriginZ,
                        index, seed, coordinateMap, key.dissipateIntensity / 100F
                    ) * STEPS)));

                    thresholds[index] = (byte) (threshold - 1);
                }
            }
        }

        public void update(TextureManager manager, TextureTweenSourceCache.SourceTexture sourceA,
                           TextureTweenSourceCache.SourceTexture sourceB, int step)
        {
            if (this.step == step && this.isValid(manager))
            {
                return;
            }

            generate(this, sourceA, sourceB, step);

            if (this.diffuseTexture == null)
            {
                this.diffuseTexture = createTexture(this.diffusePixels.width, this.diffusePixels.height);
                uploadSubImage(this.diffuseTexture, this.diffusePixels);

                if (this.normalPixels != null)
                {
                    this.normalTexture = createTexture(this.normalPixels.width, this.normalPixels.height);
                    uploadSubImage(this.normalTexture, this.normalPixels);
                    manager.textures.put(this.normalLink, this.normalTexture);
                }

                if (this.specularPixels != null)
                {
                    this.specularTexture = createTexture(this.specularPixels.width, this.specularPixels.height);
                    uploadSubImage(this.specularTexture, this.specularPixels);
                    manager.textures.put(this.specularLink, this.specularTexture);
                }

                manager.textures.put(this.diffuseLink, this.diffuseTexture);
            }
            else
            {
                uploadSubImage(this.diffuseTexture, this.diffusePixels);

                if (this.normalTexture != null && this.normalPixels != null)
                {
                    uploadSubImage(this.normalTexture, this.normalPixels);
                }

                if (this.specularTexture != null && this.specularPixels != null)
                {
                    uploadSubImage(this.specularTexture, this.specularPixels);
                }
            }

            this.step = step;
        }

        public boolean isValid(TextureManager manager)
        {
            return this.diffuseTexture == null || this.diffuseTexture.isValid()
                && manager.textures.get(this.diffuseLink) == this.diffuseTexture
                && (!this.hasNormal || this.normalTexture != null && this.normalTexture.isValid()
                && manager.textures.get(this.normalLink) == this.normalTexture)
                && (!this.hasSpecular || this.specularTexture != null && this.specularTexture.isValid()
                && manager.textures.get(this.specularLink) == this.specularTexture);
        }

        public boolean isBoundToForm()
        {
            return this.key.form != null && this.diffuseLink.equals(this.key.form.texture.getRuntimeValue());
        }

        public void delete(TextureManager manager)
        {
            deleteTexture(manager, this.diffuseLink, this.diffuseTexture);
            deleteTexture(manager, this.normalLink, this.normalTexture);
            deleteTexture(manager, this.specularLink, this.specularTexture);
            this.diffuseTexture = null;
            this.normalTexture = null;
            this.specularTexture = null;
            this.diffusePixels.delete();

            if (this.normalPixels != null)
            {
                this.normalPixels.delete();
            }

            if (this.specularPixels != null)
            {
                this.specularPixels.delete();
            }

            this.step = -1;
        }

        private static Texture createTexture(int width, int height)
        {
            Texture texture = new Texture();

            texture.setFilter(GL11.GL_NEAREST);
            texture.setSize(width, height);
            texture.unbind();

            return texture;
        }

        private static void uploadSubImage(Texture texture, Pixels pixels)
        {
            ByteBuffer source = pixels.getBuffer();
            long expectedBytes = (long) pixels.width * pixels.height * 4L;

            if (source == null || expectedBytes > Integer.MAX_VALUE || source.capacity() < expectedBytes)
            {
                throw new IllegalStateException("纹理上传缓冲区容量不足：" + pixels.width + "x" + pixels.height
                    + "，需要 " + expectedBytes + " 字节，实际 " + (source == null ? 0 : source.capacity()) + " 字节");
            }

            ByteBuffer upload = source.duplicate();

            upload.position(0);
            upload.limit((int) expectedBytes);
            texture.bind();
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, pixels.width);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);

            try
            {
                GL11.glTexSubImage2D(texture.target, 0, 0, 0, pixels.width, pixels.height,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, upload);
            }
            finally
            {
                /* 避免运行时纹理上传状态污染后续 Minecraft 和 Iris 的纹理操作。 */
                GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
                texture.unbind();
            }
        }

        private static void deleteTexture(TextureManager manager, Link link, Texture texture)
        {
            if (link == null || texture == null)
            {
                return;
            }

            if (manager.textures.get(link) == texture)
            {
                manager.textures.remove(link);
            }

            if (texture.isValid())
            {
                texture.delete();
            }
        }
    }

    private static final class RuntimeKey
    {
        private final ModelForm form;
        private final Link a;
        private final Link b;
        private final int mode;
        private final boolean flash;
        private final int blockSize;
        private final boolean pbrGlow;
        private final int pbrGlowStrength;
        private final int dissipateIntensity;
        private final String modelId;
        private final int hash;

        private RuntimeKey(ModelForm form, Link a, Link b, int mode, boolean flash,
                           int blockSize, boolean pbrGlow, int pbrGlowStrength, int dissipateIntensity, String modelId)
        {
            this.form = form;
            this.a = a;
            this.b = b;
            this.mode = mode;
            this.flash = flash;
            this.blockSize = blockSize;
            this.pbrGlow = pbrGlow;
            this.pbrGlowStrength = pbrGlowStrength;
            this.dissipateIntensity = dissipateIntensity;
            this.modelId = modelId;
            this.hash = Objects.hash(System.identityHashCode(form), a, b, mode, flash, blockSize,
                pbrGlow, pbrGlowStrength, dissipateIntensity, modelId);
        }

        @Override
        public boolean equals(Object object)
        {
            if (this == object)
            {
                return true;
            }

            if (!(object instanceof RuntimeKey key))
            {
                return false;
            }

            return this.form == key.form && this.mode == key.mode && this.flash == key.flash
                && this.blockSize == key.blockSize && this.a.equals(key.a) && this.b.equals(key.b)
                && this.pbrGlow == key.pbrGlow && this.pbrGlowStrength == key.pbrGlowStrength
                && this.dissipateIntensity == key.dissipateIntensity && this.modelId.equals(key.modelId);
        }

        @Override
        public int hashCode()
        {
            return this.hash;
        }
    }
}
