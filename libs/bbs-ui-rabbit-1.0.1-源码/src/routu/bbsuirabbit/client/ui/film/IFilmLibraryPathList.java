package routu.bbsuirabbit.client.ui.film;

import java.util.Collection;
import mchorse.bbs_mod.utils.DataPath;

public interface IFilmLibraryPathList {
    public void rabbit$setFilmLibraryMode(boolean var1);

    public boolean rabbit$isFilmLibraryMode();

    public void rabbit$filterFilmLibrary(String var1);

    public void rabbit$setFilmLibrarySortDescending(boolean var1);

    public boolean rabbit$isFilmLibrarySortDescending();

    public void rabbit$showAllFilmLibraryFilms();

    public void rabbit$showFilmLibraryFolder(DataPath var1);

    public Collection<DataPath> rabbit$getFilmLibraryFolders();

    public boolean rabbit$isShowingAllFilmLibraryFilms();

    public DataPath rabbit$getFilmLibraryFolder();

    public void rabbit$selectFilmLibraryAfterRefresh(String var1);

    public boolean rabbit$isFilmLibraryFolderEmpty(DataPath var1);

    public void rabbit$rememberFilmLibraryFolder(DataPath var1);

    public void rabbit$forgetFilmLibraryFolder(DataPath var1);

    public void rabbit$renameKnownFilmLibraryFolder(DataPath var1, String var2);
}
