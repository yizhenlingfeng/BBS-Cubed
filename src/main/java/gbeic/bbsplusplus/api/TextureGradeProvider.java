package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;

/** Runtime access to the two texture-grade properties added to a form. */
public interface TextureGradeProvider
{
    ValueColor bbspp_cml$getTextureTint();

    ValueFloat bbspp_cml$getTextureWhiten();
}
