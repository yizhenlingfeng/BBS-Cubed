package routu.bbsuirabbit.client.launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.class_310;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the launcher remembers about worlds that the worlds themselves don't hold: favourites,
 * the user's own tags, aliases, covers, the sort order and the last selection. Keyed by save folder name, kept
 * in {@code config/bbs_ui_rabbit/launcher.json}.
 */
public class LauncherPrefs
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public final Set<String> favorites = new LinkedHashSet<>();
    public final Map<String, List<String>> tags = new HashMap<>();
    /** Folder → chosen cover: "film:<id>[|<copy>]" (a locked film cover, with a copy of the picture it had then) or "file:<path>"; absent = newest film cover, then the world icon. */
    public final Map<String, String> covers = new HashMap<>();
    /** Folder → the name the launcher shows instead of the world's own (which stays in level.dat). */
    public final Map<String, String> aliases = new HashMap<>();
    /** "auto" follows BBS's light/dark surfaces; "light" / "dark" are picked with the launcher's lamp. */
    public String theme = "auto";
    public String sort = "recent";
    public String selected = "";

    private File file()
    {
        return new File(class_310.method_1551().field_1697, "config/bbs_ui_rabbit/launcher.json");
    }

    public void load()
    {
        File file = this.file();

        if (!file.isFile())
        {
            return;
        }

        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
        {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            this.favorites.clear();
            this.tags.clear();

            if (root.has("favorites"))
            {
                for (JsonElement element : root.getAsJsonArray("favorites"))
                {
                    this.favorites.add(element.getAsString());
                }
            }

            if (root.has("tags"))
            {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("tags").entrySet())
                {
                    List<String> list = new ArrayList<>();

                    for (JsonElement element : entry.getValue().getAsJsonArray())
                    {
                        list.add(element.getAsString());
                    }

                    if (!list.isEmpty())
                    {
                        this.tags.put(entry.getKey(), list);
                    }
                }
            }

            this.covers.clear();

            if (root.has("covers"))
            {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("covers").entrySet())
                {
                    this.covers.put(entry.getKey(), entry.getValue().getAsString());
                }
            }

            this.aliases.clear();

            if (root.has("aliases"))
            {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("aliases").entrySet())
                {
                    this.aliases.put(entry.getKey(), entry.getValue().getAsString());
                }
            }

            if (root.has("theme"))
            {
                this.theme = root.get("theme").getAsString();
            }

            if (root.has("sort"))
            {
                this.sort = root.get("sort").getAsString();
            }

            if (root.has("selected"))
            {
                this.selected = root.get("selected").getAsString();
            }
        }
        catch (Exception e)
        {
            System.err.println("[BBS UI Rabbit] Failed to read launcher settings " + file + ": " + e);
        }
    }

    public void save()
    {
        JsonObject root = new JsonObject();
        JsonArray favorites = new JsonArray();
        JsonObject tags = new JsonObject();

        this.favorites.forEach(favorites::add);

        for (Map.Entry<String, List<String>> entry : this.tags.entrySet())
        {
            if (entry.getValue().isEmpty())
            {
                continue;
            }

            JsonArray array = new JsonArray();

            entry.getValue().forEach(array::add);
            tags.add(entry.getKey(), array);
        }

        root.add("favorites", favorites);
        root.add("tags", tags);

        JsonObject covers = new JsonObject();

        this.covers.forEach(covers::addProperty);
        root.add("covers", covers);

        JsonObject aliases = new JsonObject();

        this.aliases.forEach(aliases::addProperty);
        root.add("aliases", aliases);
        root.addProperty("theme", this.theme);
        root.addProperty("sort", this.sort);
        root.addProperty("selected", this.selected);

        File file = this.file();

        try
        {
            file.getParentFile().mkdirs();

            File tmp = new File(file.getPath() + ".tmp");

            try (Writer writer = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8))
            {
                GSON.toJson(root, writer);
            }

            Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        catch (Exception e)
        {
            System.err.println("[BBS UI Rabbit] Failed to save launcher settings " + file + ": " + e);
        }
    }

    public List<String> tagsOf(String folder)
    {
        return this.tags.getOrDefault(folder, List.of());
    }

    public void addTag(String folder, String tag)
    {
        List<String> list = new ArrayList<>(this.tagsOf(folder));

        if (!list.contains(tag))
        {
            list.add(tag);
            this.tags.put(folder, list);
            this.save();
        }
    }

    public void removeTag(String folder, String tag)
    {
        List<String> list = new ArrayList<>(this.tagsOf(folder));

        if (list.remove(tag))
        {
            if (list.isEmpty())
            {
                this.tags.remove(folder);
            }
            else
            {
                this.tags.put(folder, list);
            }

            this.save();
        }
    }

    public void setAlias(String folder, String alias)
    {
        if (alias == null || alias.isBlank())
        {
            this.aliases.remove(folder);
        }
        else
        {
            this.aliases.put(folder, alias.trim());
        }

        this.save();
    }

    public boolean hasTag(String tag)
    {
        return this.tags.values().stream().anyMatch((list) -> list.contains(tag));
    }

    /** Renames a tag on every world (merging into {@code to} where a world has both); {@code to} null removes it everywhere. */
    public void renameTag(String from, String to)
    {
        for (String folder : new ArrayList<>(this.tags.keySet()))
        {
            List<String> list = new ArrayList<>(this.tags.get(folder));
            int at = list.indexOf(from);

            if (at < 0)
            {
                continue;
            }

            if (to == null || list.contains(to))
            {
                list.remove(at);
            }
            else
            {
                list.set(at, to);
            }

            if (list.isEmpty())
            {
                this.tags.remove(folder);
            }
            else
            {
                this.tags.put(folder, list);
            }
        }

        this.save();
    }

    public boolean toggleFavorite(String folder)
    {
        boolean now = !this.favorites.remove(folder);

        if (now)
        {
            this.favorites.add(folder);
        }

        this.save();

        return now;
    }

    /** A deleted world leaves nothing behind. */
    public void forget(String folder)
    {
        boolean changed = this.favorites.remove(folder) | this.tags.remove(folder) != null | this.covers.remove(folder) != null | this.aliases.remove(folder) != null;

        if (changed)
        {
            this.save();
        }
    }
}
