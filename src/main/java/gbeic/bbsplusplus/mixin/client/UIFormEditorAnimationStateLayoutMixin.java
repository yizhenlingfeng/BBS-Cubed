package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.AnimationStateLayoutHost;
import gbeic.bbsplusplus.ui.forms.AnimationStateLayoutSupport;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.forms.editors.states.keyframes.UIAnimationStateEditor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 动画状态布局注入薄壳：注入点留在 Fabric Mixin，布局和撤销业务下沉到
 * {@link AnimationStateLayoutSupport}。
 */
@Mixin(value = UIFormEditor.class, remap = false)
public abstract class UIFormEditorAnimationStateLayoutMixin implements AnimationStateLayoutHost
{
    @Shadow
    public UIElement statesEditor;

    @Shadow
    public UIAnimationStateEditor statesKeyframes;

    @Shadow
    public UIIcon shiftDuration;

    @Unique
    private AnimationStateLayoutSupport bbspp_cml$support;

    @Unique
    private AnimationStateLayoutSupport bbspp_cml$support()
    {
        if (this.bbspp_cml$support == null)
        {
            this.bbspp_cml$support = new AnimationStateLayoutSupport(
                (UIFormEditor) (Object) this,
                this.statesEditor,
                this.statesKeyframes,
                this.shiftDuration
            );
        }

        return this.bbspp_cml$support;
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$createStateDock(UIFormPalette palette, CallbackInfo ci)
    {
        this.bbspp_cml$support().activate();
    }

    @Override
    public void bbspp_cml$prepareStateKeyframeEditor()
    {
        this.bbspp_cml$support().prepareStateKeyframeEditor();
    }

    @Override
    public void bbspp_cml$attachStateKeyframeEditor(UIKeyframeEditor editor)
    {
        this.bbspp_cml$support().attachStateKeyframeEditor(editor);
    }

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void bbspp_cml$keepStateDockContentMounted(UIContext context, CallbackInfo ci)
    {
        this.bbspp_cml$support().keepStateDockContentMounted();
    }

    @Inject(method = "clickViewport", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp_cml$prioritizeStatePreviewControls(
        UIContext context,
        StencilFormFramebuffer stencil,
        CallbackInfoReturnable<Boolean> cir
    )
    {
        if (this.bbspp_cml$support().clickPreviewControls(context))
        {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = {"undo", "redo"}, at = @At("HEAD"), remap = false)
    private void bbspp_cml$capturePoseBeforeHistoryChange(CallbackInfo ci)
    {
        this.bbspp_cml$support().capturePoseBeforeHistoryChange();
    }

    @Inject(method = {"undo", "redo"}, at = @At("RETURN"), remap = false)
    private void bbspp_cml$restorePoseAfterHistoryChange(CallbackInfo ci)
    {
        this.bbspp_cml$support().restorePoseAfterHistoryChange();
    }
}
