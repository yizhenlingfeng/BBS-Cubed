package gbeic.bbsplusplus.mixin.morph;

import gbeic.bbsplusplus.client.morph.MorphRenderContext;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把 BBS 当前正在绘制的 ModelForm 暴露给 VAO 路径判定。
 *
 * <p>BBS 在选择模型着色器时会先查询 {@code ModelInstance.isVAORendered()}，之后才在 IK
 * 求值里写入共享的 {@code ModelInstance.form}。因此读取该共享字段会拿到上一次绘制的表单；
 * 这里在 ModelFormRenderer 的入口建立线程内作用域，避免同 ID 的其他表单影响当前渲染。</p>
 */
@Mixin(value = ModelFormRenderer.class, remap = true)
public abstract class ModelFormRendererMorphMixin
{
    @Shadow
    public abstract ModelForm getForm();

    @Inject(method = "renderInUI", at = @At("HEAD"), require = 1)
    private void bbsppp$beginUiForm(UIContext context, int x, int y, int width, int height, CallbackInfo ci)
    {
        MorphRenderContext.beginForm(this.getForm());
    }

    @Inject(method = "renderInUI", at = @At("RETURN"), require = 1)
    private void bbsppp$endUiForm(UIContext context, int x, int y, int width, int height, CallbackInfo ci)
    {
        MorphRenderContext.endForm();
    }

    @Inject(method = "render3D", at = @At("HEAD"), require = 1)
    private void bbsppp$beginWorldForm(FormRenderingContext context, CallbackInfo ci)
    {
        MorphRenderContext.beginForm(this.getForm());
    }

    @Inject(method = "render3D", at = @At("RETURN"), require = 1)
    private void bbsppp$endWorldForm(FormRenderingContext context, CallbackInfo ci)
    {
        MorphRenderContext.endForm();
    }

    @Inject(method = "renderArm", at = @At("HEAD"), require = 1)
    private void bbsppp$beginArmForm(MatrixStack stack, int light, AbstractClientPlayerEntity player, Hand hand,
                                     CallbackInfoReturnable<Boolean> cir)
    {
        MorphRenderContext.beginForm(this.getForm());
    }

    @Inject(method = "renderArm", at = @At("RETURN"), require = 1)
    private void bbsppp$endArmForm(MatrixStack stack, int light, AbstractClientPlayerEntity player, Hand hand,
                                   CallbackInfoReturnable<Boolean> cir)
    {
        MorphRenderContext.endForm();
    }

    @Inject(method = "renderFirstPersonHand", at = @At("HEAD"), require = 1)
    private void bbsppp$beginHandForm(MatrixStack stack, int light, Hand hand,
                                      CallbackInfoReturnable<Boolean> cir)
    {
        MorphRenderContext.beginForm(this.getForm());
    }

    @Inject(method = "renderFirstPersonHand", at = @At("RETURN"), require = 1)
    private void bbsppp$endHandForm(MatrixStack stack, int light, Hand hand,
                                    CallbackInfoReturnable<Boolean> cir)
    {
        MorphRenderContext.endForm();
    }
}
