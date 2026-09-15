package gbeic.bbsplusplus.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import gbeic.bbsplusplus.api.FormColorProvider;
import gbeic.bbsplusplus.client.screen.TextureGradeRenderContext;
import mchorse.bbs_mod.forms.forms.TrailForm;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.TrailFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/** Makes TrailForm's added color keyframe visible and texture grading functional. */
@Mixin(value = TrailFormRenderer.class, remap = false)
public abstract class TrailFormRendererMixin
{
    @Unique
    private boolean bbspp_cml$changedShaderColor;

    @Redirect(
        method = "render3D",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShader(Ljava/util/function/Supplier;)V",
            ordinal = 1
        ),
        require = 0
    )
    private void bbspp_cml$useTrailGradeShader(
        Supplier<ShaderProgram> original,
        FormRenderingContext context
    )
    {
        TrailForm form = ((TrailFormRenderer) (Object) this).getForm();
        Color color = Color.white();

        color.set(context.color);
        FormColorBlend.blend(
            color,
            ((FormColorProvider) form).bbspp_cml$getColor().get()
        );

        RenderSystem.setShaderColor(color.r, color.g, color.b, color.a);
        this.bbspp_cml$changedShaderColor = true;

        RenderSystem.setShader(() -> TextureGradeRenderContext.select(GameRenderer.getPositionTexProgram(), form));
    }

    @Inject(method = "render3D", at = @At("TAIL"), remap = false, require = 0)
    private void bbspp_cml$resetTrailColor(FormRenderingContext context, CallbackInfo ci)
    {
        if (this.bbspp_cml$changedShaderColor)
        {
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            this.bbspp_cml$changedShaderColor = false;
        }
    }
}
