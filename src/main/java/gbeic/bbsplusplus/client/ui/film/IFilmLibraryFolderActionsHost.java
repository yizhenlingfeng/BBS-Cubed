package gbeic.bbsplusplus.client.ui.film;

import mchorse.bbs_mod.utils.DataPath;

/**
 * 影片库文件夹树向外发起的仓库操作。
 * <p>
 * 该接口把「树上的意图」与「Mixin 中的实现」隔开。文件夹树和回调实现都放在普通类里，
 * 不把匿名内部类嵌进 Mixin 中，避免运行期 Mixin 转换后再加载内部类时找不到宿主类型。
 * </p>
 */
public interface IFilmLibraryFolderActionsHost
{
    /** 在指定文件夹中创建影片。 */
    void bbspp$createFilmAt(DataPath folder);

    /** 在指定文件夹中创建子文件夹。 */
    void bbspp$createFolderAt(DataPath folder);

    /** 重命名指定文件夹。 */
    void bbspp$renameFolder(DataPath folder);

    /** 删除指定文件夹。 */
    void bbspp$deleteFolder(DataPath folder);

    /** 在系统资源管理器中打开指定文件夹。 */
    void bbspp$openFolder(DataPath folder);

    /** 重新扫描并请求影片列表。 */
    void bbspp$refreshFilmLibrary();
}
