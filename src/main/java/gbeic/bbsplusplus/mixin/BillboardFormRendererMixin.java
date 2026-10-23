package gbeic.bbsplusplus.mixin;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.renderers.BillboardFormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Quad;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/**
 * 在广告牌原生 UV 偏移与旋转基础上补充 U/V 缩放。
 * <p>
 * 绘制前重新计算四个 UV 顶点，把缩放插入原生变换矩阵；
 * 同时修正原版用纹理宽度计算 Y 旋转中心的问题。
 * </p>
 */
@Mixin(value = BillboardFormRenderer.class, remap = true)
public abstract class BillboardFormRendererMixin extends FormRenderer<BillboardForm>
{
    @Shadow @Final private static Quad uvQuad;
    @Shadow @Final private static Matrix4f matrix;

    private BillboardFormRendererMixin(BillboardForm form)
    {
        super(form);
    }

    /**
     * 注入目标：{@code BillboardFormRenderer#renderModel} 调用带透明延迟参数的 {@code renderQuad} 之前。
     * 注入原因：原生已生成 UV 四角，但没有缩放且非方形纹理的 Y 中心除数错误。
     * 修改行为：从裁剪区域重建 UV，并围绕正确中心应用缩放、偏移和旋转。
     */
    @Inject(
        method = "renderModel(Lnet/minecraft/client/render/VertexFormat;Ljava/util/function/Supplier;Lnet/minecraft/client/util/math/MatrixStack;IIIFZ)V",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/forms/renderers/BillboardFormRenderer;renderQuad(Lnet/minecraft/client/render/VertexFormat;Lmchorse/bbs_mod/graphics/texture/Texture;Ljava/util/function/Supplier;Lnet/minecraft/client/util/math/MatrixStack;IIIFZ)V",
            shift = At.Shift.BEFORE
        ),
        require = 1
    )
    private void bbspp$applyCompleteUvTransformNew(VertexFormat format, Supplier<ShaderProgram> shader, MatrixStack matrices, int overlay, int light, int overlayColor, float transition, boolean defer, CallbackInfo ci)
    {
        this.bbspp$applyCompleteUvTransform();
    }

    private void bbspp$applyCompleteUvTransform()
    {
        Link link = this.form.texture.get();
        Texture texture = link == null ? null : BBSModClient.getTextures().getTexture(link);

        if (texture == null)
        {
            return;
        }

        float width = Math.max(1F, texture.width);
        float height = Math.max(1F, texture.height);
        Vector4f crop = this.form.crop.get();
        float u1 = crop.x / width;
        float v1 = crop.y / height;
        float u2 = 1F - crop.z / width;
        float v2 = 1F - crop.w / height;

        uvQuad.p1.set(u1, v1, 0F);
        uvQuad.p2.set(u2, v1, 0F);
        uvQuad.p3.set(u1, v2, 0F);
        uvQuad.p4.set(u2, v2, 0F);

        mchorse.bbs_mod.settings.values.misc.ValueVector4f uvScale = gbeic.bbsplusplus.api.BBSPPFormValues.getUvScale(this.form);

        Vector4f scale = uvScale != null && uvScale.get() != null
            ? uvScale.get()
            : new Vector4f(1F);
        float centerU = (crop.x + width - crop.z) / 2F / width;
        float centerV = (crop.y + height - crop.w) / 2F / height;

        matrix.identity()
            .translate(centerU, centerV, 0F)
            .rotateZ(MathUtils.toRad(this.form.rotation.get()))
            .translate(this.form.offsetX.get() / width, this.form.offsetY.get() / height, 0F)
            .scale(scale.x, scale.y, 1F)
            .translate(-centerU, -centerV, 0F);

        uvQuad.transform(matrix);
    }
}
