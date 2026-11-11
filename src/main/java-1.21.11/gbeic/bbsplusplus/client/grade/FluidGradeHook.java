package gbeic.bbsplusplus.client.grade;

import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.forms.FormRenderCapture;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.renderers.utils.FormOverlay;
import mchorse.bbs_mod.graphics.texture.Texture;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 流体表单的纹理调色/白化接管。
 *
 * <p>为什么单独一份：{@link GradeSubmitHook} 挂在 {@code FormTranslucentQueue.submit} 上，而
 * 流体走的是自己 {@code Tessellator} → {@code RenderLayer.draw()} 的直通路径（见
 * {@code FluidFormRenderer#renderFluid}），从来不进那个队列。1.20.x 时代这里靠
 * {@code RenderSystem.setShader(() -> TextureGradeRenderContext.select(...))} 换 shader，
 * 1.21.11 没有全局 shader 可换了，只能像模型那样自己开 pass。</p>
 *
 * <p>顶点格式与 {@link GradePass#drawBase} 声明的一致
 * （{@code POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL}），因此快照可以原样喂给分级 pass；
 * 流体的 DrawMode 是 TRIANGLES 而分级 pipeline 声明的是 QUADS，这没问题 ——
 * {@code drawBase} 用快照自带的 mode 取索引缓冲。</p>
 *
 * <p>和模型那条一样仍然入 {@link FormTranslucentQueue}：开启半透明队列时流体也要参与
 * 「远到近」排序，否则水面会盖在模型前面。</p>
 */
public final class FluidGradeHook
{
    private FluidGradeHook()
    {
    }

    /**
     * @param built   {@code renderFluid} 刚结束的顶点缓冲
     * @param stack   当前矩阵栈（用来算模型视图与排序原点）
     * @param texture 这次绘制实际绑定的纹理（{@code getLastBound()}）
     * @param params  由 {@link ModelGradeResolver#resolveTint} 从表单值算出的分级参数
     *
     * @return {@code true} 表示已接替绘制，调用方**不要**再画原缓冲。
     */
    public static boolean handle(BuiltBuffer built, MatrixStack stack, Texture texture, GradeParams params)
    {
        if (built == null || stack == null || params == null || !params.needBase())
        {
            return false;
        }

        /* 顶点布局不同就是完全不同的管线，硬接管会把 32 字节的顶点按 56 字节读。 */
        if (built.getDrawParameters().format() != VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL)
        {
            return false;
        }

        Identifier textureId = GradePass.adopt(texture);

        if (textureId == null)
        {
            return false;
        }

        /* 预检：管线建不起来就让调用方走原 layer，否则流体会整块消失。 */
        if (!GradePass.readyBase())
        {
            return false;
        }

        FormRenderCapture.Captured captured = FormRenderCapture.copy(built);

        if (captured == null || captured.params().vertexCount() <= 0)
        {
            return false;
        }

        Matrix4f modelView = ModelVAORenderer.captureModelView(stack);
        Vector3f origin = modelView.getTranslation(new Vector3f());
        int[] overlay = FormOverlay.hasPalette() ? FormOverlay.snapshot() : null;

        /* 流体在 1.20.x 里是 disableCull 的（双面水面），BBS 的 getBoundModelLayer() 也是
         * cull = false，这里保持一致。 */
        FormTranslucentQueue.add(new GradeDrawCommand(origin, captured, modelView, textureId, params, false, overlay));

        /* 原缓冲由这里关掉：调用方不会再 draw 它。 */
        built.close();

        return true;
    }
}
