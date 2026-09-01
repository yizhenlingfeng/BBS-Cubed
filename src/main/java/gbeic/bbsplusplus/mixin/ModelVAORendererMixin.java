package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.render.ModelUVTransformRuntime;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 VAO 渲染路径的 shader uniform 设置阶段同步 UV 变换。
 * <p>
 * VAO 顶点数据是预烘焙的，不能每帧直接改数组；因此 BBS++ 给模型 shader 增加
 * {@code UVTransform} uniform，并在这里把当前表单参数传进去。
 * </p>
 */
@Mixin(value = ModelVAORenderer.class, remap = true)
public class ModelVAORendererMixin
{
    /**
     * 注入目标：{@code ModelVAORenderer#setupUniforms} 结束处。
     * 注入原因：原版只设置模型、投影、光照等 uniform，没有 BBS++ 的 UV 变换。
     * 修改行为：当当前 shader 声明了 {@code UVTransform} 时写入偏移和缩放参数。
     */
    @Inject(
        method = "setupUniforms(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/gl/ShaderProgram;)V",
        at = @At("TAIL"),
        require = 0
    )
    private static void bbspp$setupUvTransformUniform(MatrixStack stack, ShaderProgram shader, CallbackInfo ci)
    {
        ModelUVTransformRuntime.applyToShader(shader);
    }

    /**
     * 注入目标：新版 {@code ModelVAORenderer#setupUniforms} 结束处。
     * 注入原因：新版 FS 把真实 uniform 设置下沉到带模型视图矩阵和法线矩阵的重载里。
     * 修改行为：在新版 VAO 渲染实际使用的路径中同步 BBS++ 的 UV 变换 uniform。
     */
    @Inject(
        method = "setupUniforms(Lnet/minecraft/client/gl/ShaderProgram;Lorg/joml/Matrix4f;Lorg/joml/Matrix3f;)V",
        at = @At("TAIL"),
        require = 0
    )
    private static void bbspp$setupUvTransformUniformNew(ShaderProgram shader, Matrix4f modelView, Matrix3f normalMat, CallbackInfo ci)
    {
        ModelUVTransformRuntime.applyToShader(shader);
    }

    /**
     * 注入目标：{@code ModelVAORenderer#render} 解绑 shader 前。
     * 注入原因：混合渲染模型可能先画 VAO 骨骼，再画 CPU 顶点骨骼。
     * 修改行为：VAO 绘制结束后把 uniform 恢复默认值，避免后续 CPU 顶点路径重复应用 UV 变换。
     */
    @Inject(
        method = "render(Lnet/minecraft/client/gl/ShaderProgram;Lmchorse/bbs_mod/cubic/render/vao/IModelVAO;Lnet/minecraft/client/util/math/MatrixStack;FFFFII)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gl/ShaderProgram;unbind()V",
            shift = At.Shift.BEFORE
        ),
        require = 0
    )
    private static void bbspp$clearUvTransformUniform(ShaderProgram shader, mchorse.bbs_mod.cubic.render.vao.IModelVAO modelVAO, MatrixStack stack, float r, float g, float b, float a, int light, int overlay, CallbackInfo ci)
    {
        ModelUVTransformRuntime.clearShader(shader);
    }

    /**
     * 注入目标：新版 {@code ModelVAORenderer#render} 解绑 shader 前。
     * 注入原因：新版 FS 的旧 {@code MatrixStack} 重载只转发，真正的绘制和解绑发生在矩阵重载中。
     * 修改行为：绘制结束后恢复默认 UV uniform，避免后续绘制复用上一形态的 UV 变换。
     */
    @Inject(
        method = "render(Lnet/minecraft/client/gl/ShaderProgram;Lmchorse/bbs_mod/cubic/render/vao/IModelVAO;Lorg/joml/Matrix4f;Lorg/joml/Matrix3f;FFFFII)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gl/ShaderProgram;unbind()V",
            shift = At.Shift.BEFORE
        ),
        require = 0
    )
    private static void bbspp$clearUvTransformUniformNew(ShaderProgram shader, mchorse.bbs_mod.cubic.render.vao.IModelVAO modelVAO, Matrix4f modelView, Matrix3f normalMat, float r, float g, float b, float a, int light, int overlay, CallbackInfo ci)
    {
        ModelUVTransformRuntime.clearShader(shader);
    }
}
