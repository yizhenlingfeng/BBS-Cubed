package gbeic.bbsplusplus.premiere.util;

import mchorse.bbs_mod.camera.clips.misc.AudioClip;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 按 BBS layer 优先、同 layer 内不重叠装箱的音轨打包。
 * <p>
 * BBS 时间轴: layer 越大越靠上(toLayerY 从下往上排)。
 * Premiere 时间轴: 先写出的 track 在上方。
 * 因此按 layer <b>降序</b> 输出轨道,使视觉上下顺序与 BBS 一致。
 * <p>
 * 规则:
 * <ol>
 *   <li>不同 layer → 不同 PR 轨道组(layer 降序)</li>
 *   <li>同 layer 内不重叠 → 同一 PR 轨道</li>
 *   <li>同 layer 内重叠 → 拆到新轨道(再装箱)</li>
 * </ol>
 */
public final class AudioTrackPacker
{
    private AudioTrackPacker()
    {}

    public static final class PackedItem<T>
    {
        public final AudioClip clip;
        public final T payload;

        public PackedItem(AudioClip clip, T payload)
        {
            this.clip = clip;
            this.payload = payload;
        }
    }

    /**
     * @return 每个内层 List 对应一条 PR &lt;track&gt;,内含可并列的 clipitem;
     *         列表顺序为 layer 从高到低(对应 BBS 从上到下)
     */
    public static <T> List<List<PackedItem<T>>> pack(List<PackedItem<T>> items)
    {
        List<List<PackedItem<T>>> tracks = new ArrayList<>();

        if (items == null || items.isEmpty())
        {
            return tracks;
        }

        /* layer 降序: 大 layer 先输出 → PR 上方,对齐 BBS 顶部行 */
        Map<Integer, List<PackedItem<T>>> byLayer = new TreeMap<>(Comparator.reverseOrder());

        List<PackedItem<T>> sorted = new ArrayList<>(items);
        sorted.sort(Comparator
            .comparingInt((PackedItem<T> i) -> i.clip.layer.get())
            .reversed()
            .thenComparingInt(i -> i.clip.tick.get()));

        for (PackedItem<T> item : sorted)
        {
            int layer = item.clip.layer.get();
            byLayer.computeIfAbsent(layer, k -> new ArrayList<>()).add(item);
        }

        for (List<PackedItem<T>> layerItems : byLayer.values())
        {
            /* 组内按 tick 升序装箱 */
            layerItems.sort(Comparator.comparingInt(i -> i.clip.tick.get()));

            List<List<PackedItem<T>>> layerTracks = new ArrayList<>();

            for (PackedItem<T> item : layerItems)
            {
                int start = item.clip.tick.get();
                int end = start + item.clip.duration.get();
                boolean placed = false;

                for (List<PackedItem<T>> track : layerTracks)
                {
                    if (isTrackAvailable(track, start, end))
                    {
                        track.add(item);
                        placed = true;
                        break;
                    }
                }

                if (!placed)
                {
                    List<PackedItem<T>> newTrack = new ArrayList<>();
                    newTrack.add(item);
                    layerTracks.add(newTrack);
                }
            }

            tracks.addAll(layerTracks);
        }

        return tracks;
    }

    private static <T> boolean isTrackAvailable(List<PackedItem<T>> track, int start, int end)
    {
        for (PackedItem<T> existing : track)
        {
            int existingStart = existing.clip.tick.get();
            int existingEnd = existingStart + existing.clip.duration.get();

            if (start < existingEnd && end > existingStart)
            {
                return false;
            }
        }
        return true;
    }
}
