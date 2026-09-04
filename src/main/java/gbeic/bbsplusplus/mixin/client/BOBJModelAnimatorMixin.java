package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.cubic.animation.AdditiveLayerContext;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.CubicModelAnimator;
import mchorse.bbs_mod.cubic.data.animation.AnimationPart;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModelAnimator;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BOBJ(骨骼网格)模型的 actions 附加层相加叠加,与 {@link CubicModelAnimatorMixin} 对称:
 * 当 {@link AdditiveLayerContext} 激活时,把 {@code applyGroupAnimation} 从覆盖改成
 * {@code current += delta * blend} 并 composeOrient 组合旋转四元数,让多个附加层像
 * pose/transform 那样彼此叠加而非越靠下越覆盖。
 *
 * <p>与 cubic 的差异:BOBJ 旋转以弧度存储且不对 x/y 取负(见 vanilla
 * {@code BOBJModelAnimator.postAnimate}),故这里用 {@code toQuaternionZYXRadians}
 * 且不翻转旋转分量。</p>
 */
@Mixin(value = BOBJModelAnimator.class, remap = false)
public abstract class BOBJModelAnimatorMixin
{
    @Inject(method = "applyGroupAnimation", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bbspp_cml$applyGroupAnimationAdditive(BOBJBone group, AnimationPart animation, float frame, float blend, CallbackInfo ci)
    {
        if (!AdditiveLayerContext.isActive())
        {
            return;
        }

        Vector3d position = CubicModelAnimator.interpolateList(new Vector3d(), animation.x, animation.y, animation.z, frame, 0D);
        Vector3d scale = CubicModelAnimator.interpolateList(new Vector3d(), animation.sx, animation.sy, animation.sz, frame, 1D);
        Vector3d rotation = CubicModelAnimator.interpolateList(new Vector3d(), animation.rx, animation.ry, animation.rz, frame, 0D);

        scale.sub(1, 1, 1);

        Transform current = group.transform;

        current.translate.x += (float) (position.x * blend);
        current.translate.y += (float) (position.y * blend);
        current.translate.z += (float) (position.z * blend);

        current.scale.x += (float) (scale.x * blend);
        current.scale.y += (float) (scale.y * blend);
        current.scale.z += (float) (scale.z * blend);

        float rx = (float) (rotation.x * blend);
        float ry = (float) (rotation.y * blend);
        float rz = (float) (rotation.z * blend);

        current.rotate.x += rx;
        current.rotate.y += ry;
        current.rotate.z += rz;

        group.composeOrient(Matrices.toQuaternionZYXRadians(rx, ry, rz));

        ci.cancel();
    }
}
