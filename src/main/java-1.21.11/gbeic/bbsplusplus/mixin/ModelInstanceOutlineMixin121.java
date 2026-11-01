package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.outline.OutlineConfigHolder;
import gbeic.bbsplusplus.client.outline.OutlineRenderer121;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.utils.FormMaterialLevels;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 1.21.11 twin of {@link ModelInstanceOutlineMixin}: same Cool_pack layering — colour at HEAD,
 * depth-only at RETURN. Glow groups use the solid fallback on this target until the 1.21.11
 * LabPBR entity layer is ported. The host method lost its {@code Supplier<ShaderProgram>} argument
 * when the immediate shader path was removed. Registered only for the 1.21.11 build; the 1.20.x
 * mixin and this one select different {@code render} descriptors.
 *
 * <p>Note: 1.21.11 world bodies are deferred through {@code FormTranslucentQueue}; when that queue
 * is active this HEAD hook runs before the end-of-frame body flush, and the RETURN depth pass still
 * runs before it too. UI previews and the immediate path follow the exact Cool_pack order.</p>
 */
@Mixin(value = ModelInstance.class, remap = false)
public abstract class ModelInstanceOutlineMixin121
{
    @Shadow public IModel model;
    @Shadow public Link baseTexture;
    @Shadow public ModelConfig config;
    @Shadow public Form form;

    @Inject(method = "render", at = @At("HEAD"), remap = false, require = 0)
    private void bbspp$outlineColor(MatrixStack stack, Color color, int light, int overlay, StencilMap stencilMap,
                                    ShapeKeys keys, Function<String, Texture> textureResolver, CallbackInfo ci)
    {
        if (stencilMap != null)
        {
            return;
        }

        bbspp$renderColor(stack, color);
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false, require = 0)
    private void bbspp$outlineDepth(MatrixStack stack, Color color, int light, int overlay, StencilMap stencilMap,
                                    ShapeKeys keys, Function<String, Texture> textureResolver, CallbackInfo ci)
    {
        if (stencilMap != null)
        {
            return;
        }

        bbspp$renderDepth(stack, color);
    }

    private void bbspp$renderColor(MatrixStack stack, Color color)
    {
        if (!(this.model instanceof Model model) || !(this.config instanceof OutlineConfigHolder outline))
        {
            return;
        }

        OutlineRenderer121.render(stack, model, this.config, outline, color,
            bbspp$texture(), bbspp$materialVisible());
    }

    private void bbspp$renderDepth(MatrixStack stack, Color color)
    {
        if (!(this.model instanceof Model model) || !(this.config instanceof OutlineConfigHolder outline))
        {
            return;
        }

        OutlineRenderer121.renderDepth(stack, model, this.config, outline, color,
            bbspp$texture(), bbspp$materialVisible());
    }

    private Link bbspp$texture()
    {
        Link texture = this.config.getTexture();

        return texture == null ? this.baseTexture : texture;
    }

    private Predicate<String> bbspp$materialVisible()
    {
        if (this.form instanceof ModelForm modelForm)
        {
            return material -> FormMaterialLevels.materialVisible(modelForm, material);
        }

        return material -> true;
    }
}
