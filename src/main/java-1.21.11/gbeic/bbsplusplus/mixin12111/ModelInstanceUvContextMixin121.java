package gbeic.bbsplusplus.mixin12111;

import gbeic.bbsplusplus.client.grade.GradeContext;
import gbeic.bbsplusplus.client.grade.GradeParams;
import gbeic.bbsplusplus.client.grade.ModelGradeResolver;
import gbeic.bbsplusplus.client.renderer.ModelUVTransformRuntime;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

/**
 * 为 1.21.11 的 CPU 顶点渲染路径建立「当前模型」上下文：UV 变换、纹理分级参数、附魔光效。
 *
 * <p>1.20.x 里这段 push/pop 由 {@code ModelFormRendererEquipmentMixin}（UV）与
 * {@code CubicVAORendererMixin}（光效）负责，但两者在 1.21.11 都被排除（它们引用了
 * 旧渲染 API），导致 {@link ModelUVTransformRuntime#current()} 永远返回默认值、
 * 纹理分级与光效参数永远为空 —— 表现就是「纹理变换 / 调色 / 白化 / 附魔光效」面板
 * 能改能存、画面上完全没反应。
 * </p>
 *
 * <p>1.21.11 的 {@code ModelInstance#render} 是立方体模型唯一的 CPU 绘制入口，在这里
 * push/pop 等价于 1.20 的注入点，并且同时覆盖世界渲染与 UI 内的 3D 预览。
 * </p>
 */
@Mixin(value = ModelInstance.class, remap = false)
public class ModelInstanceUvContextMixin121
{
    @Inject(method = "render", at = @At("HEAD"))
    private void bbspp$pushRenderContext(MatrixStack stack, Color color, int light, int overlay, StencilMap stencilMap, ShapeKeys keys, Function<String, Texture> textureResolver, CallbackInfo info)
    {
        ModelInstance self = (ModelInstance) (Object) this;
        Form form = self.form;
        boolean modelForm = form instanceof ModelForm;

        ModelUVTransformRuntime.push(modelForm ? (ModelForm) form : null, self);

        GradeParams grade = modelForm ? ModelGradeResolver.resolve(self) : GradeParams.NONE;

        GradeContext.push(grade);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void bbspp$popRenderContext(MatrixStack stack, Color color, int light, int overlay, StencilMap stencilMap, ShapeKeys keys, Function<String, Texture> textureResolver, CallbackInfo ci)
    {
        GradeContext.pop();
        ModelUVTransformRuntime.pop();
    }
}
