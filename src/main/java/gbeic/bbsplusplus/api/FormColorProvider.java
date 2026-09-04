package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.settings.values.core.ValueColor;

/** Access to a color property added to a BBS form that does not provide one. */
public interface FormColorProvider
{
    ValueColor bbspp_cml$getColor();
}
