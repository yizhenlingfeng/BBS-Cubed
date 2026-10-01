package bbslezy.camera.clips.screen;

import mchorse.bbs_mod.utils.MathUtils;

/**
 * Fisheye UV warp uses {@code uv' = uv * (1 + k * r²)}.
 */
public final class LensDistortionOverscan
{
    public static final float CORNER_R2 = 0.5F;
    public static final float EDGE_R2 = 0.25F;
    public static final float MIN_UNDERSCAN_SCALE = 0.55F;
    public static final float FRAMING_FOCUS_BLOCKS = 4F;
    public static final float CORNER_RADIUS = 0.70710678F;

    private LensDistortionOverscan()
    {}

    public static float positiveFitScale(float lensDistortion, float lensRadiusX, float lensRadiusY, float lensHardness)
    {
        if (lensDistortion <= 1.0e-6F)
        {
            return 1F;
        }

        float radiusX = Math.max(lensRadiusX * CORNER_RADIUS, 1.0e-6F);
        float radiusY = Math.max(lensRadiusY * CORNER_RADIUS, 1.0e-6F);
        float hardness = MathUtils.clamp(lensHardness, 0F, 1F);
        float feather = (1F - hardness) * 0.75F;
        float extent = Math.min(0.5F, Math.max(radiusX, radiusY) * (1F + feather));

        return Math.max(1F, extent * (1F + lensDistortion * CORNER_R2) / 0.5F);
    }

    public static float framingDistanceOffset(float lensDistortion, float lensRadiusX, float lensRadiusY, float lensHardness)
    {
        float fitScale = positiveFitScale(lensDistortion, lensRadiusX, lensRadiusY, lensHardness);

        if (fitScale <= 1.0e-4F)
        {
            return 0F;
        }

        return FRAMING_FOCUS_BLOCKS * (1F - fitScale);
    }

    public static float overscanScale(float lensDistortion)
    {
        if (Math.abs(lensDistortion) <= 1.0e-6F)
        {
            return 1F;
        }

        if (lensDistortion > 0F)
        {
            return 1F + CORNER_R2 * lensDistortion;
        }

        return Math.max(MIN_UNDERSCAN_SCALE, 1F + EDGE_R2 * lensDistortion);
    }

    public static float adjustFovDegrees(float fovDegrees, float lensDistortion)
    {
        return adjustFovDegreesByScale(fovDegrees, overscanScale(lensDistortion));
    }

    public static float adjustFovDegreesByScale(float fovDegrees, float scale)
    {
        if (Math.abs(scale - 1F) <= 1.0e-4F)
        {
            return fovDegrees;
        }

        float half = MathUtils.toRad(fovDegrees) * 0.5F;
        float tanHalf = (float) Math.tan(half);

        if (!Float.isFinite(tanHalf) || tanHalf <= 0F)
        {
            return fovDegrees;
        }

        float scaledHalf = (float) Math.atan(tanHalf * (double) scale);
        float scaled = MathUtils.toDeg(scaledHalf * 2F);

        if (!Float.isFinite(scaled))
        {
            return fovDegrees;
        }

        return Math.max(1F, scaled);
    }
}
