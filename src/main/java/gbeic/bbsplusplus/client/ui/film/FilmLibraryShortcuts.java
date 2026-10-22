package gbeic.bbsplusplus.client.ui.film;

import gbeic.bbsplusplus.BBSAddonsSettings;
import mchorse.bbs_mod.utils.DataPath;

import java.util.ArrayList;
import java.util.List;

/**
 * 影片库快捷栏的持久化。路径以换行分隔保存在隐藏设置项中。
 */
public final class FilmLibraryShortcuts
{
    private FilmLibraryShortcuts()
    {
    }

    public static List<DataPath> load()
    {
        List<DataPath> shortcuts = new ArrayList<>();

        if (BBSAddonsSettings.filmLibraryShortcuts == null)
        {
            return shortcuts;
        }

        String raw = BBSAddonsSettings.filmLibraryShortcuts.get();

        if (raw == null || raw.isEmpty())
        {
            return shortcuts;
        }

        for (String line : raw.split("\\R"))
        {
            String path = line.trim();

            if (path.isEmpty())
            {
                continue;
            }

            DataPath folder = new DataPath(path);

            folder.folder = true;
            shortcuts.add(folder);
        }

        return shortcuts;
    }

    public static void save(List<DataPath> shortcuts)
    {
        if (BBSAddonsSettings.filmLibraryShortcuts == null)
        {
            return;
        }

        StringBuilder builder = new StringBuilder();

        for (DataPath shortcut : shortcuts)
        {
            if (builder.length() > 0)
            {
                builder.append('\n');
            }

            builder.append(shortcut.toString());
        }

        BBSAddonsSettings.filmLibraryShortcuts.set(builder.toString());
    }

    public static boolean isPinned(DataPath folder)
    {
        for (DataPath shortcut : load())
        {
            if (shortcut.equals(folder))
            {
                return true;
            }
        }

        return false;
    }

    public static void pin(DataPath folder)
    {
        if (folder == null || folder.strings.isEmpty())
        {
            return;
        }

        List<DataPath> shortcuts = load();

        for (DataPath shortcut : shortcuts)
        {
            if (shortcut.equals(folder))
            {
                return;
            }
        }

        DataPath copy = folder.copy();

        copy.folder = true;
        shortcuts.add(copy);
        save(shortcuts);
    }

    public static void unpin(DataPath folder)
    {
        List<DataPath> shortcuts = load();

        shortcuts.removeIf((shortcut) -> shortcut.equals(folder));
        save(shortcuts);
    }
}
