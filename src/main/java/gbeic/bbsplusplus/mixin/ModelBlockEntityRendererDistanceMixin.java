package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.settings.ModSettings;
import mchorse.bbs_mod.client.renderer.ModelBlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 模型方块渲染距离可配置。
 *
 * <p>BBS 2.7 的 {@code ModelBlockEntityRenderer} 并未覆写
 * {@link BlockEntityRenderer#getRenderDistance()}，而是使用接口默认值；
 * 因此这里注入接口默认方法，并用 {@code instanceof ModelBlockEntityRenderer}
 * 限定只影响模型方块。0 表示保留接口默认距离。</p>
 */
@Mixin(value = BlockEntityRenderer.class, remap = true)
public interface ModelBlockEntityRendererDistanceMixin
{
    @Inject(method = "getRenderDistance", at = @At("HEAD"), cancellable = true, remap = true, require = 1)
    private void bbspp$overrideRenderDistance(CallbackInfoReturnable<Integer> cir)
    {
        if (!((Object) this instanceof ModelBlockEntityRenderer))
        {
            return;
        }

        if (ModSettings.modelBlockRenderDistance != null)
        {
            int distance = ModSettings.modelBlockRenderDistance.get();

            if (distance > 0)
            {
                cir.setReturnValue(distance);
            }
        }
    }
}
