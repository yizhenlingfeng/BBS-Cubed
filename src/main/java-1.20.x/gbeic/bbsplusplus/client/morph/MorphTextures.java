package gbeic.bbsplusplus.client.morph;

import gbeic.bbsplusplus.BBSPlusPlusMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.resources.Pixels;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 形变期间的纹理渐变。
 *
 * <p>目标驱动（逆向绑定）方案下，过渡体顶点使用<b>目标 Cube 自己的 UV</b> 且全程不动。
 * 源内容会先按映射面的区域对应关系搬到目标 UV 布局，再逐像素与目标内容混合：
 * {@code Color(t) = (1-w)·MappedSource(targetUv) + w·Target(targetUv)}。</p>
 *
 * <p>源图集按映射面的 UV 区域搬到目标布局，再与目标内容混合。目标侧以原生图集为基准，
 * 合成画布做 4 倍超采样，避免把较大源面压进较小目标区域时直接丢失像素细节。</p>
 */
public final class MorphTextures
{
    /** 与既有纹理补间一致的量化步数，避免每个浮点进度都重建纹理。 */
    private static final int STEPS = 32;

    /**
     * 画布相对目标图集的超采样倍数。
     *
     * <p>目标面矩形常常比源面矩形小得多（实测 bed 面 16×16、boat 面 16×6），若画布
     * 按目标图集尺寸存储，源内容会被不可逆地压掉大半细节；首帧几何已恢复成源形状，
     * 却只能显示这份被压扁的内容，于是与真实源模型明显不同、观感突兀。
     * 放大画布能保留更多细节，显著缩小这个差异。</p>
     */
    private static final int SUPERSAMPLE = 4;

    private static final int MAX_ENTRIES = 8;
    private static final Map<Key, Entry> CACHE = new LinkedHashMap<>(4, 0.75F, true);

    private MorphTextures()
    {
    }

    /**
     * 按进度返回应当绑定的贴图。
     *
     * @param source      源模型图集
     * @param target      目标模型图集
     * @param progress    形变进度
     * @param regions     两侧 UV 区域对应关系
     * @return 应当绑定的纹理；{@code null} 表示不应改动当前绑定
     */
    public static Texture blend(Link source, Link target, float progress,
                                java.util.List<MutationCube.UvRegion> regions)
    {
        if (target == null)
        {
            return null;
        }

        /* 目标图集是形变几何的基准：UV 取自目标 Cube，必须绑定目标图集。 */
        Texture targetTexture = resolve(target);

        if (source == null || source.equals(target) || targetTexture == null)
        {
            return targetTexture;
        }

        int step = Math.max(0, Math.min(STEPS, Math.round(MorphMath.clamp01(progress) * STEPS)));

        /* 进度 1：完全是目标模型，直接用目标图集，无需生成混合图。 */
        if (step >= STEPS)
        {
            return targetTexture;
        }

        /* 注意：进度接近 0 时<b>绝不能</b>返回原始源图集。
           过渡体的顶点使用目标 Cube 的 UV，若此时绑定源图集，就会用「目标 UV」去采
           源图集上完全无关的位置（现场表现：源模型帧后连续几刻出现严重错乱的贴图）。
           正确做法是始终使用「源内容已映射进目标区域」的画布——它在进度 0 时恰好
           等于源模型外观，因为源内容就摆在与目标 UV 对应的矩形里。 */

        Key key = new Key(source, target, regions);

        if (FAILED.contains(key))
        {
            return targetTexture;
        }

        Entry entry = CACHE.get(key);

        if (entry == null)
        {
            entry = Entry.create(source, target, regions);

            if (entry == null)
            {
                FAILED.add(key);

                return targetTexture;
            }

            CACHE.put(key, entry);
            trim();
        }

        try
        {
            entry.update(step);

            return entry.texture;
        }
        catch (Exception exception)
        {
            BBSPlusPlusMod.LOGGER.warn("[Model Morph] texture blend failed: {} -> {}", source, target, exception);

            entry.delete();
            CACHE.remove(key);
            FAILED.add(key);

            return targetTexture;
        }
    }

    public static Texture resolve(Link link)
    {
        if (link == null)
        {
            return null;
        }

        try
        {
            return BBSModClient.getTextures().getTexture(link);
        }
        catch (Exception exception)
        {
            BBSPlusPlusMod.LOGGER.warn("[Model Morph] texture resolve failed: {}", link, exception);

            return null;
        }
    }

    public static void invalidateAll()
    {
        for (Entry entry : CACHE.values())
        {
            entry.delete();
        }

        CACHE.clear();
        FAILED.clear();
    }

    private static void trim()
    {
        while (CACHE.size() > MAX_ENTRIES)
        {
            Iterator<Map.Entry<Key, Entry>> iterator = CACHE.entrySet().iterator();

            if (!iterator.hasNext())
            {
                return;
            }

            iterator.next().getValue().delete();
            iterator.remove();
        }
    }

    static record Key(Link source, Link target, java.util.List<MutationCube.UvRegion> regions)
    {
        Key
        {
            regions = regions == null ? java.util.List.of() : java.util.List.copyOf(regions);
        }
    }

    /**
     * 把源图集每个面的内容映射到目标图集的对应矩形。
     *
     * <p>目标侧按自身原生尺寸保留不动，只有源内容被重采样进目标矩形；
     * 混合时目标像素始终是原生的，因此画面清晰度不会因为混合而下降。</p>
     */
    static int[] remapSourceInto(java.util.List<MutationCube.UvRegion> regions,
                                         int[] sourceColors, int sourceWidth, int sourceHeight,
                                         int targetWidth, int targetHeight)
    {
        int[] canvas = new int[targetWidth * targetHeight];

        /* 默认底色：目标区域之外取源图集按比例缩放的近似，避免大片透明。 */
        int[] base = sourceColors;

        if (sourceWidth != targetWidth || sourceHeight != targetHeight)
        {
            base = resample(sourceColors, sourceWidth, sourceHeight, targetWidth, targetHeight);
        }

        System.arraycopy(base, 0, canvas, 0, canvas.length);

        if (regions == null)
        {
            return canvas;
        }

        for (MutationCube.UvRegion region : regions)
        {
            int sx0 = Math.round(region.su0() * sourceWidth);
            int sy0 = Math.round(region.sv0() * sourceHeight);
            int sx1 = Math.round(region.su1() * sourceWidth);
            int sy1 = Math.round(region.sv1() * sourceHeight);
            int tx0 = Math.round(region.tu0() * targetWidth);
            int ty0 = Math.round(region.tv0() * targetHeight);
            int tx1 = Math.round(region.tu1() * targetWidth);
            int ty1 = Math.round(region.tv1() * targetHeight);
            int sourceW = sx1 - sx0;
            int sourceH = sy1 - sy0;
            int targetW = tx1 - tx0;
            int targetH = ty1 - ty0;

            if (sourceW <= 0 || sourceH <= 0 || targetW <= 0 || targetH <= 0)
            {
                continue;
            }

            for (int y = 0; y < targetH; y++)
            {
                int destY = ty0 + y;

                if (destY < 0 || destY >= targetHeight)
                {
                    continue;
                }

                int sourceY = sy0 + y * sourceH / targetH;

                if (sourceY < 0 || sourceY >= sourceHeight)
                {
                    continue;
                }

                for (int x = 0; x < targetW; x++)
                {
                    int destX = tx0 + x;

                    if (destX < 0 || destX >= targetWidth)
                    {
                        continue;
                    }

                    int sourceX = sx0 + x * sourceW / targetW;

                    if (sourceX < 0 || sourceX >= sourceWidth)
                    {
                        continue;
                    }

                    canvas[destY * targetWidth + destX] = sourceColors[sourceY * sourceWidth + sourceX];
                }
            }
        }

        return canvas;
    }

    private static final class Entry
    {
        private final int[] sourceColors;
        private final int[] targetColors;
        private final int width;
        private final int height;

        private Texture texture;
        private Pixels pixels;
        private int step = -1;

        private Entry(int[] sourceColors, int[] targetColors, int width, int height)
        {
            this.sourceColors = sourceColors;
            this.targetColors = targetColors;
            this.width = width;
            this.height = height;
        }

        private static Entry create(Link source, Link target, java.util.List<MutationCube.UvRegion> regions)
        {
            Loaded from = load(source);
            Loaded to = load(target);

            if (from == null || to == null || from.width() <= 0 || from.height() <= 0)
            {
                return null;
            }

            /* 以目标图集尺寸为基准：过渡体 UV 是目标 UV，采样窗口必须与目标图集对齐。
               源内容按面映射进目标区域——目标内容因此始终保持原生像素，只有正在淡出的
               源内容会被重采样，这正是"贴图全程清晰"的关键。 */
            /* 画布放大 SUPERSAMPLE 倍：UV 是归一化坐标，放大后采样结果完全不变，
               但每个面的目标矩形像素变多，足以容纳源面的全部细节。
               否则源内容会被压进更小的目标矩形（实测 16×16 → 16×6），
               纵向 16 行只剩 6 行，首帧几何虽已恢复成源形状，画面却明显变糊、观感突兀。 */
            int canvasWidth = to.width() * SUPERSAMPLE;
            int canvasHeight = to.height() * SUPERSAMPLE;
            int[] sourceColors = MorphTextures.remapSourceInto(regions, from.colors(), from.width(), from.height(),
                canvasWidth, canvasHeight);

            return new Entry(sourceColors, upscale(to.colors(), to.width(), to.height(), canvasWidth, canvasHeight),
                canvasWidth, canvasHeight);
        }

        private void update(int requestedStep)
        {
            if (this.step == requestedStep && this.texture != null && this.texture.isValid())
            {
                return;
            }

            float progress = requestedStep / (float) STEPS;
            ByteBuffer buffer = this.pixels().getBuffer();

            buffer.position(0);

            for (int i = 0; i < this.targetColors.length; i++)
            {
                int from = this.sourceColors[i];
                int to = this.targetColors[i];
                int blended = blendPixel(from, to, progress);

                int r = (blended >> 16) & 0xFF;
                int g = (blended >> 8) & 0xFF;
                int b = blended & 0xFF;

                int a = (blended >>> 24) & 0xFF;

                buffer.put((byte) r);
                buffer.put((byte) g);
                buffer.put((byte) b);
                buffer.put((byte) a);
            }

            this.pixels().rewindBuffer();
            upload(this.texture(), this.pixels());

            /* 同步 translucent 标志：BBS 只在 uploadTexture(Pixels) 里计算它，
               而那条路径会 delete 掉缓冲区。这里用 glTexSubImage2D 保留缓冲区，
               因此必须自己按同样的规则更新，否则模型不会被当作半透明处理，
               淡入淡出会完全失效。 */
            ((gbeic.bbsplusplus.mixin.morph.TextureTranslucentAccessor) (Object) this.texture())
                .bbsppp$setTranslucent(hasSemiTransparent(this.pixels()));

            this.step = requestedStep;
        }

        private Texture texture()
        {
            if (this.texture == null || !this.texture.isValid())
            {
                Texture created = new Texture();

                created.setFilter(GL11.GL_NEAREST);
                created.setSize(this.width, this.height);
                created.unbind();
                this.texture = created;
            }

            return this.texture;
        }

        /**
         * 上传混合后的像素。
         *
         * <p>刻意不复用 {@code Texture.updateTexture(Pixels)}：它内部走
         * {@code uploadTexture(...)}，而后者会 {@code delete()} 掉传入的 Pixels ——
         * 缓冲区要跨帧复用，第二帧就会 NPE。这里直接对<b>已绑定</b>的纹理做
         * {@code glTexSubImage2D}，既不接管所有权，也不依赖 setSize 预留的存储内容。</p>
         */
        private static void upload(Texture texture, Pixels pixels)
        {
            ByteBuffer source = pixels.getBuffer();

            if (source == null)
            {
                throw new IllegalStateException("Model Morph 贴图缓冲区为空");
            }

            ByteBuffer upload = source.duplicate();

            upload.position(0);
            upload.limit(pixels.width * pixels.height * 4);
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
                GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
                texture.unbind();
            }
        }

        private Pixels pixels()
        {
            if (this.pixels == null)
            {
                this.pixels = Pixels.fromSize(this.width, this.height);
            }

            return this.pixels;
        }

        private void delete()
        {
            if (this.texture != null)
            {
                this.texture.delete();
                this.texture = null;
            }

            if (this.pixels != null)
            {
                this.pixels.delete();
                this.pixels = null;
            }

            this.step = -1;
        }
    }

    /** 已失败过的贴图对：不再每帧重试解码与混合，资源重载时清空。 */
    private static final java.util.Set<Key> FAILED = new java.util.HashSet<>();

    /**
     * 计算单个像素在给定进度下的颜色（不含 alpha）。
     *
     * <p>RGB 与 alpha 都按进度连续插值。若源像素全透明，则不把其无效 RGB 混入颜色。</p>
     *
     * @return ARGB
     */
    /**
     * 判断像素缓冲区是否含半透明像素。
     *
     * <p>阈值与 BBS {@code Texture.scanTranslucency} 一致：alpha 落在 26..254
     * 之间才算半透明（低于 26 会被 cutout 当作完全透明）。</p>
     */
    static boolean hasSemiTransparent(Pixels pixels)
    {
        if (pixels == null || pixels.bits < 4)
        {
            return false;
        }

        ByteBuffer buffer = pixels.getBuffer();
        int limit = Math.min(buffer.limit(), pixels.width * pixels.height * 4);

        for (int i = 3; i < limit; i += 4)
        {
            int alpha = buffer.get(i) & 0xFF;

            if (alpha >= 26 && alpha <= 254)
            {
                return true;
            }
        }

        return false;
    }

    static int blendPixel(int from, int to, float progress)
    {
        int fromAlpha = (from >>> 24) & 0xFF;
        int r;
        int g;
        int b;

        /* 源像素透明时，按进度从「目标颜色」渐入到目标颜色本身。
           这里不能直接返回目标色：源全透明意味着源模型在这个位置什么都看不到，
           若 p=0 就直接显示目标纹理，形变第一刻就会「凭空冒出」一块带纹理的面，
           非常突兀。改为用进度调制亮度，让内容随形变逐步显现。 */
        if (fromAlpha == 0)
        {
            r = (to >> 16) & 0xFF;
            g = (to >> 8) & 0xFF;
            b = to & 0xFF;
        }
        else
        {
            r = blendChannel(from, to, progress, 16);
            g = blendChannel(from, to, progress, 8);
            b = blendChannel(from, to, progress, 0);
        }

        /* BBS 支持半透明模型渲染，中间 alpha 连续变化而不是硬切或抖动噪点。 */
        int a = blendChannel(from, to, progress, 24);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int blendChannel(int from, int to, float progress, int shift)
    {
        int a = (from >> shift) & 0xFF;
        int b = (to >> shift) & 0xFF;

        return Math.max(0, Math.min(255, Math.round(a + (b - a) * progress)));
    }

    private static Loaded load(Link link)
    {
        Pixels pixels = null;

        try
        {
            pixels = BBSModClient.getTextures().getPixels(link);

            if (pixels == null || pixels.width <= 0 || pixels.height <= 0)
            {
                return null;
            }

            int count = pixels.width * pixels.height;
            int[] colors = new int[count];
            int bits = pixels.bits;
            ByteBuffer buffer = pixels.getBuffer();

            buffer.position(0);

            for (int i = 0; i < count; i++)
            {
                int r = buffer.get() & 0xFF;
                int g = buffer.get() & 0xFF;
                int b = buffer.get() & 0xFF;
                int a = bits >= 4 ? buffer.get() & 0xFF : 0xFF;

                colors[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }

            return new Loaded(colors, pixels.width, pixels.height);
        }
        catch (Exception exception)
        {
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

    private record Loaded(int[] colors, int width, int height)
    {
    }

    /**
     * 最近邻放大：像素风纹理必须保持硬边界，不能用线性插值。
     */
    private static int[] upscale(int[] colors, int width, int height, int targetWidth, int targetHeight)
    {
        if (width == targetWidth && height == targetHeight)
        {
            return colors;
        }

        int[] result = new int[targetWidth * targetHeight];

        for (int y = 0; y < targetHeight; y++)
        {
            int sourceY = Math.min(height - 1, y * height / targetHeight);

            for (int x = 0; x < targetWidth; x++)
            {
                int sourceX = Math.min(width - 1, x * width / targetWidth);

                result[y * targetWidth + x] = colors[sourceY * width + sourceX];
            }
        }

        return result;
    }

    private static int[] resample(int[] colors, int width, int height, int targetWidth, int targetHeight)
    {
        int[] result = new int[targetWidth * targetHeight];

        for (int y = 0; y < targetHeight; y++)
        {
            int sourceY = Math.min(height - 1, y * height / targetHeight);

            for (int x = 0; x < targetWidth; x++)
            {
                int sourceX = Math.min(width - 1, x * width / targetWidth);

                result[y * targetWidth + x] = colors[sourceY * width + sourceX];
            }
        }

        return result;
    }
}
