package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.PivotHolder;
import gbeic.bbsplusplus.api.TextureGradeHolder;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.utils.interps.AutoBezier;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.pose.Transform;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.MathUtils;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给 {@link Transform} 增加 CML 的"中心点"（pivot，旋转/缩放的中心偏移）字段，
 * 全链与 CML 对齐（含 "p" 序列化键，姿势/关键帧数据与 CML 互通）：
 *
 * <ul>
 *   <li>{@code setupMatrix} —— 核心消费:首个 rotate(Quaternion)(2.4 为 rotateX/Y/Z,
 *       2.5 统一为 createRotation() 的四元数)之前 translate(+pivot)、
 *       RETURN 之前 translate(-pivot)(createMatrix 内部走 setupMatrix,自动覆盖);</li>
 *   <li>{@code identity/copy/add} —— 置零 / 拷贝 / 相加；</li>
 *   <li>{@code lerp}（两参 + IInterp 版）与 {@code autoLerp} —— 逐分量插值，
 *       使 TRANSFORM/POSE 关键帧轨道的 pivot 正常插值；</li>
 *   <li>{@code equals/isDefault} —— pivot 非零不视为默认值/相等
 *       （isDefault 若误判 true，toData 会整体跳过导致 pivot 丢失）；</li>
 *   <li>{@code toData/fromData} —— "p" 键（CML 同键名）。</li>
 * </ul>
 *
 * <p>mixin 挂在基类 Transform，PoseTransform 等子类实例全部持有该字段，
 * cast 到 {@link PivotHolder} 恒安全。</p>
 */
@Mixin(value = Transform.class, remap = false)
public abstract class TransformMixin implements PivotHolder, TextureGradeHolder
{
    @Unique
    private final Vector3f bbspp_cml$pivot = new Vector3f();

    /** 渲染期纹理调色（挂在 Transform 上，供 group.current 使用；
     *  字段名与 PoseTransformMixin 的 bbspp_cml$textureTint 区分以避免子类字段冲突） */
    @Unique
    private final Color bbspp_cml$renderTextureTint = new Color(1F, 1F, 1F, 0F);

    /** 渲染期纹理白化强度（0-1） */
    @Unique
    private float bbspp_cml$renderTextureWhiten = 0F;

    @Override
    public Color bbspp_cml$getTextureTint()
    {
        return this.bbspp_cml$renderTextureTint;
    }

    @Override
    public float bbspp_cml$getTextureWhiten()
    {
        return this.bbspp_cml$renderTextureWhiten;
    }

    @Override
    public void bbspp_cml$setTextureTint(Color tint)
    {
        this.bbspp_cml$renderTextureTint.copy(tint);
    }

    @Override
    public void bbspp_cml$setTextureWhiten(float whiten)
    {
        this.bbspp_cml$renderTextureWhiten = MathUtils.clamp(whiten, 0F, 1F);
    }

    @Override
    public Vector3f bbspp_cml$getPivot()
    {
        return this.bbspp_cml$pivot;
    }

    @Inject(method = "identity()V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$identityPivot(CallbackInfo ci)
    {
        this.bbspp_cml$pivot.set(0F, 0F, 0F);
        this.bbspp_cml$renderTextureTint.set(0x00ffffff);
        this.bbspp_cml$renderTextureWhiten = 0F;
    }

    @Inject(method = "copy(Lmchorse/bbs_mod/utils/pose/Transform;)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$copyPivot(Transform transform, CallbackInfo ci)
    {
        this.bbspp_cml$pivot.set(((PivotHolder) transform).bbspp_cml$getPivot());

        /* 源为 TextureGradeHolder（PoseTransform 或带渲染期调色的 Transform）时，
         * 把调色/白化复制到当前 Transform 的渲染期字段，使动画器设置 group.current 时不丢失。
         * PoseTransform 的 getTextureTint() 返回其自身字段（PoseTransformMixin 覆盖），
         * 普通 Transform 的 getTextureTint() 返回渲染期字段（本 mixin）。 */
        if (transform instanceof TextureGradeHolder holder)
        {
            this.bbspp_cml$renderTextureTint.copy(holder.bbspp_cml$getTextureTint());
            this.bbspp_cml$renderTextureWhiten = holder.bbspp_cml$getTextureWhiten();
        }
    }

    @Inject(method = "add(Lmchorse/bbs_mod/utils/pose/Transform;)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$addPivot(Transform transform, CallbackInfo ci)
    {
        this.bbspp_cml$pivot.add(((PivotHolder) transform).bbspp_cml$getPivot());
    }

    @Inject(method = "lerp(Lmchorse/bbs_mod/utils/pose/Transform;F)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$lerpPivot(Transform transform, float a, CallbackInfo ci)
    {
        this.bbspp_cml$pivot.lerp(((PivotHolder) transform).bbspp_cml$getPivot(), a);
    }

    @Inject(
        method = "lerp(Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/interps/IInterp;F)V",
        at = @At("TAIL"),
        remap = false
    )
    private void bbspp_cml$lerpPivot(Transform preA, Transform a, Transform b, Transform postB, IInterp interp, float x, CallbackInfo ci)
    {
        Vector3f pa = ((PivotHolder) preA).bbspp_cml$getPivot();
        Vector3f va = ((PivotHolder) a).bbspp_cml$getPivot();
        Vector3f vb = ((PivotHolder) b).bbspp_cml$getPivot();
        Vector3f pb = ((PivotHolder) postB).bbspp_cml$getPivot();

        this.bbspp_cml$pivot.x = (float) interp.interpolate(IInterp.context.set(pa.x, va.x, vb.x, pb.x, x));
        this.bbspp_cml$pivot.y = (float) interp.interpolate(IInterp.context.set(pa.y, va.y, vb.y, pb.y, x));
        this.bbspp_cml$pivot.z = (float) interp.interpolate(IInterp.context.set(pa.z, va.z, vb.z, pb.z, x));
    }

    @Inject(
        method = "autoLerp(Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/pose/Transform;FFFFZF)V",
        at = @At("TAIL"),
        remap = false
    )
    private void bbspp_cml$autoLerpPivot(Transform preA, Transform a, Transform b, Transform postB, float pt, float at, float bt, float qt, boolean clamped, float x, CallbackInfo ci)
    {
        Vector3f pa = ((PivotHolder) preA).bbspp_cml$getPivot();
        Vector3f va = ((PivotHolder) a).bbspp_cml$getPivot();
        Vector3f vb = ((PivotHolder) b).bbspp_cml$getPivot();
        Vector3f pb = ((PivotHolder) postB).bbspp_cml$getPivot();

        this.bbspp_cml$pivot.x = (float) AutoBezier.get(pa.x, va.x, vb.x, pb.x, pt, at, bt, qt, clamped, x);
        this.bbspp_cml$pivot.y = (float) AutoBezier.get(pa.y, va.y, vb.y, pb.y, pt, at, bt, qt, clamped, x);
        this.bbspp_cml$pivot.z = (float) AutoBezier.get(pa.z, va.z, vb.z, pb.z, pt, at, bt, qt, clamped, x);
    }

    @Inject(method = "equals(Ljava/lang/Object;)Z", at = @At("RETURN"), cancellable = true, remap = false)
    private void bbspp_cml$equalsPivot(Object obj, CallbackInfoReturnable<Boolean> cir)
    {
        if (cir.getReturnValueZ() && obj instanceof PivotHolder holder && !this.bbspp_cml$pivot.equals(holder.bbspp_cml$getPivot()))
        {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isDefault()Z", at = @At("RETURN"), cancellable = true, remap = false)
    private void bbspp_cml$isDefaultPivot(CallbackInfoReturnable<Boolean> cir)
    {
        if (cir.getReturnValueZ() && this.bbspp_cml$isPivoted())
        {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "toData(Lmchorse/bbs_mod/data/types/MapType;)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$writePivot(MapType data, CallbackInfo ci)
    {
        if (this.bbspp_cml$isPivoted())
        {
            data.put("p", DataStorageUtils.vector3fToData(this.bbspp_cml$pivot));
        }
    }

    @Inject(method = "fromData(Lmchorse/bbs_mod/data/types/MapType;)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$readPivot(MapType data, CallbackInfo ci)
    {
        /* fromData 开头的 identity() 已把 pivot 清零，缺 "p" 键时无需处理 */
        if (data.has("p"))
        {
            this.bbspp_cml$pivot.set(DataStorageUtils.vector3fFromData(data.getList("p")));
        }
    }

    @Inject(
        method = "setupMatrix(Lorg/joml/Matrix4f;)Lorg/joml/Matrix4f;",
        at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;rotate(Lorg/joml/Quaternionfc;)Lorg/joml/Matrix4f;", ordinal = 0, remap = false),
        remap = false
    )
    private void bbspp_cml$pivotBeforeRotate(Matrix4f matrix, CallbackInfoReturnable<Matrix4f> cir)
    {
        if (this.bbspp_cml$isPivoted())
        {
            matrix.translate(this.bbspp_cml$pivot);
        }
    }

    @Inject(method = "setupMatrix(Lorg/joml/Matrix4f;)Lorg/joml/Matrix4f;", at = @At("RETURN"), remap = false)
    private void bbspp_cml$pivotAfterScale(Matrix4f matrix, CallbackInfoReturnable<Matrix4f> cir)
    {
        if (this.bbspp_cml$isPivoted())
        {
            matrix.translate(-this.bbspp_cml$pivot.x, -this.bbspp_cml$pivot.y, -this.bbspp_cml$pivot.z);
        }
    }

    /**
     * 2.5 新增的镜像:translate.x 取反时 pivot 同步取反,
     * 使 D·(T(p)·R·T(−p))·D = T(Dp)·(D·R·D)·T(−Dp) 保持镜像后旋转中心正确。
     */
    @Inject(method = "mirrorX()V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$mirrorPivot(CallbackInfo ci)
    {
        this.bbspp_cml$pivot.x = -this.bbspp_cml$pivot.x;
    }

    @Unique
    private boolean bbspp_cml$isPivoted()
    {
        return this.bbspp_cml$pivot.x != 0F || this.bbspp_cml$pivot.y != 0F || this.bbspp_cml$pivot.z != 0F;
    }
}
