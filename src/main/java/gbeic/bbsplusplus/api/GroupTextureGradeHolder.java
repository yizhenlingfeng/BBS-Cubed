package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.utils.colors.Color;

/** Runtime texture grade attached to a rendered model group. */
public interface GroupTextureGradeHolder
{
    Color bbspp_cml$getTextureTint();

    float bbspp_cml$getTextureWhiten();

    void bbspp_cml$setTextureTint(Color color);

    void bbspp_cml$setTextureWhiten(float value);
}
