package routu.bbsuirabbit.ui.morphing;

import routu.bbsuirabbit.RabbitMod;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class MorphCategoryIconStorage
{
    private static final File FILE = BBSMod.getSettingsPath("bbs_ui_rabbit/morph_category_icons.json");
    private static final Map<String, String> ICON_IDS = new HashMap<>();
    private static boolean loaded;

    private MorphCategoryIconStorage()
    {}

    public static Icon get(String categoryId)
    {
        return get(categoryId, Icons.FOLDER);
    }

    /** The user's pick, or {@code fallback} when none was picked for this id. */
    public static Icon get(String categoryId, Icon fallback)
    {
        load();

        Icon icon = Icons.ICONS.get(ICON_IDS.get(categoryId));

        return icon == null ? fallback : icon;
    }

    public static void set(String categoryId, Icon icon)
    {
        load();

        if (categoryId == null || icon == null || !Icons.ICONS.containsKey(icon.id))
        {
            return;
        }

        ICON_IDS.put(categoryId, icon.id);
        save();
    }

    private static void load()
    {
        if (loaded)
        {
            return;
        }

        loaded = true;

        if (!FILE.isFile())
        {
            return;
        }

        try
        {
            BaseType data = DataToString.read(FILE);

            if (data.isMap())
            {
                MapType map = data.asMap();

                for (String categoryId : map.keys())
                {
                    String iconId = map.getString(categoryId);

                    if (Icons.ICONS.containsKey(iconId))
                    {
                        ICON_IDS.put(categoryId, iconId);
                    }
                }
            }
        }
        catch (IOException | RuntimeException e)
        {
            RabbitMod.LOGGER.warn("读取伪装分类图标配置失败，将使用默认文件夹图标：{}", FILE.getAbsolutePath(), e);
        }
    }

    private static void save()
    {
        File parent = FILE.getParentFile();

        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
        {
            RabbitMod.LOGGER.warn("无法创建伪装分类图标配置目录：{}", parent.getAbsolutePath());

            return;
        }

        MapType data = new MapType(false);

        for (Map.Entry<String, String> entry : ICON_IDS.entrySet())
        {
            data.putString(entry.getKey(), entry.getValue());
        }

        if (!DataToString.writeSilently(FILE, data, true))
        {
            RabbitMod.LOGGER.warn("保存伪装分类图标配置失败：{}", FILE.getAbsolutePath());
        }
    }
}
