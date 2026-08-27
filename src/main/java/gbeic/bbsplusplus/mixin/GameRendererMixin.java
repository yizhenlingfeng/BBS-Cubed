package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.compat.vfx.VfxCoreShaderRegistrationGuard;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

 /**
  * 混入 GameRenderer 类。
  */

 @Mixin(GameRenderer.class)
public class GameRendererMixin {

    /**
     * 注入目标：{@code GameRenderer#loadPrograms(ResourceFactory)} 入口。
     * 注入原因：当前渲染加载链会在一次资源重载中重复触发 Fabric 核心着色器注册事件。
     * 修改行为：开始新一轮注册代次，让 VFX 兼容包装器在本轮只执行一次各自的着色器回调。
     */
    @Inject(method = "loadPrograms", at = @At("HEAD"))
    private void bbspp$beginVfxCoreShaderReload(CallbackInfo ci)
    {
        VfxCoreShaderRegistrationGuard.beginReload();
    }
}
