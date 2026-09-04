package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;

/**
 * Exposes the expanded pose-tab state needed by viewport bone picking.
 */
public interface PoseTabStateProvider
{
    boolean bbspp_cml$isExpandedPoseChild(UIKeyframeSheet sheet);
}
