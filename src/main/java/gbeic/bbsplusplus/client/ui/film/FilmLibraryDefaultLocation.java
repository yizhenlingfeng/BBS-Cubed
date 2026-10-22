package gbeic.bbsplusplus.client.ui.film;

import gbeic.bbsplusplus.BBSAddonsSettings;
import mchorse.bbs_mod.settings.Settings;
import mchorse.bbs_mod.utils.DataPath;

/**
 * 管理影片库的默认打开位置。
 * <p>
 * 设置值编码为 {@code folder:路径}；根目录对应 {@code folder:}。旧版本的 {@code all}
 * 会按根目录处理。
 * </p>
 */
public final class FilmLibraryDefaultLocation
{
    private static final String LEGACY_ALL = "all";
    private static final String FOLDER_PREFIX = "folder:";

    private FilmLibraryDefaultLocation()
    {
    }

    /** 判断指定文件夹是否为默认入口。 */
    public static boolean isFolder(DataPath folder)
    {
        return get().equals(encodeFolder(folder));
    }

    /** 把默认入口设为指定文件夹。 */
    public static void setFolder(DataPath folder)
    {
        set(encodeFolder(folder));
    }

    /** @return 默认入口的文件夹路径；根目录返回空路径。 */
    public static DataPath getFolder()
    {
        String value = get();
        String path = value.startsWith(FOLDER_PREFIX) ? value.substring(FOLDER_PREFIX.length()) : "";
        DataPath folder = new DataPath(path);

        folder.folder = true;

        return folder;
    }

    private static String encodeFolder(DataPath folder)
    {
        return FOLDER_PREFIX + (folder == null ? "" : folder.toString());
    }

    private static String get()
    {
        if (BBSAddonsSettings.filmLibraryDefaultLocation == null)
        {
            return FOLDER_PREFIX;
        }

        String value = BBSAddonsSettings.filmLibraryDefaultLocation.get();

        if (value == null || value.isEmpty() || LEGACY_ALL.equals(value))
        {
            return FOLDER_PREFIX;
        }

        return value;
    }

    private static void set(String value)
    {
        if (BBSAddonsSettings.filmLibraryDefaultLocation != null)
        {
            if (value.equals(get()))
            {
                return;
            }

            BBSAddonsSettings.filmLibraryDefaultLocation.set(value);
            saveNow();
        }
    }

    private static void saveNow()
    {
        if (BBSAddonsSettings.filmLibraryDefaultLocation.getRoot() instanceof Settings settings)
        {
            settings.save();
        }
    }
}
