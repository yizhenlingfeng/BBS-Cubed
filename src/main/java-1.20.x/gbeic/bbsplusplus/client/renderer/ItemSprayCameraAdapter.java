package gbeic.bbsplusplus.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;

/**
 * 1.20.x 的相机兜底矩阵适配器。
 *
 * <p>1.20.x 仍提供 {@code RenderSystem.getInverseViewRotationMatrix()}，直接复用即可。</p>
 */
final class ItemSprayCameraAdapter
{
    private ItemSprayCameraAdapter() {}

    static Matrix4f fallbackInverseViewRotation()
    {
        return new Matrix4f(RenderSystem.getInverseViewRotationMatrix());
    }
}
