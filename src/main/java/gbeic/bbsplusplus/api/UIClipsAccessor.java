package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.factory.IFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link UIClips} 的工厂与 addClip 入口。
 */
@Mixin(value = UIClips.class, remap = false)
public interface UIClipsAccessor
{
    @Accessor(value = "factory", remap = false)
    IFactory<Clip, ClipFactoryData> bbspp_cml$getFactory();

    @Invoker(value = "addClip", remap = false)
    void bbspp_cml$invokeAddClip(Clip clip, int tick, int layer, int duration);
}
