package gbeic.bbsplusplus.utils;

import gbeic.bbsplusplus.api.PivotHolder;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * cubic 骨骼"中心点"的成对平移（对齐 CML 消费方式：/16 像素单位、坐标不翻转）。
 * 在 rotate/scale 之前 +pivot、之后 -pivot —— 旋转缩放为恒等时相互抵消，
 * 即中心点只改旋转/缩放的参考点，不移动模型。
 *
 * <p>供 CubicCubeRenderer/CubicAxisRenderer 的 override 注入方法与
 * CubicMatrixRenderer 的 @Overwrite 共用。</p>
 */
public class CubicPivotTransformations
{
    public static void moveToPoseAnchor(MatrixStack stack, ModelGroup group)
    {
        Vector3f pivot = ((PivotHolder) group.current).bbspp_cml$getPivot();

        if (pivot.x != 0F || pivot.y != 0F || pivot.z != 0F)
        {
            stack.translate(pivot.x / 16F, pivot.y / 16F, pivot.z / 16F);
        }
    }

    public static void moveBackFromPoseAnchor(MatrixStack stack, ModelGroup group)
    {
        Vector3f pivot = ((PivotHolder) group.current).bbspp_cml$getPivot();

        if (pivot.x != 0F || pivot.y != 0F || pivot.z != 0F)
        {
            stack.translate(-pivot.x / 16F, -pivot.y / 16F, -pivot.z / 16F);
        }
    }

    /**
     * CubicMatrixRenderer stores the bone transform before ModelInstance adds the
     * model-space bind point. Append the pose pivot here so that later addition
     * resolves to bind point + pose pivot, i.e. the actual rotation center.
     */
    public static void moveCollectedMatrixToPoseAnchor(Matrix4f matrix, ModelGroup group)
    {
        Vector3f pivot = ((PivotHolder) group.current).bbspp_cml$getPivot();

        if (pivot.x != 0F || pivot.y != 0F || pivot.z != 0F)
        {
            matrix.translate(pivot.x / 16F, pivot.y / 16F, pivot.z / 16F);
        }
    }
}
