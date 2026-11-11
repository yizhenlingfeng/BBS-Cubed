package gbeic.bbsplusplus.client.ui.pose;

import mchorse.bbs_mod.graphics.GuiQuadMesh;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.IKeyframeShapeRenderer;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.KeyframeShapeRenderers;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeShape;
import org.joml.Matrix3x2fc;

/**
 * 使用 BBS 关键帧形状渲染器绘制骨骼列表中的修改标记。
 *
 * <p>外层采用轨道菱形的 3 单位尺寸，内层采用 2 单位尺寸并填充黑色，与关键帧编辑器中
 * 未选中菱形的双层画法一致；因此不再依赖尺寸偏大的图标贴图。</p>
 *
 * <p>1.21.11 的 GUI 改成了两阶段延迟合成：往 {@code DrawContext} 里记的图元要到帧末才由
 * {@code GuiRenderer} 一起画出来，直接在记录期间 {@code RenderLayer.draw(BuiltBuffer)} 会被合成覆盖
 * （关键帧编辑器曾因此整块不可见）。所以这里改走 {@link GuiQuadMesh} —— 它实现
 * {@code VertexConsumer}，把顶点记下来后交给 {@code Batcher2D.drawQuadMesh}，由 GUI 合成阶段统一提交。
 * 另外 {@link IKeyframeShapeRenderer#renderKeyframe} 的矩阵参数在 1.21.11 已从 {@code Matrix4f}
 * 换成 2D 的 {@code Matrix3x2fc}，与 {@code getMatrices()} 返回的 {@code Matrix3x2fStack} 直接兼容。</p>
 */
public class PoseBoneMarkerRenderer
{
    /** 绘制与关键帧外观一致的双色菱形。 */
    public static void renderModifiedDiamond(UIContext context, int x, int y, int color)
    {
        IKeyframeShapeRenderer diamond = KeyframeShapeRenderers.SHAPES.get(KeyframeShape.DIAMOND);

        if (diamond == null)
        {
            return;
        }

        GuiQuadMesh mesh = new GuiQuadMesh();
        Matrix3x2fc matrix = context.batcher.getContext().getMatrices();

        diamond.renderKeyframe(context, mesh, matrix, x, y, 3, color);
        diamond.renderKeyframe(context, mesh, matrix, x, y, 2, Colors.A100);

        context.batcher.drawQuadMesh(mesh);
    }

    /** 绘制表示“本帧不参与该骨骼插值”的灰色斜线菱形。 */
    public static void renderSkippedDiamond(UIContext context, int x, int y)
    {
        renderModifiedDiamond(context, x, y, Colors.opaque(0x888888));

        int slash = Colors.opaque(0xE0E0E0);

        for (int i = -2; i <= 2; i++)
        {
            context.batcher.box(x + i, y - i, x + i + 1, y - i + 1, slash);
        }
    }

    /** 绘制只在状态槽悬停时出现的弱提示菱形。 */
    public static void renderHoverDiamond(UIContext context, int x, int y)
    {
        renderModifiedDiamond(context, x, y, Colors.A25 | 0xC0C0C0);
    }

    private PoseBoneMarkerRenderer()
    {}
}
