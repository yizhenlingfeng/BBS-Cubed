package gbeic.bbsplusplus.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;

/**
 * 1.21.11 的相机兜底矩阵适配器。
 *
 * <p>1.21.11 移除了 {@code RenderSystem.getInverseViewRotationMatrix()}，只剩
 * {@code getModelViewMatrix()}。该分支只在拿不到相机时兜底，用当前 model-view
 * 的旋转部分求逆即可。</p>
 */
final class ItemSprayCameraAdapter
{
    private ItemSprayCameraAdapter() {}

    static Matrix4f fallbackInverseViewRotation()
    {
        Matrix4f inverse = new Matrix4f(RenderSystem.getModelViewMatrix()).invert();

        inverse.setTranslation(0F, 0F, 0F);

        return inverse;
    }
}
