package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.forms.values.ValueActionsConfig;

import java.util.List;

public interface ActionsOverlayProvider
{
    ValueActionsConfig bbspp_cml$getActionsOverlay();

    List<ValueActionsConfig> bbspp_cml$getAdditionalActionsOverlays();
}
