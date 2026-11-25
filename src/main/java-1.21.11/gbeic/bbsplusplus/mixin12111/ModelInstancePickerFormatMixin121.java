package gbeic.bbsplusplus.mixin12111;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import gbeic.bbsplusplus.compat.IrisCompat;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.colors.Color;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Function;

@Mixin(value = ModelInstance.class, remap = false)
public class ModelInstancePickerFormatMixin121
{
    @WrapMethod(method = "render", require = 1)
    private void bbspp$usePickerVertexFormat(MatrixStack stack, Color color, int light, int overlay,
                                            StencilMap stencilMap, ShapeKeys keys, Function<String, Texture> textures,
                                            Operation<Void> original)
    {
        if (stencilMap == null || !IrisCompat.isLoaded())
        {
            original.call(stack, color, light, overlay, stencilMap, keys, textures);
            return;
        }

        // Iris selects the layouts for buffer creation and upload independently.
        boolean skipExtension = ImmediateState.skipExtension.get();
        boolean extendedFormat = ImmediateState.renderWithExtendedVertexFormat;
        ImmediateState.skipExtension.set(true);
        ImmediateState.renderWithExtendedVertexFormat = false;

        try
        {
            original.call(stack, color, light, overlay, stencilMap, keys, textures);
        }
        finally
        {
            ImmediateState.renderWithExtendedVertexFormat = extendedFormat;
            ImmediateState.skipExtension.set(skipExtension);
        }
    }
}
