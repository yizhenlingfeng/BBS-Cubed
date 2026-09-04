package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.ActionsTimelineEditorContext;
import gbeic.bbsplusplus.timeline.ui.ActionsTimelineEditorSupport;
import mchorse.bbs_mod.cubic.animation.ActionConfig;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.pose.UIActionsConfigEditor;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * snow_actions 时间线编辑器注入薄壳：循环控件、上下文与端点同步的业务逻辑
 * 全部在 {@link ActionsTimelineEditorSupport}，本类只保留注入点与 duck 接口的
 * 一行委托。@Shadow 私有成员经 Supplier 注入 support。
 */
@Mixin(value = UIActionsConfigEditor.class, remap = false)
public abstract class UIActionsConfigEditorMixin implements ActionsTimelineEditorContext
{
    @Shadow
    private ActionsConfig configs;

    @Shadow
    private ActionConfig config;

    @Shadow
    private Runnable preCallback;

    @Shadow
    private Runnable postCallback;

    @Unique
    private ActionsTimelineEditorSupport bbspp_cml$support;

    @Unique
    private ActionsTimelineEditorSupport bbspp_cml$support()
    {
        if (this.bbspp_cml$support == null)
        {
            this.bbspp_cml$support = new ActionsTimelineEditorSupport(
                (UIActionsConfigEditor) (Object) this,
                () -> this.configs,
                () -> this.config,
                () -> this.preCallback,
                () -> this.postCallback
            );
        }

        return this.bbspp_cml$support;
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$addTimelineLoopControls(
        Runnable preCallback,
        Runnable postCallback,
        CallbackInfo ci
    )
    {
        this.bbspp_cml$support().initControls();
    }

    @Override
    public UIElement bbspp_cml$getLoopBeyondControl()
    {
        return this.bbspp_cml$support().getLoopBeyondControl();
    }

    @Override
    public UIElement bbspp_cml$getLoopIntervalRow()
    {
        return this.bbspp_cml$support().getLoopIntervalRow();
    }

    @Override
    public void bbspp_cml$refreshTimelineLoopControls()
    {
        this.bbspp_cml$support().refreshLoopControls();
    }

    @Override
    public void bbspp_cml$setTimelineContext(
        Keyframe<ActionsConfig> keyframe,
        UIKeyframes editor,
        ModelForm form,
        boolean overlay
    )
    {
        this.bbspp_cml$support().setTimelineContext(keyframe, editor, form, overlay);
    }

    @Inject(method = "pickAction", at = @At("HEAD"), remap = false)
    private void bbspp_cml$beginProgrammaticAnimationSelection(String key, boolean select, CallbackInfo ci)
    {
        this.bbspp_cml$support().beginProgrammaticSelection();
    }

    @Inject(method = "pickAction", at = @At("RETURN"), remap = false)
    private void bbspp_cml$endProgrammaticAnimationSelection(String key, boolean select, CallbackInfo ci)
    {
        this.bbspp_cml$support().endProgrammaticSelection();
    }

    @Inject(
        method = "lambda$new$2",
        at = @At(
            value = "FIELD",
            target = "Lmchorse/bbs_mod/cubic/animation/ActionConfig;loop:Z",
            opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER
        ),
        remap = false
    )
    private void bbspp_cml$syncLoopToEndpoint(UIToggle toggle, CallbackInfo ci)
    {
        this.bbspp_cml$support().syncPairedOptions();
    }

    @Inject(
        method = "lambda$new$3",
        at = @At(
            value = "FIELD",
            target = "Lmchorse/bbs_mod/cubic/animation/ActionConfig;speed:F",
            opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER
        ),
        remap = false
    )
    private void bbspp_cml$syncSpeedToEndpoint(Double value, CallbackInfo ci)
    {
        this.bbspp_cml$support().syncPairedOptions();
    }

    @Inject(
        method = "lambda$new$4",
        at = @At(
            value = "FIELD",
            target = "Lmchorse/bbs_mod/cubic/animation/ActionConfig;fade:F",
            opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER
        ),
        remap = false
    )
    private void bbspp_cml$syncFadeToEndpoint(Double value, CallbackInfo ci)
    {
        this.bbspp_cml$support().syncPairedOptions();
    }

    @Inject(
        method = "lambda$new$5",
        at = @At(
            value = "FIELD",
            target = "Lmchorse/bbs_mod/cubic/animation/ActionConfig;tick:I",
            opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER
        ),
        remap = false
    )
    private void bbspp_cml$syncTickToEndpoint(Double value, CallbackInfo ci)
    {
        this.bbspp_cml$support().syncPairedOptions();
    }

    @Inject(
        method = "lambda$new$1",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/ui/utils/pose/UIActionsConfigEditor;callback(Ljava/lang/Runnable;)V",
            ordinal = 1,
            shift = At.Shift.BEFORE
        ),
        remap = false
    )
    private void bbspp_cml$createAnimationEndpoint(List<String> selection, CallbackInfo ci)
    {
        this.bbspp_cml$support().createAnimationEndpoint();
    }
}
