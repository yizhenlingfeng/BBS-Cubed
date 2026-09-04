package gbeic.bbsplusplus.mixin.client.accessor;

import gbeic.bbsplusplus.api.UIClipsAccessor;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.factory.IFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link UIClips} 的私有成员供音频拖入使用:factory 用于按注册类型
 * 创建剪辑(客户端工厂注册的是 AudioClientClip 子类,直接 new AudioClip
 * 会因 UIClip.FACTORIES 查不到该类而打不开编辑面板),addClip 含原版的
 * 选中与数据链路。
 */
@Mixin(value = UIClips.class, remap = false)
public interface UIClipsInvoker extends UIClipsAccessor
{
    @Accessor(value = "factory", remap = false)
    public IFactory<Clip, ClipFactoryData> bbspp_cml$getFactory();

    @Invoker(value = "addClip", remap = false)
    public void bbspp_cml$invokeAddClip(Clip clip, int tick, int layer, int duration);
}
