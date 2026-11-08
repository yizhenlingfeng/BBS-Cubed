package routu.bbsuirabbit.client.ui.home;

import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.utils.clips.Clip;

import java.time.Instant;
import java.util.List;

/** What the library shows about a film without opening it, read once per file version. */
public class FilmMeta
{
    public final String id;

    /** File's last-modified time when this was read (0 for remote films). */
    public long stamp;
    public long modified;
    public long created;
    public int duration;
    public int replays;
    public int cameraClips;
    public long timeSpent;
    public String description = "";

    /** Camera clips as [tick, duration, layer, rgb] quadruples, for the mini timelines. */
    public int[] clips = new int[0];
    public int layers = 1;

    public FilmMeta(String id)
    {
        this.id = id;
    }

    public static FilmMeta of(String id, Film film, long stamp)
    {
        FilmMeta meta = new FilmMeta(id);

        meta.stamp = stamp;
        meta.modified = stamp;
        meta.duration = film.calculateDuration();
        meta.replays = film.replays.getList().size();
        meta.timeSpent = film.timeSpentActive.get();
        meta.description = film.description.get() == null ? "" : film.description.get().trim();

        String created = film.createdAt.get();

        if (created != null && !created.isEmpty())
        {
            try
            {
                meta.created = Instant.parse(created).toEpochMilli();
            }
            catch (Exception e)
            {}
        }

        List<Clip> clips = film.camera.get();
        int[] data = new int[clips.size() * 4];
        int count = 0;
        int top = 0;

        for (Clip clip : clips)
        {
            if (!clip.enabled.get())
            {
                continue;
            }

            int color = 0x8899aa;

            try
            {
                ClipFactoryData factory = film.camera.getFactory().getData(clip);

                if (factory != null)
                {
                    color = factory.color;
                }
            }
            catch (Exception e)
            {}

            data[count * 4] = clip.tick.get();
            data[count * 4 + 1] = clip.duration.get();
            data[count * 4 + 2] = clip.layer.get();
            data[count * 4 + 3] = color;
            top = Math.max(top, clip.layer.get());
            count += 1;
        }

        /* Layers are sparse (a new film's idle clip sits on layer 8): pack the used ones */
        java.util.TreeSet<Integer> used = new java.util.TreeSet<>();

        for (int i = 0; i < count; i++)
        {
            used.add(data[i * 4 + 2]);
        }

        java.util.List<Integer> order = new java.util.ArrayList<>(used);

        for (int i = 0; i < count; i++)
        {
            data[i * 4 + 2] = order.size() - 1 - order.indexOf(data[i * 4 + 2]);
        }

        meta.cameraClips = count;
        meta.clips = java.util.Arrays.copyOf(data, count * 4);
        meta.layers = Math.max(1, order.size());

        if (meta.modified == 0 && meta.created > 0)
        {
            meta.modified = meta.created;
        }

        return meta;
    }
}
