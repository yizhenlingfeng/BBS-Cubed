package gbeic.bbsplusplus.ui.morphing;

import mchorse.bbs_mod.ui.framework.UIContext;

/**
 * 1.20.x 的二维矩阵栈适配器。
 *
 * <p>1.20.x 的 {@code getMatrices()} 返回 {@code MatrixStack}，平移与缩放都带 Z 轴分量。</p>
 */
final class CategoryIconMatrix
{
    private CategoryIconMatrix() {}

    static void push(UIContext context, int x, int y, float scale)
    {
        context.batcher.getContext().getMatrices().push();
        context.batcher.getContext().getMatrices().translate(x, y, 0);
        context.batcher.getContext().getMatrices().scale(scale, scale, 1F);
    }

    static void pop(UIContext context)
    {
        context.batcher.getContext().getMatrices().pop();
    }
}
