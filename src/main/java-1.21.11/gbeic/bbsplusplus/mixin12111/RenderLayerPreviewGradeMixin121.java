package gbeic.bbsplusplus.mixin12111;

import gbeic.bbsplusplus.client.grade.PreviewGradeHook;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 面板内 3D 预览的纹理分级。
 *
 * <p>{@code RenderLayer.draw} 是预览唯一会走的出口（{@code ModelInstance.render} 的
 * {@code ModelPreviewRenderer.ACTIVE} 分支），所以在这里接管。描述符里的 MC 类型写开发
 * 环境名（{@code BuiltBuffer}），Loom 的 mixin 注解处理器会连同类型一起重映射；不要再手写
 * 混淆名的孪生 mixin，否则两份同时命中、同一帧画两遍。
 *
 * <p>HEAD 负责「需要调色/白化」的接管；RETURN 负责「只开附魔光效」时在基础层之后叠加
 * 光效（见 {@link PreviewGradeHook#afterDraw()}）。
 */
@Mixin(value = RenderLayer.class, remap = false)
public class RenderLayerPreviewGradeMixin121
{
    @Inject(
        method = "draw(Lnet/minecraft/client/render/BuiltBuffer;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void bbspp$drawPreviewGrade(BuiltBuffer built, CallbackInfo info)
    {
        if (PreviewGradeHook.handle(built))
        {
            info.cancel();
        }
    }

    @Inject(
        method = "draw(Lnet/minecraft/client/render/BuiltBuffer;)V",
        at = @At("RETURN"),
        require = 0
    )
    private void bbspp$afterDrawPreviewGrade(BuiltBuffer built, CallbackInfo info)
    {
        PreviewGradeHook.afterDraw();
    }
}
