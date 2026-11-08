package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.client.ui.picking.LimbPickMode;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.forms.editors.utils.UIPickableFormRenderer;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
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
 * 肢体选择模式在表单预览里的表现。
 *
 * <p>按住「肢体选择」按键时隐藏操控器、坐标轴与命中框（模型的着色器切换见
 * {@code ModelFormRendererLimbPickMixin}）。预览地面网格保持可见，它是判断模型站位的参照；
 * 控件层级也不做任何改动，UI 面板始终盖在模型之上。</p>
 *
 * <p>鼠标键分工：左键走 BBS 原有的拾取（按住按键时模板缓冲区记录的就是实际肢体），
 * 右键在这里接管，用骨骼覆盖映射后的结果去选中。动画态编辑器可见时不接管右键——那条路径
 * 本来就靠右键插入关键帧，交给 BBS 自己的处理即可。</p>
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

    /**
     * 右键 = 按骨骼覆盖选取。
     *
     * <p>BBS 原本右键不拾取（只用来取消骨骼拾取），所以这里自己读取模板缓冲区的
     * 拾取结果，再交给编辑器走正常的选中流程；正在「点选骨骼」时不接管，好让右键
     * 仍然能取消。</p>
     *
     * <p>动画态编辑器可见时不接管：那时右键的语义是「插入关键帧」，交给
     * {@code UIReplaysEditorUtils#pickFormWithOffers} 的原生流程更合适。</p>
     */
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

    /**
     * 操控器的模板缓冲绘制：按住肢体选择按键时一并隐藏。
     */
    @Redirect(
        method = "renderUserModel",
        at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/ui/framework/UIBaseMenu;isHideGizmoHeld()Z")
    )
    private boolean bbspp$limbPickHideGizmo()
    {
        return this.bbspp$limbPickActive || UIBaseMenu.isHideGizmoHeld();
    }

    /**
     * 预览里的坐标轴（含摄像机参考轴）：按住肢体选择按键时隐藏。
     */
    @Redirect(
        method = "renderAxes",
        at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/ui/framework/UIBaseMenu;shouldRenderAxes()Z")
    )
    private boolean bbspp$limbPickHideAxes()
    {
        return UIBaseMenu.shouldRenderAxes() && !this.bbspp$limbPickActive;
    }

    /**
     * 命中框：按住肢体选择按键时不再绘制，免得挡住肢体。
     */
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
