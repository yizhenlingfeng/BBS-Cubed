package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.texture.TextureTweenManager;
import mchorse.bbs_mod.graphics.texture.TextureManager;
import mchorse.bbs_mod.utils.watchdog.WatchDogEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

/**
 * 让 BBSPPP 的运行时补间纹理缓存跟随 BBS 资源重载失效。
 *
 * <p>BBS 的纹理管理器只认识真实资源 Link，不知道 BBSPPP 根据源贴图生成的临时纹理。
 * 该 Mixin 在全量重载或文件监视器热重载完成后清空补间缓存，防止继续绑定已删除或内容过期的 OpenGL 纹理。</p>
 */
@Mixin(value = TextureManager.class, remap = false)
public class TextureManagerReloadMixin
{
    /**
     * 注入目标：{@link TextureManager#delete()} 完成后。
     * 注入原因：F6 等全量资源重载会删除实际纹理，但 BBSPPP 的静态 Link 缓存不会自动清空。
     * 修改行为：同步清空全部运行时补间缓存，使下一帧重新生成纹理。
     */
    @Inject(method = "delete", at = @At("TAIL"))
    private void bbsppp$invalidateTweenAfterDelete(CallbackInfo ci)
    {
        TextureTweenManager.invalidateAll();
    }

    /**
     * 注入目标：{@link TextureManager#accept(Path, WatchDogEvent)} 完成后。
     * 注入原因：单个源贴图热重载时 Link 不变，旧补间帧无法仅凭缓存键发现内容已经变化。
     * 修改行为：源资源发生变化后清空补间缓存，保证随后使用最新像素重新计算。
     */
    @Inject(method = "accept", at = @At("TAIL"))
    private void bbsppp$invalidateTweenAfterAssetChange(Path path, WatchDogEvent event, CallbackInfo ci)
    {
        TextureTweenManager.invalidateAll();
    }
}
