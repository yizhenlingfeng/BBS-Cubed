package gbeic.bbsplusplus.client.ui.film;

import mchorse.bbs_mod.utils.DataPath;

/**
 * 影片库文件夹树的回调实现。
 * <p>
 * 单独成类而不是在 Mixin 里写匿名内部类：匿名类会以 Mixin 类型作为外部类字段，
 * 但 Mixin 类在运行期不会被加载，启用功能时会直接抛错。
 * </p>
 */
public class UIFilmLibraryFolderActions implements UIFilmLibraryFolderTree.Actions
{
    private final IFilmLibraryFolderActionsHost host;

    public UIFilmLibraryFolderActions(IFilmLibraryFolderActionsHost host)
    {
        this.host = host;
    }

    @Override
    public void createFilm(DataPath folder)
    {
        this.host.bbspp$createFilmAt(folder);
    }

    @Override
    public void createFolder(DataPath folder)
    {
        this.host.bbspp$createFolderAt(folder);
    }

    @Override
    public void renameFolder(DataPath folder)
    {
        this.host.bbspp$renameFolder(folder);
    }

    @Override
    public void deleteFolder(DataPath folder)
    {
        this.host.bbspp$deleteFolder(folder);
    }

    @Override
    public void openFolder(DataPath folder)
    {
        this.host.bbspp$openFolder(folder);
    }

    @Override
    public void refresh()
    {
        this.host.bbspp$refreshFilmLibrary();
    }
}
