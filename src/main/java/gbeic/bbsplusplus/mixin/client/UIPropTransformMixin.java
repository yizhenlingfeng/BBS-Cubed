package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.GizmoPivotTarget;
import gbeic.bbsplusplus.api.PivotHolder;
import gbeic.bbsplusplus.api.TransformPivotEditor;
import gbeic.bbsplusplus.settings.CMLSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link UIPropTransform} 的中心点接线：
 * <ul>
 *   <li>{@code setTransform} TAIL —— 把编辑目标的 pivot 填进中心点行；</li>
 *   <li>覆盖 {@code TransformPivotEditor.setP}（对齐 CML 的 UIPropTransform.setP）
 *       —— preCallback → 写 transform.pivot → postCallback，宿主的
 *       callbacks 机制（如伪装编辑的 valuePose 通知）自动生效。</li>
 * </ul>
 */
@Mixin(value = UIPropTransform.class, remap = false)
public abstract class UIPropTransformMixin implements TransformPivotEditor
{
    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void bbspp_cml$trackVisibleTransform(UIContext context, CallbackInfo ci)
    {
        UIPropTransform self = (UIPropTransform) (Object) this;

        if (self.getTransform() != null)
        {
            ((GizmoPivotTarget) (Object) Gizmo.INSTANCE).bbspp_cml$setPivotTransform(self);
        }
    }

    @Inject(method = "setTransform(Lmchorse/bbs_mod/utils/pose/Transform;)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$fillPivot(Transform transform, CallbackInfo ci)
    {
        if (transform == null || (CMLSettings.pivotTransform != null && !CMLSettings.pivotTransform.get()))
        {
            return;
        }

        Vector3f pivot = ((PivotHolder) transform).bbspp_cml$getPivot();

        this.bbspp_cml$fillP(pivot.x, pivot.y, pivot.z);
    }

    @Override
    public void bbspp_cml$setP(double x, double y, double z)
    {
        UIPropTransform self = (UIPropTransform) (Object) this;
        Transform transform = self.getTransform();

        if (transform == null)
        {
            return;
        }

        self.preCallback();
        ((PivotHolder) transform).bbspp_cml$getPivot().set((float) x, (float) y, (float) z);
        self.postCallback();
    }
}
