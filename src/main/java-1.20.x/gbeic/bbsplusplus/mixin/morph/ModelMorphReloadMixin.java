package gbeic.bbsplusplus.mixin.morph;

import gbeic.bbsplusplus.client.morph.MorphPlanCache;
import mchorse.bbs_mod.graphics.texture.TextureManager;
import mchorse.bbs_mod.utils.watchdog.WatchDogEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

/**
 * 资源重载时整体失效形变缓存。
 *
 * <p>模型或贴图在 F6 重载、文件监视器热更新后内容可能已经变化，缓存的绑定姿态与
 * 混合贴图必须一起丢弃，否则会继续使用旧模型几何。</p>
 */
@Mixin(value = TextureManager.class, remap = false)
public class ModelMorphReloadMixin
{
    @Inject(method = "delete()V", at = @At("TAIL"), require = 1)
    private void bbsppp$invalidateOnDelete(CallbackInfo ci)
    {
        MorphPlanCache.invalidateAll();
    }

    @Inject(method = "accept(Ljava/nio/file/Path;Lmchorse/bbs_mod/utils/watchdog/WatchDogEvent;)V", at = @At("TAIL"), require = 1)
    private void bbsppp$invalidateOnWatch(Path path, WatchDogEvent event, CallbackInfo ci)
    {
        MorphPlanCache.invalidateAll();
    }
}
