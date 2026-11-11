package gbeic.bbsplusplus.mixin12111;

import gbeic.bbsplusplus.api.BBSPPFormValues;
import mchorse.bbs_mod.forms.forms.TrailForm;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.TrailFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.render.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.11 拖尾颜色补全。
 *
 * <p>1.20.x 通过 {@code RenderSystem.setShaderColor} 把颜色关键帧塞进固定管线；1.21.11
 * 的 {@code TrailFormRenderer.fill} 把顶点颜色硬编码成白色，颜色关键帧完全丢失。
 * 这里在 {@code render3D} 开头算出「上下文颜色 × 形态颜色」，再重定向 {@code fill}
 * 里唯一的 {@code VertexConsumer.color(...)} 调用。</p>
 */
@Mixin(value = TrailFormRenderer.class, remap = false)
public abstract class TrailFormRendererMixin121
{
    @Unique
    private final Color bbspp$trailColor = new Color(1F, 1F, 1F, 1F);

    @Inject(method = "render3D", at = @At("HEAD"), require = 0)
    private void bbspp$captureTrailColor(FormRenderingContext context, CallbackInfo ci)
    {
        Color color = this.bbspp$trailColor;

        color.set(context.color);

        TrailForm form = ((TrailFormRenderer) (Object) this).getForm();
        ValueColor colorValue = BBSPPFormValues.getColor(form);

        FormColorBlend.blend(color, colorValue != null && colorValue.get() != null ? colorValue.get() : Color.white());
    }

    @Redirect(
        method = "fill",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/VertexConsumer;color(FFFF)Lnet/minecraft/client/render/VertexConsumer;"
        ),
        require = 0
    )
    private VertexConsumer bbspp$applyTrailColor(VertexConsumer instance, float r, float g, float b, float a)
    {
        Color color = this.bbspp$trailColor;

        return instance.color(color.r, color.g, color.b, color.a);
    }
}
