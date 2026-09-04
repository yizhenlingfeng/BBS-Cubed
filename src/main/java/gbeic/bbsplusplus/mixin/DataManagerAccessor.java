package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.presets.DataManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link DataManager} 的内部成员供自动保存与 FSloveCML 预设操作使用：
 * 内存缓存 data 与预设文件路径解析 getFile。
 * 自动保存在主线程直接更新缓存（与原生 saveData 行为一致），仅把序列化和写盘放到后台线程。
 * FSloveCML 的 PresetDataOperations 用 bbspp_cml$ 前缀方法实现 FS 版缺失的预设移除/重命名。
 */
@Mixin(DataManager.class)
public interface DataManagerAccessor
{
    @Accessor("data")
    MapType bbspp$getData();

    @Invoker("getFile")
    Link bbspp$getFile(String group);

    @Accessor(value = "data", remap = false)
    MapType bbspp_cml$getData();

    @Invoker(value = "getFile", remap = false)
    Link bbspp_cml$invokeGetFile(String group);
}
