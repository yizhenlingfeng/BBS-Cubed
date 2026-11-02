package gbeic.bbsplusplus.client.outline;

import gbeic.bbsplusplus.client.outline.geometry.OutlineShellKey;
import mchorse.bbs_mod.cubic.data.model.Model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Cross-frame cache for 1.21.11, where the render-layer API has no reusable vertex buffer.
 *
 * <p>The vertices are stored as {@code x, y, z, r, g, b, a} floats in the caller's coordinate
 * space (the stack is baked into them). Replaying a hit into a fresh
 * {@link net.minecraft.client.render.BufferBuilder} skips the whole geometry walk: matrix tree,
 * weld sets, alpha-mask lookups, face culling and bilinear sub-quads. The form opacity is applied
 * during the replay, so fades do not invalidate the bake.</p>
 *
 * <p>Because the stack is part of the key, a static model hits across frames; a moving model
 * still builds once per frame and replays twice (colour + depth) instead of building twice.</p>
 *
 * <p>All access happens on the render thread.</p>
 */
final class OutlineShellCache121
{
    private static final int MAX_MODELS = 32;
    private static final int MAX_ENTRIES_PER_MODEL = 2;
    private static final long MAX_BYTES = 64L * 1024L * 1024L;
    private static final long BYTES_PER_VERTEX = 7L * 4L;

    private static final float[] EMPTY = new float[0];

    private static final Map<Model, ModelEntries> ENTRIES = new IdentityHashMap<>();
    private static long tick;
    private static long totalBytes;

    private OutlineShellCache121()
    {
    }

    private static final class ModelEntries
    {
        final List<Entry> entries = new ArrayList<>(MAX_ENTRIES_PER_MODEL);
    }

    static final class Entry
    {
        final Model model;

        long key;
        long geometryHash;
        long baseHash;
        long lastUse;
        int generation;
        boolean valid;
        long bytes;

        float[] vertices = EMPTY;
        int vertexCount;

        private Entry(Model model)
        {
            this.model = model;
        }
    }

    /** Growable {@code x, y, z, r, g, b, a} writer. */
    static final class VertexData
    {
        float[] data = new float[256];
        int count;

        void vertex(float x, float y, float z, float nx, float ny, float nz, float r, float g, float b, float a)
        {
            int index = this.count * 7;

            if (index + 7 > this.data.length)
            {
                this.data = Arrays.copyOf(this.data, Math.max(this.data.length * 2, index + 7));
            }

            this.data[index] = x;
            this.data[index + 1] = y;
            this.data[index + 2] = z;
            this.data[index + 3] = r;
            this.data[index + 4] = g;
            this.data[index + 5] = b;
            this.data[index + 6] = a;
            this.count++;
        }

        float[] trim()
        {
            return Arrays.copyOf(this.data, this.count * 7);
        }
    }

    static Entry getOrBuild(Model model, OutlineShellKey.Key key, Consumer<Entry> builder)
    {
        ModelEntries modelEntries = ENTRIES.get(model);

        if (modelEntries == null)
        {
            modelEntries = new ModelEntries();
            ENTRIES.put(model, modelEntries);
        }

        for (Entry cached : modelEntries.entries)
        {
            if (cached.valid && cached.key == key.value())
            {
                cached.lastUse = ++tick;

                return cached;
            }
        }

        Entry entry = null;

        for (Entry cached : modelEntries.entries)
        {
            if (!cached.valid)
            {
                entry = cached;

                break;
            }
        }

        if (entry == null)
        {
            if (modelEntries.entries.size() < MAX_ENTRIES_PER_MODEL)
            {
                entry = new Entry(model);
                modelEntries.entries.add(entry);
            }
            else
            {
                entry = leastRecentlyUsed(modelEntries.entries);
            }
        }

        long before = entry.bytes;

        entry.generation++;
        entry.key = key.value();
        entry.valid = false;
        entry.bytes = 0;
        entry.vertexCount = 0;
        entry.vertices = EMPTY;

        try
        {
            builder.accept(entry);
        }
        catch (Throwable throwable)
        {
            /* A failed bake must not break the model render; the caller falls back to the
             * immediate path below. */
            entry.valid = false;
        }

        totalBytes += entry.bytes - before;
        entry.lastUse = ++tick;

        if (!entry.valid)
        {
            return null;
        }

        trim(entry);

        return entry;
    }

    private static Entry leastRecentlyUsed(List<Entry> entries)
    {
        Entry oldest = null;

        for (Entry entry : entries)
        {
            if (oldest == null || entry.lastUse < oldest.lastUse)
            {
                oldest = entry;
            }
        }

        return oldest;
    }

    private static void trim(Entry current)
    {
        while (ENTRIES.size() > MAX_MODELS)
        {
            Entry oldest = oldestExcept(current);

            if (oldest == null)
            {
                break;
            }

            evict(oldest);
        }

        while (totalBytes > MAX_BYTES)
        {
            Entry oldest = oldestExcept(current);

            if (oldest == null)
            {
                break;
            }

            evict(oldest);
        }
    }

    private static Entry oldestExcept(Entry current)
    {
        Entry oldest = null;

        for (ModelEntries modelEntries : ENTRIES.values())
        {
            for (Entry entry : modelEntries.entries)
            {
                if (entry == current)
                {
                    continue;
                }

                if (oldest == null || entry.lastUse < oldest.lastUse)
                {
                    oldest = entry;
                }
            }
        }

        return oldest;
    }

    private static void evict(Entry entry)
    {
        ModelEntries modelEntries = ENTRIES.get(entry.model);

        if (modelEntries != null)
        {
            modelEntries.entries.remove(entry);

            if (modelEntries.entries.isEmpty())
            {
                ENTRIES.remove(entry.model);
            }
        }

        totalBytes -= entry.bytes;
        entry.bytes = 0;
        entry.vertices = EMPTY;
        entry.vertexCount = 0;
        entry.valid = false;
    }

    static long bytesOf(int vertexCount)
    {
        return vertexCount * BYTES_PER_VERTEX;
    }
}
