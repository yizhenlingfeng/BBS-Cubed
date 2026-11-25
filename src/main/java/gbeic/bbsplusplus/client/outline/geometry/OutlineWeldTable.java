package gbeic.bbsplusplus.client.outline.geometry;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.cubic.model.config.WeldValue;

/**
 * Bone-to-welded-faces lookup, signature cache and the {@code front}/{@code back} face mirroring
 * that the outline pipeline relies on, extracted from {@link OutlineGeometry}.
 */
final class OutlineWeldTable
{
    /** Cached {@code bone -> mirrored weld faces} tables, keyed by config identity. */
    private static final Map<ModelConfig, CachedWelds> WELD_CACHE = new IdentityHashMap<>();
    private static final int MAX_WELD_CACHE = 64;
    private static final Map<String, Set<String>> NO_WELDS = Collections.emptyMap();

    private record CachedWelds(long signature, Map<String, Set<String>> byBone)
    {
    }

    /**
     * Builds (and caches) the whole bone-to-welded-faces table for one config.
     *
     * <p>The old implementation walked every weld once per spec and allocated a fresh
     * {@code HashSet} each time. This version walks the weld list once per config change and
     * reuses immutable sets.</p>
     */
    static Map<String, Set<String>> weldTable(ModelConfig config)
    {
        if (config == null || config.welds == null)
        {
            return NO_WELDS;
        }

        long signature = weldSignature(config);
        CachedWelds cached = WELD_CACHE.get(config);

        if (cached != null && cached.signature == signature)
        {
            return cached.byBone;
        }

        Map<String, Set<String>> byBone = new HashMap<>();

        for (WeldValue weld : config.welds.getAllTyped())
        {
            if (weld == null)
            {
                continue;
            }

            addWeldedFace(byBone, weld.sourceBone.get(), weld.sourceFace.get());
            addWeldedFace(byBone, weld.targetBone.get(), weld.targetFace.get());
        }

        for (Map.Entry<String, Set<String>> entry : byBone.entrySet())
        {
            entry.setValue(Collections.unmodifiableSet(entry.getValue()));
        }

        Map<String, Set<String>> result = Collections.unmodifiableMap(byBone);
        if (WELD_CACHE.size() >= MAX_WELD_CACHE)
        {
            WELD_CACHE.clear();
        }

        WELD_CACHE.put(config, new CachedWelds(signature, result));

        return result;
    }

    private static void addWeldedFace(Map<String, Set<String>> byBone, String bone, String face)
    {
        if (bone == null || bone.isEmpty() || face == null || face.isEmpty())
        {
            return;
        }

        byBone.computeIfAbsent(bone, key -> new HashSet<>()).add(mirrorFace(face));
    }

    private static long weldSignature(ModelConfig config)
    {
        long hash = 1125899906842597L;
        int count = 0;

        for (WeldValue weld : config.welds.getAllTyped())
        {
            count++;

            if (weld == null)
            {
                hash = hash * 31 + 1;
                continue;
            }

            hash = hash * 31 + stringHash(weld.sourceBone.get());
            hash = hash * 31 + stringHash(weld.sourceFace.get());
            hash = hash * 31 + stringHash(weld.targetBone.get());
            hash = hash * 31 + stringHash(weld.targetFace.get());
        }

        return hash * 31 + count;
    }

    private static int stringHash(String value)
    {
        return value == null ? 0 : value.hashCode();
    }

    private static String mirrorFace(String face)
    {
        switch (face.toLowerCase())
        {
            case "front": return "back";
            case "back": return "front";
            case "right": return "left";
            case "left": return "right";
            case "top": return "bottom";
            case "bottom": return "top";
            default: return face.toLowerCase();
        }
    }

    private OutlineWeldTable()
    {
    }
}
