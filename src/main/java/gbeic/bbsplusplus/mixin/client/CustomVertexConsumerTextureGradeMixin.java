package gbeic.bbsplusplus.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import gbeic.bbsplusplus.client.screen.TextureGradeRenderContext;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies matching grade shaders to block, item and text render layers. */
@Mixin(value = CustomVertexConsumerProvider.class, remap = false)
public abstract class CustomVertexConsumerTextureGradeMixin
{
    @Inject(method = "drawLayer", at = @At("TAIL"), remap = false)
    private static void bbspp_cml$gradeBufferedLayer(RenderLayer layer, CallbackInfo ci)
    {
        ShaderProgram original = RenderSystem.getShader();
        ShaderProgram selected = TextureGradeRenderContext.select(original);

        if (selected != original)
        {
            RenderSystem.setShader(() -> selected);
        }
    }
}
