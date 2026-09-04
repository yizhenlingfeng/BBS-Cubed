package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.ActionsTimelineEditorContext;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIActionsConfigKeyframeFactory;
import mchorse.bbs_mod.ui.utils.pose.UIActionsConfigEditor;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UIActionsConfigKeyframeFactory.class, remap = false)
public abstract class UIActionsConfigKeyframeFactoryMixin
{
    @Shadow
    public UIActionsConfigEditor actionsEditor;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void bbspp_cml$attachTimelineContext(
        Keyframe<ActionsConfig> keyframe,
        UIKeyframes editor,
        CallbackInfo ci
    )
    {
        UIKeyframeSheet sheet = editor.getGraph().getSheet(keyframe);
        ModelForm form = (ModelForm) FormUtils.getForm(sheet.property);
        boolean overlay = sheet.property != null && sheet.property.getId().startsWith("actions_overlay");

        ((ActionsTimelineEditorContext) this.actionsEditor).bbspp_cml$setTimelineContext(
            keyframe,
            editor,
            form,
            overlay
        );
    }

    /**
     * 原版 resize 会 removeAll 后只重建内置控件，因此构造器中追加的循环选项会消失。
     * 在原版完成宽/窄布局、但尚未计算滚动区域前，把两个选项放回实际效果面板。
     */
    @Inject(
        method = "resize()V",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/ui/framework/elements/input/keyframes/factories/UIKeyframeFactory;resize()V",
            shift = At.Shift.BEFORE,
            remap = false
        ),
        require = 1,
        remap = false
    )
    private void bbspp_cml$restoreTimelineLoopControls(CallbackInfo ci)
    {
        ActionsTimelineEditorContext context = (ActionsTimelineEditorContext) this.actionsEditor;

        this.actionsEditor.add(
            context.bbspp_cml$getLoopBeyondControl(),
            context.bbspp_cml$getLoopIntervalRow()
        );
        context.bbspp_cml$refreshTimelineLoopControls();
    }
}
