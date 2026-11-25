package gbeic.bbsplusplus.mixin12111;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import mchorse.bbs_mod.client.BBSRendering;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderLayer.class)
public class RenderLayerIrisPositionColorMixin121
{
    @WrapOperation(
        method = "draw",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"),
        require = 1
    )
    private void bbspp$usePositionColorProgram(RenderPass pass, RenderPipeline pipeline, Operation<Void> original)
    {
        Identifier id = pipeline.getLocation();
        boolean useVanillaProgram = switch (id.getPath())
        {
            case "pipeline/draw_position_color", "pipeline/draw_position_color_no_depth",
                "pipeline/recorder_camera_preview" -> id.getNamespace().equals("bbs");
            case "pipeline/outline_color", "pipeline/outline_depth" -> id.getNamespace().equals("bbsplusplus");
            default -> false;
        };

        if (!useVanillaProgram || !BBSRendering.isIrisShadersEnabled())
        {
            original.call(pass, pipeline);
            return;
        }

        // These layers use their own position-colour shader and render state in every pass.
        boolean previousBypass = ImmediateState.bypass;
        ImmediateState.bypass = true;

        try
        {
            original.call(pass, pipeline);
        }
        finally
        {
            ImmediateState.bypass = previousBypass;
        }
    }
}
