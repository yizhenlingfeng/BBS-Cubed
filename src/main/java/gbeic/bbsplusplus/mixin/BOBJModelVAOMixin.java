package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.render.ModelUVTransformRuntime;
import mchorse.bbs_mod.cubic.render.vao.BOBJModelVAO;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 BOBJ 模型 VAO 绘制结束后恢复 UV shader uniform。
 * <p>
 * BOBJ 每个网格单独绘制，绘制前会复用 {@code ModelVAORenderer#setupUniforms} 写入
 * BBS++ 的 UV 变换；结束后恢复默认值，避免同一 shader 的后续绘制继承上一个模型的参数。
 * </p>
 */
@Mixin(value = BOBJModelVAO.class, remap = false)
public class BOBJModelVAOMixin
{
    /**
     * 注入目标：{@code BOBJModelVAO#render} 解绑 shader 前。
     * 注入原因：BOBJ 渲染直接管理 shader 绑定，不经过 {@code ModelVAORenderer#render}。
     * 修改行为：当前网格绘制完成后把 {@code UVTransform} 恢复为默认值。
     */
    @Inject(
        method = "render(Lnet/minecraft/client/gl/ShaderProgram;Lnet/minecraft/client/util/math/MatrixStack;FFFFLmchorse/bbs_mod/ui/framework/elements/utils/StencilMap;II)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gl/ShaderProgram;unbind()V",
            shift = At.Shift.BEFORE
        ),
        require = 0
    )
    private void bbspp$clearUvTransformUniform(ShaderProgram shader, MatrixStack stack, float r, float g, float b, float a, StencilMap stencilMap, int light, int overlay, CallbackInfo ci)
    {
        ModelUVTransformRuntime.clearShader(shader);
    }

    /**
     * 注入目标：新版 {@code BOBJModelVAO#render} 解绑 shader 前。
     * 注入原因：新版 FS 将真实绘制移动到带模型视图矩阵和法线矩阵的重载，旧 {@code MatrixStack} 重载只负责转发。
     * 修改行为：当前 BOBJ 网格绘制完成后恢复默认 UV uniform，避免同一 shader 的后续绘制继承上一个模型的参数。
     */
    @Inject(
        method = "render(Lnet/minecraft/client/gl/ShaderProgram;Lorg/joml/Matrix4f;Lorg/joml/Matrix3f;FFFFLmchorse/bbs_mod/ui/framework/elements/utils/StencilMap;II)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gl/ShaderProgram;unbind()V",
            shift = At.Shift.BEFORE
        ),
        require = 0
    )
    private void bbspp$clearUvTransformUniformNew(ShaderProgram shader, Matrix4f modelView, Matrix3f normalMat, float r, float g, float b, float a, StencilMap stencilMap, int light, int overlay, CallbackInfo ci)
    {
        ModelUVTransformRuntime.clearShader(shader);
    }
}
