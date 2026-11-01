package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.outline.OutlineConfigHolder;
import gbeic.bbsplusplus.client.outline.OutlineRenderer120;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.utils.FormMaterialLevels;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Hooks the outline shell around BBS's own cubic model draw.
 *
 * <p>Matches Cool_pack's two-pass layering: at HEAD the colour rim and the rim glow are drawn
 * with depth writes off, then BBS draws the body over them so overlapping outline pixels give way;
 * at RETURN the same shell is drawn once more with colour writes off but depth writes on, giving
 * the rim pixels outside the body real geometric depth for Iris deferred/composite. Picking
 * ({@code stencilMap != null}) and the Iris shadow pass are skipped: an outline must not affect
 * either.</p>
 */
@Mixin(value = ModelInstance.class, remap = false)
public abstract class ModelInstanceOutlineMixin
{
    @Shadow public IModel model;
    @Shadow public Link baseTexture;
    @Shadow public ModelConfig config;
    @Shadow public Form form;

    @Inject(method = "render", at = @At("HEAD"), remap = false, require = 0)
    private void bbspp$outlineColorAndGlow(MatrixStack stack, Supplier<ShaderProgram> program, Color color,
                                           int light, int overlay, StencilMap stencilMap, ShapeKeys keys,
                                           Function<String, Link> textureResolver, CallbackInfo ci)
    {
        if (stencilMap != null)
        {
            return;
        }

        bbspp$render(stack, color);
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false, require = 0)
    private void bbspp$outlineDepth(MatrixStack stack, Supplier<ShaderProgram> program, Color color,
                                    int light, int overlay, StencilMap stencilMap, ShapeKeys keys,
                                    Function<String, Link> textureResolver, CallbackInfo ci)
    {
        if (stencilMap != null)
        {
            return;
        }

        bbspp$renderDepth(stack, color);
    }

    private void bbspp$render(MatrixStack stack, Color color)
    {
        if (!(this.model instanceof Model model) || !(this.config instanceof OutlineConfigHolder outline))
        {
            return;
        }

        OutlineRenderer120.render(stack, model, this.config, outline, color,
            bbspp$texture(), bbspp$materialVisible());
    }

    private void bbspp$renderDepth(MatrixStack stack, Color color)
    {
        if (!(this.model instanceof Model model) || !(this.config instanceof OutlineConfigHolder outline))
        {
            return;
        }

        OutlineRenderer120.renderDepth(stack, model, this.config, outline, color,
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
