package gbeic.bbsplusplus.mixin.client.accessor;

import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.factory.IFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link UIClips} 的私有成员，供音频拖入使用。
 *
 * <p>{@code factory} 用于按注册类型创建剪辑：客户端工厂注册的是 {@code AudioClientClip}
 * 子类，直接 {@code new AudioClip} 会因 {@code UIClip.FACTORIES} 查不到该类而打不开编辑面板。
 * {@code addClip} 保留原版的选中与数据链路。</p>
 */
@Mixin(value = UIClips.class, remap = false)
public interface UIClipsInvoker
{
    @Accessor(value = "factory", remap = false)
    IFactory<Clip, ClipFactoryData> bbspp_cml$getFactory();

    @Invoker(value = "addClip", remap = false)
    void bbspp_cml$invokeAddClip(Clip clip, int tick, int layer, int duration);
}
