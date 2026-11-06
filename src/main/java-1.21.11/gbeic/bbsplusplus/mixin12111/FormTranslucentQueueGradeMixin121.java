package gbeic.bbsplusplus.mixin12111;

import gbeic.bbsplusplus.client.grade.GradeSubmitHook;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import net.minecraft.client.render.BuiltBuffer;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 纹理分级在模型提交点接管绘制。
 *
 * <p>描述符里的 MC 类型写开发环境名（{@code BuiltBuffer}）即可：Loom 的 mixin 注解处理器会把
 * 选择器连同类型一起重映射，产物里自动变成 {@code submit(Lnet/minecraft/class_9801;…;Z)V}。
 * 所以**不要**再手写一份混淆名的孪生 mixin，否则两份在产物里同时生效、同一帧画两遍。
 *
 * <p><b>handler 的 static 必须与目标方法一致</b>：{@code FormTranslucentQueue.submit} 是
 * {@code public static void}（6 参与 7 参两个重载），所以 handler 必须是 {@code private static}。
 * 写成实例方法会抛 {@code InvalidInjectionException: non-static callback method ... targets a
 * static method}，而 {@code require = 0} 让它在构建阶段完全静默 —— 这条错误是 2026-11-04 00:11
 * 实机启动时才暴露的（当时直接崩在 {@code WorldRenderer.render}，因为 BBS 的
 * {@code WorldRenderer} mixin 触发了本类的类加载）。
 */
@Mixin(value = FormTranslucentQueue.class, remap = false)
public class FormTranslucentQueueGradeMixin121
{
    @Inject(
        method = "submit(Lnet/minecraft/client/render/BuiltBuffer;Lmchorse/bbs_mod/client/BBSShaders$ModelVariant;Lmchorse/bbs_mod/graphics/texture/Texture;FLmchorse/bbs_mod/ui/framework/elements/utils/StencilMap;Lorg/joml/Vector3f;Z)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private static void bbspp$submitGrade(BuiltBuffer built, BBSShaders.ModelVariant variant, Texture texture, float alpha, StencilMap stencilMap, Vector3f origin, boolean tinted, CallbackInfo info)
    {
        if (GradeSubmitHook.handle(built, variant, texture, alpha, stencilMap, origin, tinted))
        {
            info.cancel();
        }
    }

    /**
     * 只开附魔光效时，必须在原 {@code submit} 画完基础层之后再叠加光效。
     * 见 {@link GradeSubmitHook#afterSubmit()}：HEAD 里入队会跑到基础绘制前面，被整片覆盖。
     */
    @Inject(
        method = "submit(Lnet/minecraft/client/render/BuiltBuffer;Lmchorse/bbs_mod/client/BBSShaders$ModelVariant;Lmchorse/bbs_mod/graphics/texture/Texture;FLmchorse/bbs_mod/ui/framework/elements/utils/StencilMap;Lorg/joml/Vector3f;Z)V",
        at = @At("RETURN"),
        require = 0
    )
    private static void bbspp$afterSubmitGrade(BuiltBuffer built, BBSShaders.ModelVariant variant, Texture texture, float alpha, StencilMap stencilMap, Vector3f origin, boolean tinted, CallbackInfo info)
    {
        GradeSubmitHook.afterSubmit();
    }
}
