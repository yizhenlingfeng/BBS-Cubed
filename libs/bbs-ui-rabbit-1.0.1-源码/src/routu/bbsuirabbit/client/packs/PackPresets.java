package routu.bbsuirabbit.client.packs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import routu.bbsuirabbit.mixin.PackOrganizerAccessor;
import net.minecraft.class_310;
import net.minecraft.class_3288;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resource pack presets: named combinations of selected packs, in their order, that the resource pack
 * screen can switch to in one click. Kept in {@code config/bbs_ui_rabbit/resource_pack_presets.json}, so
 * they are the same in every world.
 *
 * <p>A preset stores pack ids as the screen lists them, top (highest priority) first. Applying one
 * moves the screen's two columns to match; packs that aren't in the folder any more are skipped and
 * reported, and packs the game always needs stay selected.</p>
 */
public final class PackPresets
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static List<Preset> presets;

    private PackPresets()
    {}

    public static final class Preset
    {
        public String name;
        public final List<String> packs = new ArrayList<>();

        public Preset(String name, List<String> packs)
        {
            this.name = name;
            this.packs.addAll(packs);
        }
    }

    /* Storage */

    private static File file()
    {
        return new File(class_310.method_1551().field_1697, "config/bbs_ui_rabbit/resource_pack_presets.json");
    }

    public static List<Preset> all()
    {
        if (presets == null)
        {
            presets = load();
        }

        return presets;
    }

    private static List<Preset> load()
    {
        List<Preset> list = new ArrayList<>();
        File file = file();

        if (!file.isFile())
        {
            return list;
        }

        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
        {
            JsonElement root = JsonParser.parseReader(reader);
            JsonArray array = root.isJsonObject() && root.getAsJsonObject().has("presets") ? root.getAsJsonObject().getAsJsonArray("presets") : new JsonArray();

            for (JsonElement element : array)
            {
                if (!element.isJsonObject() || !element.getAsJsonObject().has("name"))
                {
                    continue;
                }

                JsonObject object = element.getAsJsonObject();
                List<String> packs = new ArrayList<>();

                if (object.has("packs") && object.get("packs").isJsonArray())
                {
                    for (JsonElement pack : object.getAsJsonArray("packs"))
                    {
                        packs.add(pack.getAsString());
                    }
                }

                list.add(new Preset(object.get("name").getAsString(), packs));
            }
        }
        catch (Exception e)
        {
            System.err.println("[BBS UI Rabbit] Couldn't read resource pack presets from " + file + ": " + e);
        }

        return list;
    }

    public static void save()
    {
        File file = file();
        JsonArray array = new JsonArray();

        for (Preset preset : all())
        {
            JsonObject object = new JsonObject();
            JsonArray packs = new JsonArray();

            preset.packs.forEach(packs::add);
            object.addProperty("name", preset.name);
            object.add("packs", packs);
            array.add(object);
        }

        JsonObject root = new JsonObject();

        root.add("presets", array);

        try
        {
            file.getParentFile().mkdirs();

            File tmp = new File(file.getPath() + ".tmp");

            try (Writer writer = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8))
            {
                GSON.toJson(root, writer);
            }

            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        catch (Exception e)
        {
            System.err.println("[BBS UI Rabbit] Couldn't save resource pack presets to " + file + ": " + e);
        }
    }

    /* The screen's columns */

    /** The packs selected on the screen right now (not applied yet), top first. */
    public static List<String> current(PackOrganizerAccessor organizer)
    {
        List<String> names = new ArrayList<>();

        for (class_3288 profile : organizer.rabbit$getEnabled())
        {
            names.add(profile.method_14463());
        }

        return names;
    }

    public static boolean isCurrent(PackOrganizerAccessor organizer, Preset preset)
    {
        return current(organizer).equals(preset.packs);
    }

    /**
     * Moves the screen's columns to the preset.
     *
     * @return ids of the preset's packs that aren't there any more (skipped)
     */
    public static List<String> apply(PackOrganizerAccessor organizer, Preset preset)
    {
        List<class_3288> enabled = organizer.rabbit$getEnabled();
        List<class_3288> available = organizer.rabbit$getAvailable();
        Map<String, class_3288> byName = new LinkedHashMap<>();
        Collection<class_3288> profiles = organizer.rabbit$getManager().method_14441();

        for (class_3288 profile : profiles)
        {
            byName.put(profile.method_14463(), profile);
        }

        /* What the screen holds wins over the manager's list for the same id (same objects normally) */
        for (class_3288 profile : enabled)
        {
            byName.put(profile.method_14463(), profile);
        }

        for (class_3288 profile : available)
        {
            byName.put(profile.method_14463(), profile);
        }

        List<class_3288> next = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String name : preset.packs)
        {
            class_3288 profile = byName.get(name);

            if (profile == null)
            {
                missing.add(name);
            }
            else if (!next.contains(profile))
            {
                next.add(profile);
            }
        }

        /* Packs the game always needs (vanilla's own, mods' built-in resources) stay, where they're pinned */
        for (class_3288 profile : new ArrayList<>(enabled))
        {
            if (profile.method_14464() && !next.contains(profile))
            {
                if (profile.method_14466() == class_3288.class_3289.field_14280)
                {
                    next.add(0, profile);
                }
                else
                {
                    next.add(profile);
                }
            }
        }

        enabled.clear();
        enabled.addAll(next);
        available.clear();

        for (class_3288 profile : byName.values())
        {
            if (!next.contains(profile))
            {
                available.add(profile);
            }
        }

        return missing;
    }
}
