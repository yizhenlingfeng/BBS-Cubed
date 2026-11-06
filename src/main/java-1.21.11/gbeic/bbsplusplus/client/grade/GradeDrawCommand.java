package gbeic.bbsplusplus.client.grade;

import mchorse.bbs_mod.forms.FormRenderCapture;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 接管模型基础绘制的命令：把原 {@code RenderLayer.draw(built)} 换成走
 * {@link GradePass#drawBase} 的自定义 pass，从而让调色/白化真正进到片元里。
 *
 * <p>它和 {@link GlintDrawCommand} 一样继承 {@link FormTranslucentQueue.DrawCommand}，
 * 所以仍然参与「远到近」排序，不会破坏半透明模型的层次。
 *
 * <p>{@code overlay} 是入队时的逐骨骼调色板快照：真正绘制发生在帧末，那时 swatch 早被
 * 后面画的模型改写过了（上游 {@code BufferCommand} 为此专门带了一份快照）。
 */
public class GradeDrawCommand extends FormTranslucentQueue.DrawCommand
{
    private final FormRenderCapture.Captured captured;
    private final Matrix4f modelView;
    private final Identifier texture;
    private final GradeParams params;
    private final boolean cull;
    private final int[] overlay;
    private boolean failed;

    public GradeDrawCommand(Vector3f origin, FormRenderCapture.Captured captured, Matrix4f modelView, Identifier texture, GradeParams params, boolean cull, int[] overlay)
    {
        super(origin == null ? new Vector3f() : new Vector3f(origin));

        this.captured = captured;
        this.modelView = modelView;
        this.texture = texture;
        this.params = params;
        this.cull = cull;
        this.overlay = overlay;
    }

    @Override
    public void draw()
    {
        if (this.failed)
        {
            return;
        }

        try
        {
            this.applyOverlay();
            GradePass.drawBase(this.captured, this.modelView, this.texture, this.params, this.cull);
        }
        catch (Throwable t)
        {
            this.failed = true;

            gbeic.bbsplusplus.BBSPlusPlusMod.LOGGER.error("[BBS++] 纹理分级绘制失败（本帧之后跳过该条目）", t);
        }
    }
}
