package gbeic.bbsplusplus.mixin12111;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import gbeic.bbsplusplus.BBSPlusPlusMod;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.client.render.picker.BBSPickerRenderer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.render.BuiltBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.nio.ByteBuffer;

@Mixin(value = BBSPickerRenderer.class, remap = false)
public abstract class PickerDrawFixMixin121
{
    @Unique
    private static GpuTextureView bbspp$whiteView;

    @Unique
    private static GpuSampler bbspp$nearest;

    @Unique
    private static boolean bbspp$ensureOpaqueSampler()
    {
        if (bbspp$whiteView != null)
        {
            return true;
        }

        GpuTexture texture = null;

        try
        {
            bbspp$nearest = RenderSystem.getSamplerCache().get(FilterMode.NEAREST);
            var device = RenderSystem.getDevice();
            int usage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
            texture = device.createTexture("bbspp_picker_opaque", usage, TextureFormat.RGBA8, 1, 1, 1, 1);
            device.createCommandEncoder().clearColorTexture(texture, 0xFFFFFFFF);
            bbspp$whiteView = device.createTextureView(texture);

            return true;
        }
        catch (RuntimeException e)
        {
            if (texture != null)
            {
                texture.close();
            }

            BBSPlusPlusMod.LOGGER.warn("[BBS++] 无法创建不透明拾取纹理", e);
            return false;
        }
    }

    @WrapMethod(method = "draw", require = 1)
    private static void bbspp$drawPicker(RenderPipeline pipeline, BuiltBuffer buffer, Matrix4f modelView,
                                       Operation<Void> original)
    {
        GpuTextureView colorOverride = RenderSystem.outputColorTextureOverride;
        GpuTextureView depthOverride = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = null;
        RenderSystem.outputDepthTextureOverride = null;

        try
        {
            // Model picking uses texture alpha so transparent overlays leave the underlying bone selectable.
            if (pipeline != BBSShaders.getPickerModelsProgram())
            {
                if (bbspp$ensureOpaqueSampler())
                {
                    BBSPickerRenderer.setSampler0(bbspp$whiteView, bbspp$nearest);
                }

                bbspp$forceOpaqueAlpha(buffer);
            }

            original.call(pipeline, buffer, modelView);
        }
        finally
        {
            RenderSystem.outputDepthTextureOverride = depthOverride;
            RenderSystem.outputColorTextureOverride = colorOverride;
        }
    }

    @Unique
    private static void bbspp$forceOpaqueAlpha(BuiltBuffer buffer)
    {
        BuiltBuffer.DrawParameters params = buffer.getDrawParameters();
        var format = params.format();
        var color = VertexFormatElement.COLOR;

        if (!format.contains(color))
        {
            return;
        }

        ByteBuffer vertices = buffer.getBuffer();
        int stride = format.getVertexSize();
        int alphaOffset = format.getOffset(color) + color.byteSize() - 1;

        for (int i = 0; i < params.vertexCount(); i++)
        {
            vertices.put(i * stride + alphaOffset, (byte) 0xFF);
        }
    }
}
