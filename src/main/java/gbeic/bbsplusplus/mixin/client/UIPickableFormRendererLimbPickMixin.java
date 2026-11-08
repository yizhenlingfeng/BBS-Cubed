package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.client.ui.picking.LimbPickMode;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.forms.editors.utils.UIPickableFormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.GizmoInteraction;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.utils.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 肢体选择模式在表单预览里的表现：按住时隐藏命中框与操控器的读数叠加层，地面网格保持可见。
 *
 * <p>操控器本体与坐标轴由 {@code UIBaseMenuLimbPickMixin} 统一隐藏，着色器切换见
 * {@code ModelFormRendererLimbPickMixin}。右键在这里接管为「按骨骼覆盖选中」（BBS 原本右键不拾取）；
 * 正在点选骨骼或动画态编辑器可见时不接管，那时右键分别用于取消拾取与插入关键帧。</p>
 */
@Mixin(value = UIPickableFormRenderer.class, remap = false)
public class UIPickableFormRendererLimbPickMixin
{
    @Shadow
    public UIFormEditor formEditor;

    @Shadow
    private StencilFormFramebuffer stencil;

    /** 这一帧是否处于肢体选择状态（由 {@code render} 头部算出，供各 Redirect 复用） */
    @Unique
    private boolean bbspp$limbPickActive;

    @Inject(method = "subMouseClicked", at = @At("HEAD"), cancellable = true)
    private void bbspp$limbPickRightClick(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        if (context.mouseButton != 1 || !LimbPickMode.held(context))
        {
            return;
        }

        UIFormEditor editor = this.formEditor;

        if (editor == null || editor.isBonePicking())
        {
            return;
        }

        if (editor.statesEditor != null && editor.statesEditor.isVisible())
        {
            return;
        }

        StencilFormFramebuffer stencil = this.stencil;

        if (stencil != null && stencil.hasPicked())
        {
            Pair<Form, String> pair = stencil.getPicked();

            if (pair != null && pair.a != null && pair.b != null && !pair.b.isEmpty())
            {
                editor.pickFormFromRenderer(pair);
            }
        }

        cir.setReturnValue(true);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void bbspp$limbPickRenderHead(UIContext context, CallbackInfo ci)
    {
        this.bbspp$limbPickActive = LimbPickMode.held(context);
    }

    /** 命中框：按住时不绘制，免得挡住肢体本身 */
    @Redirect(
        method = "renderUserModel",
        at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/settings/values/numeric/ValueBoolean;get()Ljava/lang/Object;")
    )
    private Object bbspp$limbPickHideHitbox(ValueBoolean hitbox)
    {
        if (this.bbspp$limbPickActive)
        {
            return Boolean.FALSE;
        }

        return hitbox.get();
    }

    @Redirect(
        method = "render",
        at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/ui/utils/GizmoInteraction;renderSphereHighlight(Lmchorse/bbs_mod/ui/framework/UIContext;)V")
    )
    private void bbspp$limbPickHideSphereHighlight(GizmoInteraction gizmo, UIContext context)
    {
        if (!this.bbspp$limbPickActive)
        {
            gizmo.renderSphereHighlight(context);
        }
    }

    @Redirect(
        method = "render",
        at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/ui/utils/GizmoInteraction;renderReadout(Lmchorse/bbs_mod/ui/framework/UIContext;)V")
    )
    private void bbspp$limbPickHideReadout(GizmoInteraction gizmo, UIContext context)
    {
        if (!this.bbspp$limbPickActive)
        {
            gizmo.renderReadout(context);
        }
    }
}
