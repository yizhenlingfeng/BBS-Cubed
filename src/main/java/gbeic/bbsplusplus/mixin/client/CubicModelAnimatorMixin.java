package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.cubic.animation.AdditiveLayerContext;
import mchorse.bbs_mod.cubic.CubicModelAnimator;
import mchorse.bbs_mod.cubic.data.animation.AnimationPart;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复"动画转姿势关键帧"的角度回绕:{@code applyGroupAnimation} 用
 * {@code Lerps.lerpYaw} 混合旋转,它按最短路径把角度归一到 ±180°(-270°
 * 变 90°)。渲染上 mod 360 等价无所谓,但 UIReplaysEditorUtils 转关键帧
 * 走 blend=1 的 apply 后用 createPose() 读回欧拉角,连续旋转(0,-117,
 * -270,-475)被回绕成(0,-117,90,-115),再插值时转向就反了。
 *
 * <p>blend>=1 时 lerpYaw(a,b,1) 数学上就等于 b(差 360k),直接返回 b
 * 保留原始角度;blend<1(动画过渡混合)保持原最短路径行为不变。</p>
 *
 * <p>另外:当 {@link AdditiveLayerContext} 处于激活态(正在应用 actions
 * 附加层)时,把 {@code applyGroupAnimation} 从"覆盖"(current = delta +
 * initial)改成"相加"(current += delta * blend)并组合旋转四元数,使多个附加
 * 层像 pose/transform 附加层一样彼此叠加,而不是越靠下越覆盖。</p>
 */
@Mixin(value = CubicModelAnimator.class, remap = false)
public abstract class CubicModelAnimatorMixin
{
    @Redirect(
        method = "applyGroupAnimation",
        at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/utils/interps/Lerps;lerpYaw(DDD)D"),
        remap = false
    )
    private static double bbspp_cml$keepUnwrappedAtFullBlend(double a, double b, double t)
    {
        return t >= 1D ? b : Lerps.lerpYaw(a, b, t);
    }

    /**
     * 相加叠加路径:与 vanilla {@code applyGroupAnimationPost} 一致地把本层动画的
     * 位移/缩放/旋转增量累加到 current,并 composeOrient 组合旋转四元数;唯一区别是
     * 用调用方传入的 {@code blend}(附加层的淡入淡出/权重)缩放增量,从而保留权重控制。
     * 只在附加层应用期间生效,基础动作仍走原覆盖路径。
     */
    @Inject(method = "applyGroupAnimation", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bbspp_cml$applyGroupAnimationAdditive(ModelGroup group, AnimationPart animation, float frame, float blend, CallbackInfo ci)
    {
        if (!AdditiveLayerContext.isActive())
        {
            return;
        }

        Vector3d position = CubicModelAnimator.interpolateList(new Vector3d(), animation.x, animation.y, animation.z, frame, 0D);
        Vector3d scale = CubicModelAnimator.interpolateList(new Vector3d(), animation.sx, animation.sy, animation.sz, frame, 1D);
        Vector3d rotation = CubicModelAnimator.interpolateList(new Vector3d(), animation.rx, animation.ry, animation.rz, frame, 0D);

        scale.sub(1, 1, 1);

        rotation.x *= -1;
        rotation.y *= -1;

        Transform current = group.current;

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

        /* 与 vanilla applyGroupAnimationPost 一致:欧拉读回保留给 gizmo/IK,orient 四元数
         * 才是渲染真值,首层从累加后的欧拉角种子化,后续层以四元数相乘,避免欧拉极点翻转。 */
        group.composeOrient(Matrices.toQuaternionZYXDegrees(rx, ry, rz));

        ci.cancel();
    }
}
