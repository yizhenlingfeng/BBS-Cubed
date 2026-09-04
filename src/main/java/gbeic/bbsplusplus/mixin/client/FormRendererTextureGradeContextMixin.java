package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.client.screen.TextureGradeRenderContext;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.ui.framework.UIContext;
import net.minecraft.client.gl.ShaderProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

/** Establishes a form-local grade state for buffered and direct render paths. */
@Mixin(value = FormRenderer.class, remap = false)
public abstract class FormRendererTextureGradeContextMixin
{
    @Inject(method = "getShader", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void bbspp_cml$selectTextureGradeShader(
        FormRenderingContext context,
        Supplier<ShaderProgram> regular,
        Supplier<ShaderProgram> picking,
        CallbackInfoReturnable<Supplier<ShaderProgram>> cir
    )
    {
        if (context.isPicking())
        {
            return;
        }

        FormRenderer<?> renderer = (FormRenderer<?>) (Object) this;
        Supplier<ShaderProgram> original = cir.getReturnValue();

        cir.setReturnValue(() -> TextureGradeRenderContext.select(original.get(), renderer.getForm()));
    }

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void bbspp_cml$push3DGrade(FormRenderingContext context, CallbackInfo ci)
    {
        FormRenderer<?> renderer = (FormRenderer<?>) (Object) this;

        TextureGradeRenderContext.push(renderer.getForm(), context.isPicking());
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void bbspp_cml$pop3DGrade(FormRenderingContext context, CallbackInfo ci)
    {
        TextureGradeRenderContext.pop();
    }

    @Inject(method = "renderUI", at = @At("HEAD"), remap = false)
    private void bbspp_cml$pushUIGrade(UIContext context, int x1, int y1, int x2, int y2, CallbackInfo ci)
    {
        FormRenderer<?> renderer = (FormRenderer<?>) (Object) this;

        TextureGradeRenderContext.push(renderer.getForm(), false);
    }

    @Inject(method = "renderUI", at = @At("RETURN"), remap = false)
    private void bbspp_cml$popUIGrade(UIContext context, int x1, int y1, int x2, int y2, CallbackInfo ci)
    {
        TextureGradeRenderContext.pop();
    }
}
