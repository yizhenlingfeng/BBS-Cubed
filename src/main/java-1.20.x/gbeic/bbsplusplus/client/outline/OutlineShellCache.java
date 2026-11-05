package gbeic.bbsplusplus.client.outline;

import gbeic.bbsplusplus.client.outline.geometry.OutlineGeometry;
import gbeic.bbsplusplus.client.outline.geometry.OutlineShellKey;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import net.minecraft.client.gl.VertexBuffer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Cross-frame cache for the {@code POSITION_COLOR} halves of the outline shell.
 *
 * <p>The geometry is baked in the model's root space (no caller stack, no form alpha), so the
 * same buffers can be reused by every form that renders that model while the pose and the
 * outline settings stay identical. Drawing supplies the caller's stack as part of the
 * model-view matrix, and the form opacity through {@code ColorModulator}, neither of which has
 * to invalidate the bake.</p>
 *
 * <p>Up to {@link #MAX_ENTRIES_PER_MODEL} poses are kept per model. That covers the common case
 * of one character model being rendered with several textures/outline settings per frame, and an
 * animated model keeps one slot for the previous pose as well. LRU eviction happens inside the
 * model's list first, then across models, with a global byte budget on top.</p>
 *
 * <p>Only the solid colour pass and the depth pass are cached. The two additive layers on top of
 * the shell - LabPBR emission and the vanilla full-bright glow - are rebuilt from their spec
 * lists every frame: emission needs world-space normals (the vanilla entity shader consumes them
 * without a normal matrix), and the vanilla layer is cheap enough to re-emit rather than bake a
 * second buffer per strength.</p>
 *
 * <p>All access happens on the render thread.</p>
 */
final class OutlineShellCache
{
    private static final int MAX_MODELS = 32;
    private static final int MAX_ENTRIES_PER_MODEL = 2;
    private static final long MAX_BYTES = 64L * 1024L * 1024L;
    /** Position (12) + colour (4), rounded up for the driver's alignment/index storage. */
    private static final long BYTES_PER_VERTEX = 20L;

    private static final Map<Model, ModelEntries> ENTRIES = new IdentityHashMap<>();
    private static long tick;
    private static long totalBytes;

    private OutlineShellCache()
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
        long lastUse;
        int generation;
        boolean valid;
        long bytes;

        /**
         * The plain colour shell. It always holds every outlined spec, so the rim itself never
         * depends on Iris or on a LabPBR shader pack being present; the glow layers below are
         * purely additive on top of it.
         */
        VertexBuffer solidVbo;
        int solidVertices;

        /** Specs with {@code glow > 0}: submitted again through the LabPBR entity pass. */
        List<OutlineGeometry.Spec> glowSpecs = List.of();

        /** Specs with {@code vanillaGlow > 0}: submitted again as a full-bright overlay. */
        List<OutlineGeometry.Spec> vanillaSpecs = List.of();

        Map<ModelCube, float[][]> faceRects;
        Set<ModelCube> decals = Collections.emptySet();

        private Entry(Model model)
        {
            this.model = model;
        }

        void recalcBytes()
        {
            this.bytes = this.solidVertices * BYTES_PER_VERTEX;
        }
    }

    /**
     * Returns the cached entry for this key, rebuilding the least-recently-used entry of this
     * model when the key changed. The builder must set the lists/counts, set {@code valid = true}
     * and may reuse the existing {@link VertexBuffer}s. Returns null when the build failed; the
     * caller then falls back to the immediate path.
     */
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
        entry.solidVertices = 0;
        entry.glowSpecs = List.of();
        entry.vanillaSpecs = List.of();
        entry.bytes = 0;

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

        entry.recalcBytes();
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

        close(entry);
    }

    private static void close(Entry entry)
    {
        entry.valid = false;

        if (entry.solidVbo != null)
        {
            entry.solidVbo.close();
            entry.solidVbo = null;
        }

        entry.solidVertices = 0;
        entry.glowSpecs = List.of();
        entry.vanillaSpecs = List.of();
    }
}
