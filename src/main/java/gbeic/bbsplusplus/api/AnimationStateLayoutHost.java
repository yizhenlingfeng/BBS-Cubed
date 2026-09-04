package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;

/** Connects the animation-state timeline to the dock layout owned by UIFormEditor. */
public interface AnimationStateLayoutHost
{
    void bbspp_cml$prepareStateKeyframeEditor();

    void bbspp_cml$attachStateKeyframeEditor(UIKeyframeEditor editor);
}
