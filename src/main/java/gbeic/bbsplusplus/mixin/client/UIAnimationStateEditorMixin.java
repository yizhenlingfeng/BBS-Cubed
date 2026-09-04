package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.ui.forms.AnimationStateEditorSupport;
import mchorse.bbs_mod.forms.states.AnimationState;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.forms.editors.states.keyframes.UIAnimationStateEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = UIAnimationStateEditor.class, remap = false)
public abstract class UIAnimationStateEditorMixin
{
    @Shadow
    public UIKeyframeEditor keyframeEditor;

    @Shadow
    public UIFormEditor editor;

    @Shadow
    private AnimationState state;

    @Unique
    private AnimationStateEditorSupport bbspp_cml$support;

    @Unique
    private AnimationStateEditorSupport bbspp_cml$support()
    {
        if (this.bbspp_cml$support == null)
        {
            this.bbspp_cml$support = new AnimationStateEditorSupport(
                (UIAnimationStateEditor) (Object) this,
                this.editor,
                () -> this.keyframeEditor,
                () -> this.state
            );
        }

        return this.bbspp_cml$support;
    }

    @Inject(method = "setState", at = @At("HEAD"), remap = false)
    private void bbspp_cml$rememberExpandedPoseTabs(AnimationState nextState, CallbackInfo ci)
    {
        this.bbspp_cml$support().rememberExpandedPoseTabs(nextState);
    }

    @Inject(method = "flushForm", at = @At("TAIL"), remap = false)
    private void bbspp_cml$addStateIKSheets(
        List<UIKeyframeSheet> sheets,
        List<UIKeyframeSheet> formSheets,
        Form form,
        CallbackInfo ci
    )
    {
        this.bbspp_cml$support().addStateIKSheets(sheets, form);
    }

    @Inject(method = "setState", at = @At("TAIL"), remap = false)
    private void bbspp_cml$finishStateKeyframeSetup(AnimationState nextState, CallbackInfo ci)
    {
        this.bbspp_cml$support().finishStateKeyframeSetup(nextState);
    }
}
