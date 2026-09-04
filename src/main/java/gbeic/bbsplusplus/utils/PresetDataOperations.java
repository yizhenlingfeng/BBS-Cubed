package gbeic.bbsplusplus.utils;

import gbeic.bbsplusplus.mixin.DataManagerAccessor;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.presets.DataManager;

import java.io.File;

/**
 * FS 版 {@link DataManager} 缺失的预设移除/重命名实现，逻辑照抄 CML 的
 * {@code DataManager.removeData/renameData}：改内存缓存中的组 MapType 后
 * 整组写回预设文件。经 {@link DataManagerAccessor} 访问私有缓存与 getFile。
 */
public class PresetDataOperations
{
    public static void removeData(DataManager manager, String group, String key)
    {
        if (group.isEmpty() || key == null || key.isEmpty())
        {
            return;
        }

        MapType poses = ((DataManagerAccessor) manager).bbspp_cml$getData().getMap(group);

        if (!poses.has(key))
        {
            return;
        }

        poses.remove(key);
        writeGroup(manager, group, poses);
    }

    public static void renameData(DataManager manager, String group, String oldKey, String newKey)
    {
        if (group.isEmpty() || oldKey == null || oldKey.isEmpty() || newKey == null)
        {
            return;
        }

        newKey = newKey.trim();

        if (newKey.isEmpty() || oldKey.equals(newKey))
        {
            return;
        }

        MapType poses = ((DataManagerAccessor) manager).bbspp_cml$getData().getMap(group);

        if (!poses.has(oldKey))
        {
            return;
        }

        MapType pose = poses.getMap(oldKey);

        poses.remove(oldKey);
        poses.put(newKey, pose);
        writeGroup(manager, group, poses);
    }

    private static void writeGroup(DataManager manager, String group, MapType poses)
    {
        File file = BBSMod.getProvider().getFile(((DataManagerAccessor) manager).bbspp_cml$invokeGetFile(group));

        if (file != null)
        {
            file.getParentFile().mkdirs();
            DataToString.writeSilently(file, poses, true);
        }
    }
}
