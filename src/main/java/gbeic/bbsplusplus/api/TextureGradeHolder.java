package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.utils.colors.Color;

/** Per-pose-bone texture grading values carried from PoseTransform to ModelGroup. */
public interface TextureGradeHolder
{
    Color bbspp_cml$getTextureTint();

    float bbspp_cml$getTextureWhiten();

    void bbspp_cml$setTextureTint(Color color);

    void bbspp_cml$setTextureWhiten(float value);
}
