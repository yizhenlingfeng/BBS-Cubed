package routu.bbsuirabbit.ui.morphing;

import routu.bbsuirabbit.RabbitMod;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

/** Lazy JSON storage of {@link MorphLayout} at bbsplusplus/morph_layout.json. */
public final class MorphLayoutStorage
{
    private static final File FILE = BBSMod.getSettingsPath("bbs_ui_rabbit/morph_layout.json");

    private static MorphLayout layout;
    private static boolean dirty;

    private MorphLayoutStorage()
    {}

    public static MorphLayout get()
    {
        if (layout == null)
        {
            layout = new MorphLayout();
            read();
        }

        return layout;
    }

    public static void save()
    {
        dirty = true;
        write();
    }

    private static void read()
    {
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

            MapType root = data.asMap();

            readStringMap(root, "parents", layout.parents);
            readStringMap(root, "folders", layout.folders);

            readListMap(root, "orders", layout.orders);
            readListMap(root, "pins", layout.pins);

            MapType colors = root.getMap("colors");

            for (String id : colors.keys())
            {
                layout.colors.put(id, colors.getInt(id) | 0xFF000000);
            }

            for (BaseType id : root.getList("hidden"))
            {
                if (id.isString())
                {
                    layout.hidden.add(id.asString());
                }
            }
        }
        catch (IOException | RuntimeException e)
        {
            RabbitMod.LOGGER.warn("读取伪装界面布局失败：{}", FILE.getAbsolutePath(), e);
        }
    }

    private static void readListMap(MapType root, String key, Map<String, java.util.List<String>> out)
    {
        MapType map = root.getMap(key);

        for (String k : map.keys())
        {
            java.util.List<String> list = new java.util.ArrayList<>();

            for (BaseType id : map.getList(k))
            {
                if (id.isString())
                {
                    list.add(id.asString());
                }
            }

            out.put(k, list);
        }
    }

    private static void readStringMap(MapType root, String key, Map<String, String> out)
    {
        MapType map = root.getMap(key);

        for (String k : map.keys())
        {
            out.put(k, map.getString(k));
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
            RabbitMod.LOGGER.warn("无法创建布局目录：{}", parent.getAbsolutePath());

            return;
        }

        MapType root = new MapType(false);
        MapType parents = new MapType(false);
        MapType folders = new MapType(false);
        MapType orders = new MapType(false);
        MapType pins = new MapType(false);
        MapType colors = new MapType(false);

        for (Map.Entry<String, Integer> e : layout.colors.entrySet())
        {
            colors.putInt(e.getKey(), e.getValue() & 0xFFFFFF);
        }
        ListType hidden = new ListType();

        for (Map.Entry<String, String> e : layout.parents.entrySet())
        {
            parents.putString(e.getKey(), e.getValue());
        }

        for (Map.Entry<String, String> e : layout.folders.entrySet())
        {
            folders.putString(e.getKey(), e.getValue());
        }

        writeListMap(layout.orders, orders);
        writeListMap(layout.pins, pins);

        for (String id : layout.hidden)
        {
            hidden.addString(id);
        }

        root.put("parents", parents);
        root.put("folders", folders);
        root.put("orders", orders);
        root.put("hidden", hidden);
        root.put("pins", pins);
        root.put("colors", colors);

        if (!DataToString.writeSilently(FILE, root, true))
        {
            RabbitMod.LOGGER.warn("保存伪装界面布局失败：{}", FILE.getAbsolutePath());
        }
    }

    private static void writeListMap(Map<String, java.util.List<String>> from, MapType to)
    {
        for (Map.Entry<String, java.util.List<String>> e : from.entrySet())
        {
            ListType ids = new ListType();

            for (String id : e.getValue())
            {
                ids.addString(id);
            }

            to.put(e.getKey(), ids);
        }
    }
}
