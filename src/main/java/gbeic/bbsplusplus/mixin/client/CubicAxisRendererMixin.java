package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.utils.CubicPivotTransformations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.CubicAxisRenderer;
import mchorse.bbs_mod.cubic.render.ICubicRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 {@link CubicAxisRenderer} 的骨骼坐标轴完整跟随 pose 中心点：骨骼
 * 变换围绕新中心旋转，同时 renderGroup 的轴原点也从 bind point 移到
 * bind point + pose pivot（与 CML 的 group.current.pivot 语义一致）。
 */
@Mixin(value = CubicAxisRenderer.class, remap = false)
public abstract class CubicAxisRendererMixin
{
    public void applyGroupTransformations(MatrixStack stack, ModelGroup group)
    {
        ICubicRenderer.translateGroup(stack, group);
        ICubicRenderer.moveToGroupPivot(stack, group);
        CubicPivotTransformations.moveToPoseAnchor(stack, group);
        ICubicRenderer.rotateGroup(stack, group);
        ICubicRenderer.scaleGroup(stack, group);
        CubicPivotTransformations.moveBackFromPoseAnchor(stack, group);
        ICubicRenderer.moveBackFromGroupPivot(stack, group);
    }

    @Inject(
        method = "renderGroup",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/util/math/MatrixStack;push()V",
            shift = At.Shift.AFTER,
            remap = true
        ),
        remap = true
    )
    private void bbspp_cml$moveAxisToPoseAnchor(BufferBuilder builder, MatrixStack stack, ModelGroup group, Model model, CallbackInfoReturnable<Boolean> cir)
    {
        CubicPivotTransformations.moveToPoseAnchor(stack, group);
    }
}
