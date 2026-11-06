package gbeic.bbsplusplus.client.grade;

import mchorse.bbs_mod.forms.FormRenderCapture;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 叠加一层附魔光效的绘制命令（ADDITIVE，不写深度）。
 *
 * <p>必须在基础绘制之后执行：同一 origin 的两条命令按插入顺序重放（队列排序是稳定的），
 * 所以调用方先 {@code add} 基础命令、再 {@code add} 本命令。
 */
public class GlintDrawCommand extends FormTranslucentQueue.DrawCommand
{
    private final FormRenderCapture.Captured captured;
    private final Matrix4f modelView;
    private final Identifier texture;
    private final GradeParams params;
    private final int[] overlay;
    private boolean failed;

    public GlintDrawCommand(Vector3f origin, FormRenderCapture.Captured captured, Matrix4f modelView, Identifier texture, GradeParams params, int[] overlay)
    {
        super(origin == null ? new Vector3f() : new Vector3f(origin));

        this.captured = captured;
        this.modelView = modelView;
        this.texture = texture;
        this.params = params;
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
            GradePass.drawGlint(this.captured, this.modelView, this.texture, this.params);
        }
        catch (Throwable t)
        {
            this.failed = true;

            gbeic.bbsplusplus.BBSPlusPlusMod.LOGGER.error("[BBS++] 附魔光效绘制失败（本帧之后跳过该条目）", t);
        }
    }
}
