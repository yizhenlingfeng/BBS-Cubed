package gbeic.bbsplusplus.client.ui.film;

import mchorse.bbs_mod.utils.DataPath;

import java.util.Collection;

/**
 * 暴露给影片库布局使用的路径列表增强能力。
 * <p>
 * 原版 {@code UIDataPathList} 只面向当前文件夹做名称过滤。影片库第一阶段需要在不改数据格式的前提下
 * 支持完整路径搜索与本地排序偏好，因此通过接口把这些影片库专属行为从通用列表里隔离出来。
 * </p>
 */
public interface IFilmLibraryPathList
{
    /** 标记该列表由影片库使用，启用完整路径显示、路径搜索和排序偏好。 */
    void bbspp$setFilmLibraryMode(boolean enabled);

    /**
     * 请求在影片层级首次填充完成后应用「默认打开位置」。
     * <p>
     * 影片库列表在仪表盘创建时就已存在，此时影片层级仍为空，直接读取默认文件夹会被误判为
     * 「文件夹不存在」而回退到全部影片。把应用时机推迟到第一次 fill 之后即可避免。
     * </p>
     */
    void bbspp$applyFilmLibraryDefaultWhenFilled();

    /** @return 当前列表是否启用了新版影片库模式。 */
    boolean bbspp$isFilmLibraryMode();

    /** 按名称或完整路径过滤影片，空字符串则恢复当前文件夹列表。 */
    void bbspp$filterFilmLibrary(String query);

    /** 设置名称排序方向。 */
    void bbspp$setFilmLibrarySortDescending(boolean descending);

    /** @return 当前是否按名称降序排列。 */
    boolean bbspp$isFilmLibrarySortDescending();

    /** 显示指定文件夹的直属影片。 */
    void bbspp$showFilmLibraryFolder(DataPath folder);

    /** @return 影片仓库中的所有文件夹路径。 */
    Collection<DataPath> bbspp$getFilmLibraryFolders();

    /** @return 当前列表对应的文件夹。 */
    DataPath bbspp$getFilmLibraryFolder();

    /** @return 指定文件夹的直属影片。 */
    Collection<DataPath> bbspp$getFilmLibraryFilms(DataPath folder);

    /** @return 影片仓库中的全部影片。 */
    Collection<DataPath> bbspp$getFilmLibraryAllFilms();

    /** 打开指定影片。 */
    void bbspp$openFilmLibraryFilm(DataPath film);

    /** @return 影片层级版本号，用于列视图判断是否需要重建。 */
    int bbspp$getFilmLibraryRevision();

    /** 设置刷新后需要自动选中的影片 ID。 */
    void bbspp$selectFilmLibraryAfterRefresh(String id);

    /** @return 指定文件夹是否没有直属或嵌套的影片/子文件夹。 */
    boolean bbspp$isFilmLibraryFolderEmpty(DataPath folder);

    /** 记录一个文件夹路径，避免原版刷新漏掉刚被清空的空文件夹。 */
    void bbspp$rememberFilmLibraryFolder(DataPath folder);

    /** 移除已知文件夹及其子文件夹，通常用于真实删除文件夹后同步左侧树。 */
    void bbspp$forgetFilmLibraryFolder(DataPath folder);

    /** 文件夹重命名后同步已知文件夹缓存。 */
    void bbspp$renameKnownFilmLibraryFolder(DataPath folder, String name);
}
