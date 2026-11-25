package gbeic.bbsplusplus.ui.morphing;

import mchorse.bbs_mod.ui.framework.UIContext;

/**
 * 1.21.11 的二维矩阵栈适配器。
 *
 * <p>1.21.11 的 {@code getMatrices()} 返回二维矩阵栈，平移与缩放不带 Z 轴分量，
 * 入栈出栈方法也改名为 {@code pushMatrix()} 与 {@code popMatrix()}。</p>
 */
final class CategoryIconMatrix
{
    private CategoryIconMatrix() {}

    static void push(UIContext context, int x, int y, float scale)
    {
        var matrices = context.batcher.getContext().getMatrices();

        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
    }

    static void pop(UIContext context)
    {
        context.batcher.getContext().getMatrices().popMatrix();
    }
}
