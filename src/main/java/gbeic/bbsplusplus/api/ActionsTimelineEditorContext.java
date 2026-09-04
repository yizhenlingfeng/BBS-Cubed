package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

public interface ActionsTimelineEditorContext
{
    UIElement bbspp_cml$getLoopBeyondControl();

    UIElement bbspp_cml$getLoopIntervalRow();

    void bbspp_cml$refreshTimelineLoopControls();

    void bbspp_cml$setTimelineContext(
        Keyframe<ActionsConfig> keyframe,
        UIKeyframes editor,
        ModelForm form,
        boolean overlay
    );
}
