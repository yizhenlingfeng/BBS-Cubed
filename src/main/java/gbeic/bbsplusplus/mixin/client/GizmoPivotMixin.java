package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.GizmoPivotTarget;
import gbeic.bbsplusplus.api.PivotHolder;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moves non-pose gizmos from the transformed origin to the configured pivot. */
@Mixin(value = Gizmo.class, remap = false)
public abstract class GizmoPivotMixin implements GizmoPivotTarget
{
    @Unique
    private UIPropTransform bbspp_cml$pivotTransform;

    @Override
    public void bbspp_cml$setPivotTransform(UIPropTransform transform)
    {
        this.bbspp_cml$pivotTransform = transform;
    }

    @Inject(method = {"render", "captureVisual"}, at = @At("HEAD"), remap = false)
    private void bbspp_cml$pushPivotedGizmo(MatrixStack stack, CallbackInfo ci)
    {
        stack.push();
        this.bbspp_cml$moveToPivot(stack);
    }

    @Inject(method = {"render", "captureVisual"}, at = @At("RETURN"), remap = false)
    private void bbspp_cml$popPivotedGizmo(MatrixStack stack, CallbackInfo ci)
    {
        stack.pop();
    }

    @Inject(method = "renderStencil", at = @At("HEAD"), remap = false)
    private void bbspp_cml$pushPivotedStencil(MatrixStack stack, StencilMap map, CallbackInfo ci)
    {
        stack.push();
        this.bbspp_cml$moveToPivot(stack);
    }

    @Inject(method = "renderStencil", at = @At("RETURN"), remap = false)
    private void bbspp_cml$popPivotedStencil(MatrixStack stack, StencilMap map, CallbackInfo ci)
    {
        stack.pop();
    }

    @Unique
    private void bbspp_cml$moveToPivot(MatrixStack stack)
    {
        if (this.bbspp_cml$pivotTransform == null)
        {
            return;
        }

        Transform transform = this.bbspp_cml$pivotTransform.getTransform();

        /* Pose matrices already store bind point + pose pivot. Applying it here again
         * would move the pose gizmo twice. */
        if (transform == null || transform instanceof PoseTransform)
        {
            return;
        }

        Vector3f pivot = ((PivotHolder) transform).bbspp_cml$getPivot();

        if (pivot.x == 0F && pivot.y == 0F && pivot.z == 0F)
        {
            return;
        }

        if (this.bbspp_cml$pivotTransform.isLocal())
        {
            stack.translate(
                pivot.x * transform.scale.x,
                pivot.y * transform.scale.y,
                pivot.z * transform.scale.z
            );
        }
        else
        {
            stack.translate(pivot.x, pivot.y, pivot.z);
        }
    }
}
