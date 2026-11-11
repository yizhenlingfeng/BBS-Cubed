package gbeic.bbsplusplus.mixin12111;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import gbeic.bbsplusplus.api.BBSPPFormValues;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.renderers.BillboardFormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.misc.ValueVector4f;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Quad;
import com.mojang.blaze3d.vertex.VertexFormat;
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
 * 1.21.11 广告牌 UV 补全：在原生 UV 偏移/旋转之后、提交绘制之前，
 * 重新写入四角 UV 并插入 U/V 缩放。
 *
 * <p>1.20.x 的 {@code BillboardFormRendererMixin} 注入的是旧版
 * {@code renderModel(VertexFormat, Supplier, MatrixStack, int, int, int, float, boolean)}；
 * 1.21.11 的方法签名换成了带 {@link RenderPipeline} 与 {@code deferrable} 的九参版本，
 * 且 {@code uvQuad}/{@code matrix} 仍是私有静态字段，所以单独放一份 121 版本。</p>
 */
@Mixin(value = BillboardFormRenderer.class, remap = true)
public abstract class BillboardFormRendererMixin121 extends FormRenderer<BillboardForm>
{
    @Shadow @Final private static Quad uvQuad;
    @Shadow @Final private static Matrix4f matrix;

    private BillboardFormRendererMixin121(BillboardForm form)
    {
        super(form);
    }

    @Inject(
        method = "renderModel(Lnet/minecraft/client/render/VertexFormat;Ljava/util/function/Supplier;Lcom/mojang/blaze3d/pipeline/RenderPipeline;ZLnet/minecraft/client/util/math/MatrixStack;IIIF)V",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/forms/renderers/BillboardFormRenderer;renderQuad(Lnet/minecraft/client/render/VertexFormat;Lmchorse/bbs_mod/graphics/texture/Texture;Ljava/util/function/Supplier;Lcom/mojang/blaze3d/pipeline/RenderPipeline;ZLnet/minecraft/client/util/math/MatrixStack;IIIF)V",
            shift = At.Shift.BEFORE
        ),
        require = 0
    )
    private void bbspp$applyCompleteUvTransform121(
        VertexFormat format,
        Supplier<?> shader,
        RenderPipeline picker,
        boolean deferrable,
        MatrixStack matrices,
        int overlay,
        int light,
        int overlayColor,
        float transition,
        CallbackInfo ci
    )
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

        ValueVector4f uvScale = BBSPPFormValues.getUvScale(this.form);
        Vector4f scale = uvScale != null && uvScale.get() != null ? uvScale.get() : new Vector4f(1F);

        /* 1.21.11 原生这里用 texture.width 当 Y 中心除数（应为 height）；
         * 重新按正确宽高计算，Y 旋转中心才不偏。 */
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
