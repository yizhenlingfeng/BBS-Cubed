package routu.bbsuirabbit.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The film library's covers, hover frames and colour labels for one films folder, kept beside it in
 * {@code bbspp_library/}: {@code index.json} plus one JPEG per picture under {@code images/}.
 *
 * <p>For a world that is {@code <world>/bbs/bbspp_library}, so whoever hosts the world holds the
 * pictures and every player who joins sees the same ones ({@link LibraryNetwork}). One instance per
 * folder per JVM, shared by the integrated server and its own client.</p>
 *
 * <p>Listeners hear the id of every entry that changed, or {@code null} when many did at once
 * (a folder was renamed).</p>
 */
public class LibraryStore
{
    public static final String FOLDER = "bbspp_library";
    /**
     * Pictures bigger than this are refused (a 512x288 JPEG is ~40 KB). Below the 1 MiB a packet can
     * hold, which the picture has to share with its id and the rest of the header.
     */
    public static final int MAX_IMAGE = 1000000;

    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<String, LibraryStore> STORES = new HashMap<>();

    private final File root;
    private final Map<String, LibraryEntry> entries = new LinkedHashMap<>();
    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private long nextVer = 1;
    private boolean loaded;

    /** The store beside a films folder ({@code .../films} → {@code .../bbspp_library}). */
    public static LibraryStore forFilms(File filmsFolder)
    {
        return at(new File(filmsFolder.getAbsoluteFile().getParentFile(), FOLDER));
    }

    /** The store beside a films folder if one was ever written, else null (no need to create it). */
    public static LibraryStore existingForFilms(File filmsFolder)
    {
        File root = new File(filmsFolder.getAbsoluteFile().getParentFile(), FOLDER);

        synchronized (STORES)
        {
            if (!STORES.containsKey(key(root)) && !new File(root, "index.json").exists())
            {
                return null;
            }
        }

        return at(root);
    }

    public static LibraryStore at(File root)
    {
        synchronized (STORES)
        {
            return STORES.computeIfAbsent(key(root), (k) -> new LibraryStore(root));
        }
    }

    /** Drop every cached store (the world was closed); files stay. */
    public static void forgetAll()
    {
        synchronized (STORES)
        {
            STORES.clear();
        }
    }

    private static String key(File root)
    {
        try
        {
            return root.getCanonicalPath();
        }
        catch (IOException e)
        {
            return root.getAbsolutePath();
        }
    }

    private LibraryStore(File root)
    {
        this.root = root;
    }

    public File getRoot()
    {
        return this.root;
    }

    public void addListener(Consumer<String> listener)
    {
        this.listeners.add(listener);
    }

    public void removeListener(Consumer<String> listener)
    {
        this.listeners.remove(listener);
    }

    private void changed(String id)
    {
        for (Consumer<String> listener : this.listeners)
        {
            try
            {
                listener.accept(id);
            }
            catch (Throwable e)
            {
                System.err.println("[BBS UI Rabbit] Film library listener failed: " + e);
            }
        }
    }

    /* Reading */

    public synchronized Map<String, LibraryEntry> snapshot()
    {
        this.load();

        Map<String, LibraryEntry> copy = new LinkedHashMap<>();

        for (Map.Entry<String, LibraryEntry> entry : this.entries.entrySet())
        {
            copy.put(entry.getKey(), entry.getValue().copy());
        }

        return copy;
    }

    public synchronized LibraryEntry get(String id)
    {
        this.load();

        LibraryEntry entry = this.entries.get(id);

        return entry == null ? null : entry.copy();
    }

    /** The JSON of the whole index, as sent to a client. */
    public synchronized String indexJson()
    {
        this.load();

        JsonObject films = new JsonObject();

        for (Map.Entry<String, LibraryEntry> entry : this.entries.entrySet())
        {
            films.add(entry.getKey(), entry.getValue().toJson());
        }

        return GSON.toJson(films);
    }

    /** @param kind 0 for the cover, 1..8 for hover frame kind - 1 */
    public synchronized byte[] readImage(String id, int kind)
    {
        this.load();

        LibraryEntry entry = this.entries.get(id);

        if (entry == null || entry.uid.isEmpty())
        {
            return null;
        }

        File file = this.imageFile(entry, kind);

        try
        {
            return file.isFile() ? Files.readAllBytes(file.toPath()) : null;
        }
        catch (IOException e)
        {
            return null;
        }
    }

    /* Writing */

    public synchronized boolean setColor(String id, int color)
    {
        this.load();

        color = Math.max(0, Math.min(LibraryEntry.LABELS, color));

        LibraryEntry entry = this.entry(id);

        if (entry.color == color)
        {
            return false;
        }

        entry.color = color;
        this.commit(id);

        return true;
    }

    /**
     * A new cover. Manual and custom ones always win; an automatic one only replaces nothing or a
     * worse automatic one (see {@link LibraryEntry#wantsAutoCover}).
     */
    public synchronized boolean putCover(String id, int kind, float dist, int span, byte[] jpeg)
    {
        this.load();

        if (jpeg == null || jpeg.length == 0 || jpeg.length > MAX_IMAGE || kind < LibraryEntry.COVER_AUTO || kind > LibraryEntry.COVER_CUSTOM)
        {
            return false;
        }

        LibraryEntry entry = this.entry(id);
        long now = System.currentTimeMillis();

        if (kind == LibraryEntry.COVER_AUTO && !entry.wantsAutoCover(dist, span, now))
        {
            return false;
        }

        if (!this.write(this.imageFile(entry, 0), jpeg))
        {
            return false;
        }

        entry.coverKind = kind;
        entry.coverVer = this.nextVer++;
        entry.coverDist = dist;
        entry.coverTime = now;
        entry.coverSpan = span;
        this.commit(id);

        return true;
    }

    public synchronized boolean putFrame(String id, int slot, float dist, int span, byte[] jpeg)
    {
        this.load();

        if (jpeg == null || jpeg.length == 0 || jpeg.length > MAX_IMAGE || slot < 0 || slot >= LibraryEntry.SLOTS || span <= 0)
        {
            return false;
        }

        LibraryEntry entry = this.entry(id);
        long now = System.currentTimeMillis();

        if (entry.frameSpan != span)
        {
            /* The film got longer or shorter: every frame now sits at the wrong spot, keep showing
             * them but let any capture replace them */
            entry.frameSpan = span;

            for (int i = 0; i < LibraryEntry.SLOTS; i++)
            {
                entry.frameDist[i] = 1e9F;
            }
        }

        if (!entry.wantsFrame(slot, dist, span, now))
        {
            return false;
        }

        if (!this.write(this.imageFile(entry, slot + 1), jpeg))
        {
            return false;
        }

        entry.frameVer[slot] = this.nextVer++;
        entry.frameDist[slot] = dist;
        entry.frameTime[slot] = now;
        this.commit(id);

        return true;
    }

    /** Back to an automatic cover: the next capture while editing makes one. */
    public synchronized boolean resetCover(String id)
    {
        this.load();

        LibraryEntry entry = this.entries.get(id);

        if (entry == null || entry.coverKind == LibraryEntry.COVER_NONE)
        {
            return false;
        }

        this.imageFile(entry, 0).delete();
        entry.coverKind = LibraryEntry.COVER_NONE;
        entry.coverVer = 0;
        entry.coverDist = 0;
        entry.coverTime = 0;
        this.commit(id);

        return true;
    }

    /* Films moving around (hooked into BBS's film manager) */

    public synchronized void renamed(String from, String to)
    {
        this.load();

        LibraryEntry entry = this.entries.remove(from);

        if (entry == null)
        {
            return;
        }

        this.dropFiles(this.entries.remove(to));
        this.entries.put(to, entry);
        this.save();
        this.changed(from);
        this.changed(to);
    }

    public synchronized void folderRenamed(String from, String to)
    {
        this.load();

        String a = from.endsWith("/") ? from : from + "/";
        String b = to.endsWith("/") ? to : to + "/";
        Map<String, LibraryEntry> moved = new LinkedHashMap<>();

        for (Map.Entry<String, LibraryEntry> entry : new ArrayList<>(this.entries.entrySet()))
        {
            if (entry.getKey().startsWith(a))
            {
                this.entries.remove(entry.getKey());
                moved.put(b + entry.getKey().substring(a.length()), entry.getValue());
            }
        }

        if (moved.isEmpty())
        {
            return;
        }

        this.entries.putAll(moved);
        this.save();
        this.changed(null);
    }

    public synchronized void deleted(String id)
    {
        this.load();

        LibraryEntry entry = this.entries.remove(id);

        if (entry != null)
        {
            this.dropFiles(entry);
            this.save();
            this.changed(id);
        }
    }

    /* Internals */

    private LibraryEntry entry(String id)
    {
        LibraryEntry entry = this.entries.get(id);

        if (entry == null)
        {
            entry = new LibraryEntry();
            this.entries.put(id, entry);
        }

        if (entry.uid.isEmpty())
        {
            entry.uid = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        return entry;
    }

    private void commit(String id)
    {
        LibraryEntry entry = this.entries.get(id);

        if (entry != null && entry.isEmpty())
        {
            this.dropFiles(entry);
            this.entries.remove(id);
        }

        this.save();
        this.changed(id);
    }

    private File imageFile(LibraryEntry entry, int kind)
    {
        return new File(this.root, "images/" + entry.uid + (kind == 0 ? "_c" : "_f" + (kind - 1)) + ".jpg");
    }

    private void dropFiles(LibraryEntry entry)
    {
        if (entry == null || entry.uid.isEmpty())
        {
            return;
        }

        for (int kind = 0; kind <= LibraryEntry.SLOTS; kind++)
        {
            this.imageFile(entry, kind).delete();
        }
    }

    private boolean write(File file, byte[] bytes)
    {
        try
        {
            file.getParentFile().mkdirs();

            File tmp = new File(file.getPath() + ".tmp");

            Files.write(tmp.toPath(), bytes);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);

            return true;
        }
        catch (IOException e)
        {
            System.err.println("[BBS UI Rabbit] Failed to write " + file + ": " + e);

            return false;
        }
    }

    private void load()
    {
        if (this.loaded)
        {
            return;
        }

        this.loaded = true;

        File file = new File(this.root, "index.json");

        if (!file.isFile())
        {
            return;
        }

        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
        {
            JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();

            this.nextVer = object.has("next") ? object.get("next").getAsLong() : 1;

            if (object.has("films"))
            {
                for (Map.Entry<String, JsonElement> film : object.getAsJsonObject("films").entrySet())
                {
                    LibraryEntry entry = new LibraryEntry();

                    entry.fromJson(film.getValue().getAsJsonObject());
                    this.entries.put(film.getKey(), entry);
                }
            }
        }
        catch (Exception e)
        {
            System.err.println("[BBS UI Rabbit] Failed to read " + file + ": " + e);
        }
    }

    private void save()
    {
        JsonObject object = new JsonObject();
        JsonObject films = new JsonObject();

        for (Map.Entry<String, LibraryEntry> entry : this.entries.entrySet())
        {
            films.add(entry.getKey(), entry.getValue().toJson());
        }

        object.addProperty("version", 1);
        object.addProperty("next", this.nextVer);
        object.add("films", films);

        File file = new File(this.root, "index.json");

        try
        {
            this.root.mkdirs();

            File tmp = new File(this.root, "index.json.tmp");

            try (Writer writer = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8))
            {
                GSON.toJson(object, writer);
            }

            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e)
        {
            System.err.println("[BBS UI Rabbit] Failed to write " + file + ": " + e);
        }
    }
}
