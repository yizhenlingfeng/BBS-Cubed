package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;

/** Supplies the visible transform editor whose pivot the shared gizmo should display. */
public interface GizmoPivotTarget
{
    void bbspp_cml$setPivotTransform(UIPropTransform transform);
}
