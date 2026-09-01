package gbeic.bbsppp.client.texture;

import mchorse.bbs_mod.forms.forms.ModelForm;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.BitSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把模型顶点位置按 UV 三角形烘焙到纹理像素坐标。
 *
 * <p>Blockbench 基岩版 geo 模型会把身体表面拆散到纹理图集，直接按 UV 距离扩散会导致部件乱序。
 * 本类遍历 BBS 已加载的方块面与三角网格，用重心坐标同时插值 UV 和模型顶点位置，最终为每个有效纹理像素
 * 生成归一化模型空间 X/Y/Z。扩散算法便可按模型真实空间关系工作，而不依赖图集排布。</p>
 */
public class ModelTextureCoordinateMap
{
    private static final long MAX_CACHE_BYTES = 192L * 1024L * 1024L;
    private static final Map<Key, ModelTextureCoordinateMap> CACHE = new LinkedHashMap<>(8, 0.75F, true);

    private static long cacheBytes;

    private final float[] x;
    private final float[] y;
    private final float[] z;
    private final BitSet mapped;

    private ModelTextureCoordinateMap(float[] x, float[] y, float[] z, BitSet mapped)
    {
        this.x = x;
        this.y = y;
        this.z = z;
        this.mapped = mapped;
    }

    public static ModelTextureCoordinateMap get(ModelForm form, int width, int height)
    {
        if (form == null || form.model.get().isEmpty() || width <= 0 || height <= 0)
        {
            return null;
        }

        Key key = new Key(form.model.get(), width, height);

        synchronized (CACHE)
        {
            ModelTextureCoordinateMap cached = CACHE.get(key);

            if (cached != null)
            {
                return cached;
            }
        }

        ModelTextureCoordinateMap generated = create(form, width, height);

        if (generated != null)
        {
            synchronized (CACHE)
            {
                CACHE.put(key, generated);
                cacheBytes += generated.byteSize();
                trimCache(key);
            }
        }

        return generated;
    }

    public static void invalidateAll()
    {
        synchronized (CACHE)
        {
            CACHE.clear();
            cacheBytes = 0L;
        }
    }

    public Point get(int index)
    {
        return index >= 0 && index < this.x.length && this.mapped.get(index) ? new Point(this.x[index], this.y[index], this.z[index]) : null;
    }

    private long byteSize()
    {
        return (long) this.x.length * Float.BYTES * 3L + (this.x.length + 7L) / 8L;
    }

    private static void trimCache(Key protectedKey)
    {
        Iterator<Map.Entry<Key, ModelTextureCoordinateMap>> iterator = CACHE.entrySet().iterator();

        while (cacheBytes > MAX_CACHE_BYTES && iterator.hasNext())
        {
            Map.Entry<Key, ModelTextureCoordinateMap> entry = iterator.next();

            if (entry.getKey().equals(protectedKey))
            {
                continue;
            }

            cacheBytes -= entry.getValue().byteSize();
            iterator.remove();
        }
    }

    private static ModelTextureCoordinateMap create(ModelForm form, int width, int height)
    {
        ModelBindingGeometry geometry = ModelBindingGeometry.create(form);

        if (geometry == null)
        {
            return null;
        }

        Builder builder = new Builder(width, height, geometry.min(), geometry.max());

        for (ModelBindingGeometry.Triangle triangle : geometry.triangles())
        {
            builder.triangle(triangle.ua(), triangle.ub(), triangle.uc(), triangle.a(), triangle.b(), triangle.c());
        }

        return builder.finish();
    }

    public record Point(float x, float y, float z)
    {}

    private record Key(String model, int width, int height)
    {}

    private static class Builder
    {
        private static final float EPSILON = 0.0001F;

        private final int width;
        private final int height;
        private final float[] x;
        private final float[] y;
        private final float[] z;
        private final int[] samples;
        private final Vector3f min;
        private final Vector3f max;

        private Builder(int width, int height, Vector3f min, Vector3f max)
        {
            this.width = width;
            this.height = height;
            this.x = new float[width * height];
            this.y = new float[width * height];
            this.z = new float[width * height];
            this.samples = new int[width * height];
            this.min = min;
            this.max = max;
        }

        private void triangle(Vector2f ua, Vector2f ub, Vector2f uc, Vector3f a, Vector3f b, Vector3f c)
        {
            float denominator = (ub.y - uc.y) * (ua.x - uc.x) + (uc.x - ub.x) * (ua.y - uc.y);

            if (Math.abs(denominator) < EPSILON)
            {
                return;
            }

            int minX = Math.max(0, (int) Math.floor(Math.min(ua.x, Math.min(ub.x, uc.x)) * this.width));
            int maxX = Math.min(this.width - 1, (int) Math.ceil(Math.max(ua.x, Math.max(ub.x, uc.x)) * this.width));
            int minY = Math.max(0, (int) Math.floor(Math.min(ua.y, Math.min(ub.y, uc.y)) * this.height));
            int maxY = Math.min(this.height - 1, (int) Math.ceil(Math.max(ua.y, Math.max(ub.y, uc.y)) * this.height));

            for (int py = minY; py <= maxY; py++)
            {
                float v = (py + 0.5F) / this.height;

                for (int px = minX; px <= maxX; px++)
                {
                    float u = (px + 0.5F) / this.width;
                    float wa = ((ub.y - uc.y) * (u - uc.x) + (uc.x - ub.x) * (v - uc.y)) / denominator;
                    float wb = ((uc.y - ua.y) * (u - uc.x) + (ua.x - uc.x) * (v - uc.y)) / denominator;
                    float wc = 1F - wa - wb;

                    if (wa < -EPSILON || wb < -EPSILON || wc < -EPSILON)
                    {
                        continue;
                    }

                    int index = px + py * this.width;

                    this.x[index] += a.x * wa + b.x * wb + c.x * wc;
                    this.y[index] += a.y * wa + b.y * wb + c.y * wc;
                    this.z[index] += a.z * wa + b.z * wb + c.z * wc;
                    this.samples[index] += 1;
                }
            }
        }

        private ModelTextureCoordinateMap finish()
        {
            BitSet mapped = new BitSet(this.samples.length);
            int count = 0;

            for (int i = 0; i < this.samples.length; i++)
            {
                if (this.samples[i] <= 0)
                {
                    continue;
                }

                this.x[i] /= this.samples[i];
                this.y[i] /= this.samples[i];
                this.z[i] /= this.samples[i];
                mapped.set(i);
                count += 1;
            }

            if (count == 0)
            {
                return null;
            }

            float rangeX = this.max.x - this.min.x;
            float rangeY = this.max.y - this.min.y;
            float rangeZ = this.max.z - this.min.z;

            for (int i = 0; i < this.samples.length; i++)
            {
                if (mapped.get(i))
                {
                    this.x[i] = Math.abs(rangeX) < EPSILON ? 0.5F : (this.x[i] - this.min.x) / rangeX;
                    this.y[i] = Math.abs(rangeY) < EPSILON ? 0.5F : (this.y[i] - this.min.y) / rangeY;
                    this.z[i] = Math.abs(rangeZ) < EPSILON ? 0.5F : (this.z[i] - this.min.z) / rangeZ;
                }
            }

            return new ModelTextureCoordinateMap(this.x, this.y, this.z, mapped);
        }
    }
}
