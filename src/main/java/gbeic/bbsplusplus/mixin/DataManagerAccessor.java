package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.presets.DataManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link DataManager} 的内部成员供自动保存使用：
 * 内存缓存 data 与预设文件路径解析 getFile。
 * 自动保存在主线程直接更新缓存（与原生 saveData 行为一致），仅把序列化和写盘放到后台线程。
 */
@Mixin(DataManager.class)
public interface DataManagerAccessor
{
    @Accessor("data")
    MapType bbspp$getData();

    @Invoker("getFile")
    Link bbspp$getFile(String group);
}
