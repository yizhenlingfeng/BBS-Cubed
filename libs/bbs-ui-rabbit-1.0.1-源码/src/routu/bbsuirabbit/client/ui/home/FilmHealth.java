package routu.bbsuirabbit.client.ui.home;

import mchorse.bbs_mod.data.storage.DataStorage;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * The film check of the film library ("体检"): reads every film of a films folder and lists what this
 * game can't show of it. Read only, nothing is changed.
 *
 * <ul>
 *     <li>Files that can't be read at all.</li>
 *     <li>Forms whose addon isn't installed (they show as a question mark, see UnknownFormRenderer).</li>
 *     <li>Models, particle effects, textures and audio that aren't there (shown as nothing).</li>
 *     <li>Keyframe tracks of addons that aren't installed (kept in the file, but not played).</li>
 * </ul>
 *
 * <p>BBS's own backup versions ({@code _<film>/} folders) are only checked for being readable and
 * counted: they don't affect anything until someone restores one.</p>
 *
 * <p>Runs off the render thread on a snapshot of what the game knows ({@link Known}).</p>
 */
public final class FilmHealth
{
    public static final String UNREADABLE = "unreadable";
    public static final String UNKNOWN_FORM = "unknown_form";
    public static final String MISSING_MODEL = "missing_model";
    public static final String MISSING_PARTICLE = "missing_particle";
    public static final String MISSING_TEXTURE = "missing_texture";
    public static final String MISSING_AUDIO = "missing_audio";
    public static final String UNKNOWN_KEYFRAMES = "unknown_keyframes";

    private FilmHealth()
    {}

    /** What this game has, taken on the render thread before the scan starts. */
    public static final class Known
    {
        public Predicate<MapType> form = (map) -> true;
        public Collection<String> models;
        public Collection<String> particles;
        public Collection<String> keyframeTypes;
        public Predicate<String> asset = (link) -> true;
    }

    public static final class Issue
    {
        public final String kind;
        public final String detail;
        public int count;

        Issue(String kind, String detail)
        {
            this.kind = kind;
            this.detail = detail;
        }
    }

    public static final class FilmReport
    {
        public final String id;
        public final Map<String, Issue> issues = new LinkedHashMap<>();

        FilmReport(String id)
        {
            this.id = id;
        }

        void add(String kind, String detail)
        {
            this.issues.computeIfAbsent(kind + "\u0000" + detail, (k) -> new Issue(kind, detail)).count++;
        }
    }

    public static final class Report
    {
        public final List<FilmReport> problems = new ArrayList<>();
        public int films;
        public int backups;
        public final List<String> brokenBackups = new ArrayList<>();
        public boolean cancelled;
    }

    public static Report scan(File folder, Known known, BooleanSupplier cancelled) throws IOException
    {
        Report report = new Report();
        Path root = folder.toPath();
        List<Path> films = new ArrayList<>();
        List<Path> backups = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(root))
        {
            walk.filter((p) -> p.getFileName().toString().endsWith(".dat") && Files.isRegularFile(p)).forEach((p) ->
            {
                (isBackup(root.relativize(p)) ? backups : films).add(p);
            });
        }

        films.sort(null);

        for (Path path : films)
        {
            if (cancelled.getAsBoolean())
            {
                report.cancelled = true;

                return report;
            }

            String id = idOf(root, path);
            FilmReport film = new FilmReport(id);

            try
            {
                walk(read(path), film, known);
            }
            catch (Throwable e)
            {
                film.add(UNREADABLE, e.getClass().getSimpleName());
            }

            report.films++;

            if (!film.issues.isEmpty())
            {
                report.problems.add(film);
            }
        }

        for (Path path : backups)
        {
            if (cancelled.getAsBoolean())
            {
                report.cancelled = true;

                return report;
            }

            report.backups++;

            if (!readable(path))
            {
                report.brokenBackups.add(root.relativize(path).toString().replace('\\', '/'));
            }
        }

        /* Worst first; a film that only lost its audio comes last */
        report.problems.sort(java.util.Comparator.comparingInt(FilmHealth::severity));

        return report;
    }

    /** Kinds in the order they matter: a damaged file, then what can't be shown at all, audio last. */
    public static final List<String> KINDS = List.of(UNREADABLE, UNKNOWN_FORM, MISSING_MODEL, MISSING_PARTICLE, MISSING_TEXTURE, UNKNOWN_KEYFRAMES, MISSING_AUDIO);

    static int severity(FilmReport film)
    {
        int worst = KINDS.size();

        for (Issue issue : film.issues.values())
        {
            worst = Math.min(worst, KINDS.indexOf(issue.kind));
        }

        return worst;
    }

    /** Folders starting with "_" hold BBS's backup versions (and are hidden from the film list). */
    static boolean isBackup(Path relative)
    {
        for (Path part : relative)
        {
            if (part.toString().startsWith("_"))
            {
                return true;
            }
        }

        return false;
    }

    static String idOf(Path root, Path film)
    {
        String rel = root.relativize(film).toString().replace('\\', '/');

        return rel.substring(0, rel.length() - ".dat".length());
    }

    /** A film file's data (gzipped BBS data), for other readers of all films (RecordingCleanup). */
    public static BaseType readFilm(Path path) throws IOException
    {
        return read(path);
    }

    private static BaseType read(Path path) throws IOException
    {
        if (Files.size(path) == 0)
        {
            throw new IOException("empty file");
        }

        try (InputStream in = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(path))))
        {
            BaseType data = DataStorage.readFromStream(in);

            if (data == null || !data.isMap())
            {
                throw new IOException("not a film");
            }

            return data;
        }
    }

    /** The whole file decompresses (a backup only needs to be restorable). */
    private static boolean readable(Path path)
    {
        try
        {
            if (Files.size(path) == 0)
            {
                return false;
            }

            try (InputStream in = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(path))))
            {
                byte[] buffer = new byte[65536];

                while (in.read(buffer) >= 0)
                {}
            }

            return true;
        }
        catch (IOException e)
        {
            return false;
        }
    }

    static void walk(BaseType data, FilmReport film, Known known)
    {
        if (data instanceof MapType map)
        {
            checkMap(map, film, known);

            for (String key : map.keys())
            {
                walk(map.get(key), film, known);
            }
        }
        else if (data instanceof ListType list)
        {
            for (int i = 0; i < list.size(); i++)
            {
                walk(list.get(i), film, known);
            }
        }
    }

    private static void checkMap(MapType map, FilmReport film, Known known)
    {
        String id = map.has("id") && map.get("id").isString() ? map.getString("id") : null;

        if (id != null && id.indexOf(':') > 0 && isForm(map))
        {
            if (!known.form.test(map))
            {
                film.add(UNKNOWN_FORM, id);

                /* The rest of an unknown form is its addon's business */
                return;
            }

            if (id.equals("bbs:model"))
            {
                String model = string(map, "model");

                if (!model.isEmpty() && known.models != null && !known.models.contains(model))
                {
                    film.add(MISSING_MODEL, model);
                }
            }
            else if (id.equals("bbs:particle"))
            {
                String effect = string(map, "effect");

                if (!effect.isEmpty() && known.particles != null && !known.particles.contains(effect))
                {
                    film.add(MISSING_PARTICLE, effect);
                }
            }

            String texture = string(map, "texture");

            if (texture.startsWith("assets:") && !known.asset.test(texture))
            {
                film.add(MISSING_TEXTURE, texture);
            }
        }

        String type = map.has("type") && map.get("type").isString() ? map.getString("type") : null;

        if (type != null && map.has("keyframes") && known.keyframeTypes != null && !known.keyframeTypes.contains(type))
        {
            film.add(UNKNOWN_KEYFRAMES, type);
        }

        if ("bbs:audio".equals(type) || "bbs:audio".equals(id))
        {
            String audio = string(map, "audio");

            if (audio.startsWith("assets:") && !known.asset.test(audio))
            {
                film.add(MISSING_AUDIO, audio);
            }
            else if (audio.startsWith("file:") && !new java.io.File(audio.substring(5)).isFile())
            {
                /* A local file ("file:" link) that isn't on this computer */
                film.add(MISSING_AUDIO, audio);
            }
        }
    }

    /** A form's map has these besides its id; a clip, keyframe or replay doesn't. */
    private static boolean isForm(MapType map)
    {
        return map.has("parts") || map.has("transform") || map.has("visible") || map.has("lighting") || map.has("anchor") || map.has("hitbox");
    }

    private static String string(MapType map, String key)
    {
        return map.has(key) && map.get(key).isString() ? map.getString(key) : "";
    }
}
