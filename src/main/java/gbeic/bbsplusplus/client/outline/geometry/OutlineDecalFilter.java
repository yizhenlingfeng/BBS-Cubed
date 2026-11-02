package gbeic.bbsplusplus.client.outline.geometry;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds tiny cubes that are buried inside a much larger cube and therefore should not get their
 * own outline. Eyes, brows, pupils and similar decals sit on (or just inside) the head; a rim
 * around each of them reads as a ring floating inside the face.
 *
 * <p>A cube is skipped when its largest axis is at most {@code maxDetailSize} model pixels, it has
 * positive volume, and another cube with at least eight times its volume contains it (with half a
 * model pixel of tolerance). Setting the decal size to 0 disables the filter.</p>
 *
 * <p>The old implementation re-scanned every cube for every candidate and allocated a fresh
 * {@code float[6]} for every (detail, host) pair, which is O(n&sup2;) allocations per call and was
 * run twice per frame. This version precomputes flat bounds once, pre-sorts candidate hosts by
 * volume, and caches the result per model until the cube geometry changes. The result set is
 * immutable, so callers can safely share it.</p>
 */
public final class OutlineDecalFilter
{
    private static final float MIN_VOLUME_RATIO = 8F;
    private static final float TOLERANCE = 0.5F;

    /** Models are cached by identity; clearing the whole map is cheaper than an LRU at this size. */
    private static final int MAX_MODELS = 64;

    private static final Map<Model, CacheEntry> CACHE = new IdentityHashMap<>();

    private OutlineDecalFilter()
    {
    }

    private record CacheEntry(float size, long fingerprint, Set<ModelCube> details)
    {
    }

    public static Set<ModelCube> find(Model model, float maxDetailSize)
    {
        if (model == null || maxDetailSize <= 0F)
        {
            return Collections.emptySet();
        }

        long fingerprint = fingerprint(model);
        CacheEntry entry = CACHE.get(model);

        if (entry != null && entry.size == maxDetailSize && entry.fingerprint == fingerprint)
        {
            return entry.details;
        }

        Set<ModelCube> details = compute(model, maxDetailSize);

        if (CACHE.size() >= MAX_MODELS)
        {
            CACHE.clear();
        }

        CACHE.put(model, new CacheEntry(maxDetailSize, fingerprint, details));

        return details;
    }

    /** Cheap O(n) signature of everything {@link #compute} depends on. */
    private static long fingerprint(Model model)
    {
        long hash = 1125899906842597L;

        for (ModelGroup group : model.getAllGroups())
        {
            if (group == null || group.cubes == null)
            {
                continue;
            }

            for (ModelCube cube : group.cubes)
            {
                if (cube == null || cube.origin == null || cube.size == null)
                {
                    continue;
                }

                hash = hash * 31 + Float.floatToIntBits(cube.origin.x);
                hash = hash * 31 + Float.floatToIntBits(cube.origin.y);
                hash = hash * 31 + Float.floatToIntBits(cube.origin.z);
                hash = hash * 31 + Float.floatToIntBits(cube.size.x);
                hash = hash * 31 + Float.floatToIntBits(cube.size.y);
                hash = hash * 31 + Float.floatToIntBits(cube.size.z);
                hash = hash * 31 + Float.floatToIntBits(cube.inflate);
            }
        }

        return hash;
    }

    private static Set<ModelCube> compute(Model model, float maxDetailSize)
    {
        List<ModelCube> all = new ArrayList<>();

        for (ModelGroup group : model.getAllGroups())
        {
            if (group == null || group.cubes == null)
            {
                continue;
            }

            for (ModelCube cube : group.cubes)
            {
                if (cube == null || cube.origin == null || cube.size == null)
                {
                    continue;
                }

                all.add(cube);
            }
        }

        int count = all.size();

        if (count == 0)
        {
            return Collections.emptySet();
        }

        float[] bounds = new float[count * 6];
        float[] volume = new float[count];
        float[] maxAxis = new float[count];
        float[] detailVolume = new float[count];
        boolean[] isDetail = new boolean[count];
        float minDetailVolume = Float.MAX_VALUE;
        int detailCount = 0;

        for (int i = 0; i < count; i++)
        {
            ModelCube cube = all.get(i);

            bounds[i * 6] = Math.min(cube.origin.x - cube.inflate, cube.origin.x + cube.size.x + cube.inflate);
            bounds[i * 6 + 1] = Math.min(cube.origin.y - cube.inflate, cube.origin.y + cube.size.y + cube.inflate);
            bounds[i * 6 + 2] = Math.min(cube.origin.z - cube.inflate, cube.origin.z + cube.size.z + cube.inflate);
            bounds[i * 6 + 3] = Math.max(cube.origin.x - cube.inflate, cube.origin.x + cube.size.x + cube.inflate);
            bounds[i * 6 + 4] = Math.max(cube.origin.y - cube.inflate, cube.origin.y + cube.size.y + cube.inflate);
            bounds[i * 6 + 5] = Math.max(cube.origin.z - cube.inflate, cube.origin.z + cube.size.z + cube.inflate);

            volume[i] = (bounds[i * 6 + 3] - bounds[i * 6]) * (bounds[i * 6 + 4] - bounds[i * 6 + 1])
                * (bounds[i * 6 + 5] - bounds[i * 6 + 2]);
            maxAxis[i] = Math.max(Math.abs(cube.size.x), Math.max(Math.abs(cube.size.y), Math.abs(cube.size.z)));
            detailVolume[i] = Math.abs(cube.size.x) * Math.abs(cube.size.y) * Math.abs(cube.size.z);

            if (detailVolume[i] > 0F && maxAxis[i] <= maxDetailSize)
            {
                isDetail[i] = true;
                detailCount++;
                minDetailVolume = Math.min(minDetailVolume, detailVolume[i]);
            }
        }

        if (detailCount == 0)
        {
            return Collections.emptySet();
        }

        /* A host must have at least eight times the volume of every detail, so anything below
         * the smallest detail's threshold cannot be a host at all. Sorting descending lets the
         * inner loop stop as soon as the volumes drop below the current detail's threshold. */
        float hostThreshold = minDetailVolume * MIN_VOLUME_RATIO;
        List<Integer> hosts = new ArrayList<>();

        for (int i = 0; i < count; i++)
        {
            if (volume[i] >= hostThreshold)
            {
                hosts.add(i);
            }
        }

        hosts.sort((a, b) -> Float.compare(volume[b], volume[a]));

        Set<ModelCube> details = Collections.newSetFromMap(new IdentityHashMap<>());

        for (int i = 0; i < count; i++)
        {
            if (!isDetail[i])
            {
                continue;
            }

            float threshold = detailVolume[i] * MIN_VOLUME_RATIO;

            for (int host : hosts)
            {
                if (volume[host] < threshold)
                {
                    break;
                }

                if (host == i)
                {
                    continue;
                }

                if (contains(bounds, host, bounds, i))
                {
                    details.add(all.get(i));
                    break;
                }
            }
        }

        return Collections.unmodifiableSet(details);
    }

    private static boolean contains(float[] hostBounds, int host, float[] detailBounds, int detail)
    {
        int h = host * 6;
        int d = detail * 6;

        return hostBounds[h] - TOLERANCE <= detailBounds[d]
            && hostBounds[h + 1] - TOLERANCE <= detailBounds[d + 1]
            && hostBounds[h + 2] - TOLERANCE <= detailBounds[d + 2]
            && hostBounds[h + 3] + TOLERANCE >= detailBounds[d + 3]
            && hostBounds[h + 4] + TOLERANCE >= detailBounds[d + 4]
            && hostBounds[h + 5] + TOLERANCE >= detailBounds[d + 5];
    }
}
