package routu.bbsuirabbit.client.launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import routu.bbsuirabbit.client.ui.home.ThumbImages;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_1934;
import net.minecraft.class_2487;
import net.minecraft.class_2507;
import net.minecraft.class_310;
import net.minecraft.class_32;
import net.minecraft.class_34;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * The saves folder as the launcher sees it: vanilla's level summaries (read asynchronously, the
 * same way the vanilla world list does), plus what a background thread finds in each save — its
 * size, whether it is superflat, its BBS films and the film library's covers.
 */
public class LauncherWorlds
{
    private static final ExecutorService IO = Executors.newFixedThreadPool(2, (r) ->
    {
        Thread thread = new Thread(r, "BBS UI Rabbit launcher worlds");

        thread.setDaemon(true);

        return thread;
    });

    public static class Film
    {
        public final String id;
        public final File cover;
        public final long modified;
        public final long size;
        /** From the film library: colour label (0 = none), length in ticks (0 = unknown), hover frames (null = none). */
        public final int color;
        public final int span;
        public final File[] frames;
        public Pic pic;
        Pic[] framePics;

        Film(String id, Library library, long modified, long size)
        {
            this.id = id;
            this.cover = library == null ? null : library.cover;
            this.color = library == null ? 0 : library.color;
            this.span = library == null ? 0 : library.span;
            this.frames = library == null ? null : library.frames;
            this.modified = modified;
            this.size = size;
        }

        public String name()
        {
            return this.id.substring(this.id.lastIndexOf('/') + 1);
        }
    }

    /** One film's entry in the film library's index. */
    static class Library
    {
        File cover;
        int color;
        int span;
        File[] frames;
    }

    /** A picture on its way to the GPU: decoded off-thread, uploaded on first draw. */
    public static class Pic
    {
        volatile class_1011 pending;
        class_1043 texture;
        public int width, height;
        boolean failed;
        boolean started;

        public int id()
        {
            if (this.texture == null && this.pending != null)
            {
                class_1011 image = this.pending;

                this.pending = null;
                this.texture = new class_1043(image);
            }

            return this.texture == null ? 0 : this.texture.method_4624();
        }

        void close()
        {
            if (this.texture != null)
            {
                this.texture.close();
                this.texture = null;
            }

            if (this.pending != null)
            {
                this.pending.close();
                this.pending = null;
            }
        }
    }

    public static class World
    {
        public final class_34 summary;
        public final String folder;
        public final String name;
        public final long lastPlayed;
        public final class_1934 mode;
        public final boolean hardcore, cheats, locked, unavailable, future, needsBackup, experimental;
        public final String version;
        public final Path iconPath;
        public volatile long size = -1;
        public volatile boolean flat;
        public volatile List<Film> films = List.of();
        public volatile boolean scanned;
        public final Pic icon = new Pic();
        /** The newest film cover, the card's picture when there is one. */
        public volatile Film coverFilm;

        World(class_34 summary)
        {
            this.summary = summary;
            this.folder = summary.method_248();
            this.name = summary.method_252();
            this.lastPlayed = summary.method_249();
            this.mode = summary.method_247();
            this.hardcore = summary.method_257();
            this.cheats = summary.method_259();
            this.locked = summary.method_27021();
            this.unavailable = summary.method_33784();
            this.future = summary.method_260();
            this.needsBackup = summary.method_33405().method_33406();
            this.experimental = summary.method_45554();
            this.version = summary.method_258().getString();
            this.iconPath = summary.method_27020();
        }

        public boolean isCreative()
        {
            return this.mode == class_1934.field_9220 && !this.hardcore;
        }

        public File dir()
        {
            return class_310.method_1551().method_1586().method_19636().resolve(this.folder).toFile();
        }
    }

    private CompletableFuture<List<class_34>> future;
    private List<World> worlds = new ArrayList<>();
    private final Map<String, World> byFolder = new ConcurrentHashMap<>();
    private boolean loading;
    private boolean empty;
    private String error;
    private int version;

    public List<World> worlds()
    {
        this.poll();

        return this.worlds;
    }

    public boolean isLoading()
    {
        this.poll();

        return this.loading;
    }

    /** No saves at all (vanilla then goes straight to world creation). */
    public boolean isEmpty()
    {
        return this.empty;
    }

    public String getError()
    {
        return this.error;
    }

    public int version()
    {
        return this.version;
    }

    public void reload()
    {
        class_32 storage = class_310.method_1551().method_1586();

        this.error = null;
        this.empty = false;

        try
        {
            class_32.class_7410 list = storage.method_235();

            if (list.method_43421())
            {
                this.empty = true;
                this.future = CompletableFuture.completedFuture(List.of());
            }
            else
            {
                this.future = storage.method_43417(list).exceptionally((t) ->
                {
                    this.error = String.valueOf(t.getMessage());

                    return List.of();
                });
            }
        }
        catch (Exception e)
        {
            this.error = e.getMessage();
            this.future = CompletableFuture.completedFuture(List.of());
        }

        this.loading = true;
    }

    private void poll()
    {
        if (!this.loading || this.future == null || !this.future.isDone())
        {
            return;
        }

        List<class_34> summaries;

        try
        {
            summaries = this.future.getNow(List.of());
        }
        catch (Exception e)
        {
            summaries = List.of();
        }

        this.loading = false;

        List<World> next = new ArrayList<>();
        Map<String, World> old = new java.util.HashMap<>(this.byFolder);

        this.byFolder.clear();

        for (class_34 summary : summaries)
        {
            World world = new World(summary);
            World previous = old.remove(world.folder);

            if (previous != null && previous.lastPlayed == world.lastPlayed)
            {
                /* Same save, untouched: keep what was already worked out and uploaded */
                world.size = previous.size;
                world.flat = previous.flat;
                world.films = previous.films;
                world.coverFilm = previous.coverFilm;
                world.scanned = previous.scanned;
                world.icon.texture = previous.icon.texture;
                world.icon.width = previous.icon.width;
                world.icon.height = previous.icon.height;
                world.icon.started = previous.icon.started;
                previous.icon.texture = null;
            }
            else if (previous != null)
            {
                previous.icon.close();
                previous.films.forEach(LauncherWorlds::closeFilm);
            }

            next.add(world);
            this.byFolder.put(world.folder, world);

            if (!world.scanned)
            {
                IO.submit(() -> scan(world));
            }
        }

        for (World gone : old.values())
        {
            gone.icon.close();
            gone.films.forEach(LauncherWorlds::closeFilm);
        }

        this.worlds = next;
        this.version += 1;
    }

    private static void closeFilm(Film film)
    {
        if (film.pic != null)
        {
            film.pic.close();
        }

        if (film.framePics != null)
        {
            for (Pic pic : film.framePics)
            {
                if (pic != null)
                {
                    pic.close();
                }
            }
        }
    }

    /* Background work */

    private static void scan(World world)
    {
        File dir = world.dir();

        try
        {
            world.flat = isFlat(new File(dir, "level.dat"));
        }
        catch (Exception ignored)
        {}

        try
        {
            world.films = films(dir);

            Film newest = null;

            for (Film film : world.films)
            {
                if (film.cover != null && (newest == null || film.modified > newest.modified))
                {
                    newest = film;
                }
            }

            world.coverFilm = newest;
        }
        catch (Exception ignored)
        {}

        world.scanned = true;

        try (Stream<Path> walk = Files.walk(dir.toPath()))
        {
            world.size = walk.filter(Files::isRegularFile).mapToLong((p) -> p.toFile().length()).sum();
        }
        catch (Exception ignored)
        {
            world.size = 0;
        }
    }

    /** The overworld's generator is "minecraft:flat" (superflat and its presets, the void included). */
    private static boolean isFlat(File levelDat) throws Exception
    {
        if (!levelDat.isFile())
        {
            return false;
        }

        class_2487 root;

        try (InputStream in = new FileInputStream(levelDat))
        {
            root = class_2507.method_10629(in);
        }

        class_2487 data = root.method_10562("Data");
        class_2487 gen = data.method_10562("WorldGenSettings").method_10562("dimensions").method_10562("minecraft:overworld").method_10562("generator");

        return gen.method_10558("type").equals("minecraft:flat");
    }

    private static List<Film> films(File dir)
    {
        File films = new File(dir, "bbs/films");

        if (!films.isDirectory())
        {
            return List.of();
        }

        Map<String, Library> library = library(new File(dir, "bbs/bbspp_library"));
        List<Film> list = new ArrayList<>();
        Path root = films.toPath();

        try (Stream<Path> walk = Files.walk(root))
        {
            walk.filter((p) -> Files.isRegularFile(p) && isFilm(root.relativize(p).toString().replace('\\', '/'))).forEach((p) ->
            {
                String rel = root.relativize(p).toString().replace('\\', '/');
                String id = rel.substring(0, rel.lastIndexOf('.'));

                File file = p.toFile();

                list.add(new Film(id, library.get(id), file.lastModified(), file.length()));
            });
        }
        catch (Exception ignored)
        {}

        list.sort((a, b) -> Long.compare(b.modified, a.modified));

        return Collections.unmodifiableList(list);
    }

    /** Films are .dat files (.json from older versions); folders starting with "_" hold BBS's autosave copies. */
    private static boolean isFilm(String rel)
    {
        if (!rel.endsWith(".dat") && !rel.endsWith(".json"))
        {
            return false;
        }

        for (String part : rel.split("/"))
        {
            if (part.startsWith("_"))
            {
                return false;
            }
        }

        return true;
    }

    /** Film id → what the film library (BBS++ 2.7.22) keeps about it: cover JPEG, colour label, length, hover frames. */
    private static Map<String, Library> library(File library)
    {
        Map<String, Library> map = new java.util.HashMap<>();
        File index = new File(library, "index.json");

        if (!index.isFile())
        {
            return map;
        }

        try (Reader reader = Files.newBufferedReader(index.toPath(), StandardCharsets.UTF_8))
        {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            if (root.has("films"))
            {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("films").entrySet())
                {
                    JsonObject film = entry.getValue().getAsJsonObject();
                    String uid = film.has("uid") ? film.get("uid").getAsString() : "";

                    if (uid.isEmpty())
                    {
                        continue;
                    }

                    Library info = new Library();
                    int kind = film.has("cover_kind") ? film.get("cover_kind").getAsInt() : 0;
                    File cover = new File(library, "images/" + uid + "_c.jpg");

                    info.cover = kind != 0 && cover.isFile() ? cover : null;
                    info.color = film.has("color") ? film.get("color").getAsInt() : 0;
                    info.span = Math.max(film.has("frame_span") ? film.get("frame_span").getAsInt() : 0, film.has("cover_span") ? film.get("cover_span").getAsInt() : 0);

                    /* frames: one [version, distance, time] per eighth of the film, version 0 = not captured */
                    if (film.has("frames"))
                    {
                        List<File> frames = new ArrayList<>();
                        JsonArray slots = film.getAsJsonArray("frames");

                        for (int i = 0; i < slots.size(); i++)
                        {
                            JsonElement slot = slots.get(i);

                            if (slot.isJsonArray() && slot.getAsJsonArray().size() > 0 && slot.getAsJsonArray().get(0).getAsLong() != 0)
                            {
                                frames.add(new File(library, "images/" + uid + "_f" + i + ".jpg"));
                            }
                        }

                        info.frames = frames.isEmpty() ? null : frames.toArray(new File[0]);
                    }

                    map.put(entry.getKey(), info);
                }
            }
        }
        catch (Exception ignored)
        {}

        return map;
    }

    /* Pictures */

    /** The world's own icon.png; loading starts on first ask. */
    public static int icon(World world)
    {
        Pic pic = world.icon;

        if (!pic.started)
        {
            pic.started = true;

            Path path = world.iconPath;

            if (path != null)
            {
                IO.submit(() ->
                {
                    try (InputStream in = Files.newInputStream(path))
                    {
                        class_1011 image = class_1011.method_4309(in);

                        pic.width = image.method_4307();
                        pic.height = image.method_4323();
                        pic.pending = image;
                    }
                    catch (Exception e)
                    {
                        pic.failed = true;
                    }
                });
            }
        }

        return pic.id();
    }

    public static int cover(Film film)
    {
        if (film == null || film.cover == null)
        {
            return 0;
        }

        if (film.pic == null)
        {
            Pic pic = film.pic = new Pic();

            pic.started = true;
            IO.submit(() ->
            {
                try
                {
                    pic.pending = toNative(ThumbImages.fromJpeg(Files.readAllBytes(film.cover.toPath())), pic);
                }
                catch (Exception e)
                {
                    pic.failed = true;
                }
            });
        }

        return film.pic.id();
    }

    /** One of a film's hover frames (in film order); 0 while it loads or when there is none. */
    public static int frame(Film film, int index)
    {
        if (film == null || film.frames == null || index < 0 || index >= film.frames.length)
        {
            return 0;
        }

        if (film.framePics == null)
        {
            film.framePics = new Pic[film.frames.length];
        }

        Pic pic = film.framePics[index];

        if (pic == null)
        {
            Pic made = pic = film.framePics[index] = new Pic();
            File file = film.frames[index];

            made.started = true;
            IO.submit(() ->
            {
                try
                {
                    made.pending = toNative(ThumbImages.fromJpeg(Files.readAllBytes(file.toPath())), made);
                }
                catch (Exception e)
                {
                    made.failed = true;
                }
            });
        }

        return pic.id();
    }

    public static Pic framePic(Film film, int index)
    {
        return film.framePics == null || index < 0 || index >= film.framePics.length ? null : film.framePics[index];
    }

    private static final Map<String, Pic> CUSTOM = new ConcurrentHashMap<>();

    /** A picture the user picked as a world's cover (16:9 crop, cover size). */
    public static Pic custom(File file)
    {
        String key = file.getAbsolutePath() + "@" + file.lastModified();
        Pic pic = CUSTOM.get(key);

        if (pic == null)
        {
            Pic made = new Pic();

            CUSTOM.put(key, made);
            made.started = true;
            IO.submit(() ->
            {
                try
                {
                    made.pending = toNative(ThumbImages.readCustom(file), made);
                }
                catch (Exception e)
                {
                    made.failed = true;
                }
            });
            pic = made;
        }

        pic.id();

        return pic;
    }

    private static class_1011 toNative(BufferedImage image, Pic pic)
    {
        int w = image.getWidth(), h = image.getHeight();
        class_1011 nat = new class_1011(w, h, false);

        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                int rgb = image.getRGB(x, y);

                nat.method_4305(x, y, 0xff000000 | (rgb & 0xff) << 16 | (rgb & 0xff00) | (rgb >> 16 & 0xff));
            }
        }

        pic.width = w;
        pic.height = h;

        return nat;
    }

    public World get(String folder)
    {
        return folder == null ? null : this.byFolder.get(folder);
    }
}
