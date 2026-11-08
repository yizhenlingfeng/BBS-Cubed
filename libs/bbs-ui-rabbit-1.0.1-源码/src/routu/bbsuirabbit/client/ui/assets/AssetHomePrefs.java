package routu.bbsuirabbit.client.ui.assets;

import routu.bbsuirabbit.RabbitMod;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-panel preferences of the asset workbenches ({@code bbsplusplus/asset_home.json}),
 * keyed by each panel's recent-type: favourite ids, the selected folder, sort order, grid
 * cell size and the expanded folders.
 */
public final class AssetHomePrefs
{
    private static final File FILE = BBSMod.getSettingsPath("bbs_ui_rabbit/asset_home.json");

    public static class Entry
    {
        public final Set<String> favorites = new HashSet<>();
        public String folder = "";
        public AssetIndex.Sort sort = AssetIndex.Sort.NAME;
        public int cell = 96;
        public final Set<String> expanded = new LinkedHashSet<>();
    }

    private static Map<String, Entry> entries;
    private static boolean dirty;

    private AssetHomePrefs()
    {}

    public static Entry of(String recentType)
    {
        load();

        return entries.computeIfAbsent(recentType == null ? "" : recentType, (k) -> new Entry());
    }

    public static void save()
    {
        dirty = true;
        write();
    }

    private static void load()
    {
        if (entries != null)
        {
            return;
        }

        entries = new HashMap<>();

        if (!FILE.isFile())
        {
            return;
        }

        try
        {
            BaseType data = DataToString.read(FILE);

            if (!data.isMap())
            {
                return;
            }

            for (String type : data.asMap().keys())
            {
                BaseType raw = data.asMap().get(type);

                if (!raw.isMap())
                {
                    continue;
                }

                MapType map = raw.asMap();
                Entry entry = new Entry();

                for (BaseType fav : map.getList("favorites"))
                {
                    if (fav.isString())
                    {
                        entry.favorites.add(fav.asString());
                    }
                }

                entry.folder = map.getString("folder");
                entry.sort = map.getString("sort").equals("recent") ? AssetIndex.Sort.RECENT : AssetIndex.Sort.NAME;
                entry.cell = map.getInt("cell", entry.cell);

                for (String line : map.getString("expanded").split("\n"))
                {
                    if (!line.isEmpty())
                    {
                        entry.expanded.add(line);
                    }
                }

                entries.put(type, entry);
            }
        }
        catch (IOException | RuntimeException e)
        {
            RabbitMod.LOGGER.warn("读取资源主页配置失败：{}", FILE.getAbsolutePath(), e);
        }
    }

    private static void write()
    {
        if (!dirty)
        {
            return;
        }

        dirty = false;

        File parent = FILE.getParentFile();

        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
        {
            RabbitMod.LOGGER.warn("无法创建资源主页配置目录：{}", parent.getAbsolutePath());

            return;
        }

        MapType data = new MapType(false);

        for (Map.Entry<String, Entry> e : entries.entrySet())
        {
            MapType map = new MapType(false);
            ListType favs = new ListType();

            for (String id : e.getValue().favorites)
            {
                favs.addString(id);
            }

            map.put("favorites", favs);
            map.putString("folder", e.getValue().folder);
            map.putString("sort", e.getValue().sort == AssetIndex.Sort.RECENT ? "recent" : "name");
            map.putInt("cell", e.getValue().cell);
            map.putString("expanded", String.join("\n", e.getValue().expanded));

            data.put(e.getKey(), map);
        }

        if (!DataToString.writeSilently(FILE, data, true))
        {
            RabbitMod.LOGGER.warn("保存资源主页配置失败：{}", FILE.getAbsolutePath());
        }
    }
}
