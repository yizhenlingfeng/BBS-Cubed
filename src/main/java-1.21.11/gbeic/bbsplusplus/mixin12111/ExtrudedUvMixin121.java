package gbeic.bbsplusplus.mixin12111;

import com.mojang.blaze3d.vertex.VertexFormat;
import gbeic.bbsplusplus.api.BBSPPFormValues;
import gbeic.bbsplusplus.api.ModelVAODataAccess;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAOData;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.forms.renderers.ExtrudedFormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.misc.ValueVector4f;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.21.11 挤出形态动态 UV（偏移 / 缩放 / 旋转）。
 *
 * <p>1.20.x 的做法是重定向 {@code ModelVAORenderer.render(ShaderProgram, IModelVAO, ...)}，然后
 * 用 {@code RenderSystem.setShader} + {@code BufferRenderer.drawWithGlobalProgram} 直接提交。
 * 1.21.11 这两条 API 都没了：几何体本身已经是 CPU 数组（{@code ModelVAO.data}），绘制入口变成
 * {@code ModelVAORenderer.render(ModelVAO, MatrixStack, f,f,f,f, i,i, boolean)}，由它自己塞进
 * {@code FormTranslucentQueue} 排队。</p>
 *
 * <p>因此这里只在 UV 变换生效时接管：读 {@link ModelVAODataAccess} 暴露的原始三角形，逐顶点
 * 改写 UV 后按同样的 {@code ModelVariant} 提交给 {@code FormTranslucentQueue}，半透明排序、
 * Iris 镜像管线、纹理绑定全部沿用上游路径，不碰任何 GL 状态。</p>
 */
@Mixin(value = ExtrudedFormRenderer.class, remap = false)
public abstract class ExtrudedUvMixin121 extends FormRenderer<ExtrudedForm>
{
    private ExtrudedUvMixin121(ExtrudedForm form)
    {
        super(form);
    }

    @Redirect(
        method = "renderModel",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/cubic/render/vao/ModelVAORenderer;render(Lmchorse/bbs_mod/cubic/render/vao/ModelVAO;Lnet/minecraft/client/util/math/MatrixStack;FFFFIIZ)V"
        ),
        require = 0
    )
    private void bbspp$renderWithUvTransform(ModelVAO modelVao, MatrixStack matrices, float r, float g, float b, float a, int light, int overlay, boolean cull)
    {
        ValueVector4f uvTransform = BBSPPFormValues.getUvTransform(this.form);
        ValueFloat uvRotation = BBSPPFormValues.getUvRotation(this.form);

        if (uvTransform == null
            || !(modelVao instanceof ModelVAODataAccess access)
            || !bbspp$isActive(uvTransform, uvRotation))
        {
            ModelVAORenderer.render(modelVao, matrices, r, g, b, a, light, overlay, cull);

            return;
        }

        ModelVAOData data = access.bbspp$getModelVaoData();

        if (data == null)
        {
            ModelVAORenderer.render(modelVao, matrices, r, g, b, a, light, overlay, cull);

            return;
        }

        this.bbspp$submitTransformed(data, matrices, r, g, b, a, light, overlay, cull, uvTransform, uvRotation);
    }

    private void bbspp$submitTransformed(ModelVAOData data, MatrixStack matrices, float r, float g, float b, float a, int light, int overlay, boolean cull, ValueVector4f uvTransform, ValueFloat uvRotation)
    {
        Matrix4f position = matrices.peek().getPositionMatrix();
        Matrix3f normalMatrix = matrices.peek().getNormalMatrix();

        float[] vertices = data.vertices();
        float[] normals = data.normals();
        float[] texCoords = data.texCoords();

        /* 与 1.20.x 的挤出路径同一套数学：偏移量按贴图像素折算成归一化 UV，围绕 (0.5, 0.5) 旋转。 */
        Vector4f transform = uvTransform.get() == null ? new Vector4f(0F, 0F, 1F, 1F) : uvTransform.get();
        Link link = this.form.texture.get();
        Texture texture = link == null ? null : BBSModClient.getTextures().getTexture(link);
        float width = texture == null ? 1F : Math.max(1F, texture.width);
        float height = texture == null ? 1F : Math.max(1F, texture.height);
        float radians = (float) Math.toRadians(uvRotation.get());
        float cosine = (float) Math.cos(radians);
        float sine = (float) Math.sin(radians);

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);

        Vector4f vertex = new Vector4f();
        Vector3f normal = new Vector3f();
        int lu = light & 0xffff;
        int lv = light >> 16 & 0xffff;
        int count = vertices.length / 3;

        for (int i = 0; i < count; i++)
        {
            vertex.set(vertices[i * 3], vertices[i * 3 + 1], vertices[i * 3 + 2], 1F);
            position.transform(vertex);

            normal.set(normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2]);
            normalMatrix.transform(normal);

            float localU = (texCoords[i * 2] - 0.5F) * transform.z + transform.x / width;
            float localV = (texCoords[i * 2 + 1] - 0.5F) * transform.w + transform.y / height;
            float u = 0.5F + cosine * localU - sine * localV;
            float v = 0.5F + sine * localU + cosine * localV;

            builder.vertex(vertex.x, vertex.y, vertex.z)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(overlay)
                .light(lu, lv)
                .normal(normal.x, normal.y, normal.z);
        }

        BuiltBuffer built = builder.endNullable();

        if (built == null)
        {
            return;
        }

        FormTranslucentQueue.submit(
            built,
            new BBSShaders.ModelVariant(FormTranslucentQueue.PASS_SINGLE, true, cull),
            BBSModClient.getTextures().getLastBound(),
            a,
            null,
            ModelVAORenderer.captureModelView(matrices).getTranslation(new Vector3f())
        );
    }

    private static boolean bbspp$isActive(ValueVector4f uv, ValueFloat rotation)
    {
        Vector4f value = uv.get() == null ? new Vector4f(0F, 0F, 1F, 1F) : uv.get();

        return Math.abs(value.x) > 1.0E-6F
            || Math.abs(value.y) > 1.0E-6F
            || Math.abs(value.z - 1F) > 1.0E-6F
            || Math.abs(value.w - 1F) > 1.0E-6F
            || Math.abs(rotation.get()) > 1.0E-6F;
    }
}
