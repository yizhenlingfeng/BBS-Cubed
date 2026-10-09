package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.settings.values.ui.ValueStringKeys;

import java.util.Set;

public interface LimbCullingConfigHolder
{
    Set<String> bbspp$getLimbCullingBones();

    ValueStringKeys bbspp$getLimbCullingBonesValue();
}
