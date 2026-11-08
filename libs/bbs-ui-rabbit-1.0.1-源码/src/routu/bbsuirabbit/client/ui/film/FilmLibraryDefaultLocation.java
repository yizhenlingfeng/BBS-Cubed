package routu.bbsuirabbit.client.ui.film;

import routu.bbsuirabbit.RabbitSettings;
import mchorse.bbs_mod.settings.Settings;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.DataPath;

public final class FilmLibraryDefaultLocation {
    private static final String ALL = "all";
    private static final String FOLDER_PREFIX = "folder:";

    private FilmLibraryDefaultLocation() {
    }

    public static boolean isAllFilms() {
        return ALL.equals(FilmLibraryDefaultLocation.get());
    }

    public static boolean isFolder(DataPath folder) {
        return FilmLibraryDefaultLocation.get().equals(FilmLibraryDefaultLocation.encodeFolder(folder));
    }

    public static void setAllFilms() {
        FilmLibraryDefaultLocation.set(ALL);
    }

    public static void setFolder(DataPath folder) {
        FilmLibraryDefaultLocation.set(FilmLibraryDefaultLocation.encodeFolder(folder));
    }

    public static DataPath getFolder() {
        String value = FilmLibraryDefaultLocation.get();
        if (!value.startsWith(FOLDER_PREFIX)) {
            return null;
        }
        DataPath path = new DataPath(value.substring(FOLDER_PREFIX.length()));
        path.folder = true;
        return path;
    }

    private static String encodeFolder(DataPath folder) {
        return FOLDER_PREFIX + (folder == null ? "" : folder.toString());
    }

    private static String get() {
        if (RabbitSettings.filmLibraryDefaultLocation == null) {
            return ALL;
        }
        String value = (String)RabbitSettings.filmLibraryDefaultLocation.get();
        return value == null || value.isEmpty() ? ALL : value;
    }

    private static void set(String value) {
        if (RabbitSettings.filmLibraryDefaultLocation != null) {
            if (value.equals(FilmLibraryDefaultLocation.get())) {
                return;
            }
            RabbitSettings.filmLibraryDefaultLocation.set(value);
            FilmLibraryDefaultLocation.saveNow();
        }
    }

    private static void saveNow() {
        BaseValue baseValue = RabbitSettings.filmLibraryDefaultLocation.getRoot();
        if (baseValue instanceof Settings) {
            Settings settings = (Settings)baseValue;
            settings.save();
        }
    }
}
