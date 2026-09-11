package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.BBSAddonsSettings;
import mchorse.bbs_mod.client.renderer.ModelBlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * P4：模型方块渲染距离可配置。
 *
 * <p>原版 {@link ModelBlockEntityRenderer#getRenderDistance()} 硬编码返回 512，
 * 导致 512 格内的所有模型方块都参与渲染与 look-at 计算。
 * 这里允许用户在 BBS++ 设置里调整（默认 256），0 表示回退到原版 512。</p>
 */
@Mixin(value = ModelBlockEntityRenderer.class, remap = true)
public class ModelBlockEntityRendererDistanceMixin
{
    @Inject(method = "getRenderDistance", at = @At("HEAD"), cancellable = true, remap = true)
    private void bbspp$overrideRenderDistance(CallbackInfoReturnable<Integer> cir)
    {
        if (BBSAddonsSettings.modelBlockRenderDistance != null)
        {
            int distance = BBSAddonsSettings.modelBlockRenderDistance.get();
            if (distance > 0)
            {
                cir.setReturnValue(distance);
            }
        }
    }
}
