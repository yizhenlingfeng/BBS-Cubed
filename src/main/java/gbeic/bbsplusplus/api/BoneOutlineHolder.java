package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.utils.colors.Color;

/**
 * Per-bone outline settings. They live on {@code PoseTransform} (like glint and PBR) so they are
 * saved with the pose/keyframe instead of the model config.
 */
public interface BoneOutlineHolder
{
    public static final float DEFAULT_WIDTH = 1F;
    public static final float DEFAULT_GLOW = 51F;
    public static final float DEFAULT_DECAL = 4F;

    boolean bbspp_cml$getOutline();

    void bbspp_cml$setOutline(boolean outline);

    Color bbspp_cml$getOutlineColor();

    void bbspp_cml$setOutlineColor(Color color);

    float bbspp_cml$getOutlineWidth();

    void bbspp_cml$setOutlineWidth(float width);

    float bbspp_cml$getOutlineGlow();

    void bbspp_cml$setOutlineGlow(float glow);

    float bbspp_cml$getOutlineDecalSize();

    void bbspp_cml$setOutlineDecalSize(float size);
}
