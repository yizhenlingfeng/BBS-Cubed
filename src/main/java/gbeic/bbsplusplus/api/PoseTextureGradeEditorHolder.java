package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/** Access to texture-grade controls attached to UIPoseEditor. */
public interface PoseTextureGradeEditorHolder
{
    UIElement bbspp_cml$getTextureGradeSection();

    UIColor bbspp_cml$getTextureTintControl();

    UITrackpad bbspp_cml$getTextureWhitenControl();

    void bbspp_cml$refreshTextureGradeControls();
}
