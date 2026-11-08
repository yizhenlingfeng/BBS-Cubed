package routu.bbsuirabbit.client.ui.home;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.utils.repos.FolderManagerRepository;
import mchorse.bbs_mod.utils.repos.IRepository;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_310;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The film library's own state: favourites and view preferences (config/bbs_ui_rabbit/film_library.json)
 * and a cache of {@link FilmMeta}, filled in the background.
 *
 * <p>Films in a local folder (single player, or a server without BBS) are read on a worker thread
 * and re-read when their file changes. Films of a BBS server are asked for one at a time over the
 * network, once per session.</p>
 */
public class FilmLibraryStore
{
    public static final FilmLibraryStore INSTANCE = new FilmLibraryStore();

    public static final int SORT_MODIFIED = 0;
    public static final int SORT_NAME = 1;
    public static final int SORT_CREATED = 2;
    public static final int SORT_DURATION = 3;
    public static final int SORT_RECENT = 4;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public final Set<String> favorites = new LinkedHashSet<>();
    public int sort = SORT_MODIFIED;
    public boolean descending = true;
    public boolean grid = true;

    private final Map<String, FilmMeta> meta = new ConcurrentHashMap<>();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    private final Deque<String> remoteQueue = new ArrayDeque<>();
    private ExecutorService worker;
    private boolean remoteBusy;
    private long remoteSince;
    private IRepository<?> remoteRepository;
    private volatile int version;
    private boolean loaded;

    /** Bumped whenever metadata arrives, so the screen knows to re-sort. */
    public int getVersion()
    {
        return this.version;
    }

    public FilmMeta get(String id)
    {
        return this.meta.get(id);
    }

    public boolean isFavorite(String id)
    {
        this.load();

        return this.favorites.contains(id);
    }

    public void toggleFavorite(String id)
    {
        this.load();

        if (!this.favorites.remove(id))
        {
            this.favorites.add(id);
        }

        this.save();
    }

    public void renamed(String from, String to)
    {
        this.load();

        if (this.favorites.remove(from))
        {
            this.favorites.add(to);
            this.save();
        }

        FilmMeta old = this.meta.remove(from);

        if (old != null)
        {
            FilmMeta moved = new FilmMeta(to);

            copy(old, moved);
            this.meta.put(to, moved);
        }
    }

    public void folderRenamed(String from, String to)
    {
        this.load();

        String a = from.endsWith("/") ? from : from + "/";
        String b = to.endsWith("/") ? to : to + "/";
        Set<String> next = new LinkedHashSet<>();
        boolean changed = false;

        for (String id : this.favorites)
        {
            if (id.startsWith(a))
            {
                next.add(b + id.substring(a.length()));
                changed = true;
            }
            else
            {
                next.add(id);
            }
        }

        if (changed)
        {
            this.favorites.clear();
            this.favorites.addAll(next);
            this.save();
        }
    }

    public void removed(String id)
    {
        this.load();

        if (this.favorites.remove(id))
        {
            this.save();
        }

        this.meta.remove(id);
    }

    private static void copy(FilmMeta from, FilmMeta to)
    {
        to.stamp = from.stamp;
        to.modified = from.modified;
        to.created = from.created;
        to.duration = from.duration;
        to.replays = from.replays;
        to.cameraClips = from.cameraClips;
        to.timeSpent = from.timeSpent;
        to.description = from.description;
        to.clips = from.clips;
        to.layers = from.layers;
    }

    /* Metadata */

    /** Make sure every listed film has (fresh) metadata; what is missing is read in the background. */
    public void refresh(IRepository<?> repository, Collection<String> ids)
    {
        if (repository == null)
        {
            return;
        }

        File folder = repository.getFolder();

        if (repository instanceof FolderManagerRepository<?> && folder != null)
        {
            for (String id : ids)
            {
                File file = new File(folder, id + ".dat");
                long stamp = file.lastModified();
                FilmMeta current = this.meta.get(id);

                if ((current == null || current.stamp != stamp) && this.pending.add(id))
                {
                    this.worker().submit(() -> this.readLocal(repository, id, stamp));
                }
            }
        }
        else
        {
            if (this.remoteRepository != repository)
            {
                this.remoteRepository = repository;
                this.remoteQueue.clear();
                this.remoteBusy = false;
            }

            for (String id : ids)
            {
                if (!this.meta.containsKey(id) && this.pending.add(id))
                {
                    this.remoteQueue.add(id);
                }
            }

            this.pumpRemote();
        }
    }

    /** Forget a film's metadata so the next refresh reads it again (after it was edited). */
    public void invalidate(String id)
    {
        FilmMeta current = this.meta.get(id);

        if (current != null)
        {
            current.stamp = -1;
        }

        if (this.remoteRepository != null)
        {
            this.meta.remove(id);
        }
    }

    private ExecutorService worker()
    {
        if (this.worker == null)
        {
            this.worker = Executors.newSingleThreadExecutor((runnable) ->
            {
                Thread thread = new Thread(runnable, "BBS UI Rabbit film library");

                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);

                return thread;
            });
        }

        return this.worker;
    }

    private void readLocal(IRepository<?> repository, String id, long stamp)
    {
        try
        {
            repository.load(id, (data) ->
            {
                if (data instanceof Film film)
                {
                    this.meta.put(id, FilmMeta.of(id, film, stamp));
                }
            });

            FilmMeta current = this.meta.get(id);

            if (current == null || current.stamp != stamp)
            {
                throw new IllegalStateException("unreadable");
            }
        }
        catch (Throwable e)
        {
            /* A film being written right now, or a broken one: show what is known */
            FilmMeta meta = new FilmMeta(id);

            meta.stamp = stamp;
            meta.modified = stamp;
            this.meta.put(id, meta);
        }
        finally
        {
            this.pending.remove(id);
            this.version += 1;
        }
    }

    /** Called by the screen now and then, so a stalled remote request gets retried. */
    public void tick()
    {
        this.pumpRemote();
    }

    private void pumpRemote()
    {
        /* A request the server never answered must not stall the rest */
        if (this.remoteBusy && System.currentTimeMillis() - this.remoteSince > 8000)
        {
            this.remoteBusy = false;
        }

        if (this.remoteBusy || this.remoteQueue.isEmpty() || this.remoteRepository == null)
        {
            return;
        }

        String id = this.remoteQueue.poll();
        IRepository<?> repository = this.remoteRepository;

        this.remoteBusy = true;
        this.remoteSince = System.currentTimeMillis();

        try
        {
            repository.load(id, (data) -> class_310.method_1551().execute(() ->
            {
                this.meta.put(id, data instanceof Film film ? FilmMeta.of(id, film, 0L) : new FilmMeta(id));
                this.pending.remove(id);
                this.version += 1;

                if (this.remoteRepository == repository)
                {
                    this.remoteBusy = false;
                    this.pumpRemote();
                }
            }));
        }
        catch (Throwable e)
        {
            this.pending.remove(id);
            this.remoteBusy = false;
        }
    }

    /* Preferences */

    private Path file()
    {
        return FabricLoader.getInstance().getConfigDir().resolve("bbs_ui_rabbit").resolve("film_library.json");
    }

    public void load()
    {
        if (this.loaded)
        {
            return;
        }

        this.loaded = true;

        Path path = this.file();

        if (!Files.exists(path))
        {
            return;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
        {
            JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();

            if (object.has("favorites"))
            {
                for (JsonElement element : object.getAsJsonArray("favorites"))
                {
                    this.favorites.add(element.getAsString());
                }
            }

            if (object.has("sort"))
            {
                this.sort = Math.max(0, Math.min(SORT_RECENT, object.get("sort").getAsInt()));
            }

            if (object.has("descending"))
            {
                this.descending = object.get("descending").getAsBoolean();
            }

            if (object.has("grid"))
            {
                this.grid = object.get("grid").getAsBoolean();
            }
        }
        catch (Exception e)
        {
            System.err.println("[BBS UI Rabbit] Failed to read " + path + ": " + e);
        }
    }

    public void save()
    {
        this.load();

        JsonObject object = new JsonObject();
        JsonArray favorites = new JsonArray();

        for (String id : new HashSet<>(this.favorites))
        {
            favorites.add(id);
        }

        object.add("favorites", favorites);
        object.addProperty("sort", this.sort);
        object.addProperty("descending", this.descending);
        object.addProperty("grid", this.grid);

        Path path = this.file();

        try
        {
            Files.createDirectories(path.getParent());

            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8))
            {
                GSON.toJson(object, writer);
            }
        }
        catch (Exception e)
        {
            System.err.println("[BBS UI Rabbit] Failed to write " + path + ": " + e);
        }
    }
}
