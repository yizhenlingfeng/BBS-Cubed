package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.PivotHolder;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * form/身体部位等 MatrixStack 变换路径的中心点消费：
 * {@link MatrixStackUtils#applyTransform} 手工展开 translate→rotate→scale
 * （不走 Transform.setupMatrix）。HEAD 注入 +pivot（平移可交换，等价于
 * translate 之后旋转之前）、TAIL 注入 -pivot —— 旋转缩放为恒等时抵消，
 * 中心点只改旋转/缩放参考点。单位与 setupMatrix 注入一致（世界单位）。
 */
@Mixin(value = MatrixStackUtils.class, remap = false)
public class MatrixStackUtilsMixin
{
    @Inject(method = "applyTransform", at = @At("HEAD"), remap = false)
    private static void bbspp_cml$pivotBefore(MatrixStack stack, Transform transform, CallbackInfo ci)
    {
        Vector3f pivot = ((PivotHolder) transform).bbspp_cml$getPivot();

        if (pivot.x != 0F || pivot.y != 0F || pivot.z != 0F)
        {
            stack.translate(pivot.x, pivot.y, pivot.z);
        }
    }

    @Inject(method = "applyTransform", at = @At("TAIL"), remap = false)
    private static void bbspp_cml$pivotAfter(MatrixStack stack, Transform transform, CallbackInfo ci)
    {
        Vector3f pivot = ((PivotHolder) transform).bbspp_cml$getPivot();

        if (pivot.x != 0F || pivot.y != 0F || pivot.z != 0F)
        {
            stack.translate(-pivot.x, -pivot.y, -pivot.z);
        }
    }
}
