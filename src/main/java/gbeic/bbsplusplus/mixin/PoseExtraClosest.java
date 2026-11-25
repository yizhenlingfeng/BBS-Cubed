package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.BoneOutlineHolder;
import gbeic.bbsplusplus.api.BoneTextureHolder;
import gbeic.bbsplusplus.api.GlintHolder;
import gbeic.bbsplusplus.api.OutlineGradient;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.Arrays;

/**
 * PoseTransform 插值时「取最近关键帧」的选择规则（D-18）。
 *
 * <p>贴图、glint、描边开关、面剔除、叠加层、逐面颜色与渐变都无法在不合理的情况下混合，
 * 因此统一取「最近关键帧」的值（x < 0.5 取 a，否则取 b）。搬到此处后 Mixin 的 lerp/autoLerp
 * 钩子只做字段写入。选择规则与整理前完全一致。</p>
 */
public final class PoseExtraClosest
{
    private PoseExtraClosest()
    {
    }

    public static Link closestTexture(Transform a, Transform b, float x)
    {
        Transform closest = x < 0.5F ? a : b;

        return closest instanceof BoneTextureHolder holder ? holder.bbspp_cml$getTexture() : null;
    }

    /** glint 无法插值，取最近关键帧的值。 */
    public static boolean closestGlint(Transform a, Transform b, float x)
    {
        Transform closest = x < 0.5F ? a : b;

        return closest instanceof GlintHolder holder && holder.bbspp_cml$getGlint();
    }

    /** 描边开关同样取最近关键帧的值。 */
    public static boolean closestOutline(Transform a, Transform b, float x)
    {
        Transform closest = x < 0.5F ? a : b;

        return closest instanceof BoneOutlineHolder holder && holder.bbspp_cml$getOutline();
    }

    /** 面剔除也是布尔量，与描边开关一同取最近关键帧。 */
    public static boolean closestOutlineFaceCull(Transform a, Transform b, float x)
    {
        Transform closest = x < 0.5F ? a : b;

        return !(closest instanceof BoneOutlineHolder holder) || holder.bbspp_cml$getOutlineFaceCull();
    }

    /** 叠加层描边开关，同样是布尔量取最近关键帧。 */
    public static boolean closestOverlay(BoneOutlineHolder a, BoneOutlineHolder b, float x)
    {
        return x < 0.5F ? a.bbspp_cml$getOutlineOverlay() : b.bbspp_cml$getOutlineOverlay();
    }

    /** 逐面颜色无法在两种不同配色之间插值，按最近关键帧整体取出并写入目标数组。 */
    public static void closestFaces(Transform a, Transform b, float x, int[] outlineFaces, int[] overlayFaces)
    {
        Transform closest = x < 0.5F ? a : b;

        if (!(closest instanceof BoneOutlineHolder holder))
        {
            Arrays.fill(outlineFaces, BoneOutlineHolder.DEFAULT_FACE_COLOR);
            Arrays.fill(overlayFaces, BoneOutlineHolder.DEFAULT_FACE_COLOR);

            return;
        }

        for (int i = 0; i < BoneOutlineHolder.FACE_COUNT; i++)
        {
            outlineFaces[i] = holder.bbspp_cml$getOutlineFaceColor(i);
            overlayFaces[i] = holder.bbspp_cml$getOverlayFaceColor(i);
        }
    }

    /** 渐变整体取最近关键帧：两条 stop 结构不同的色带无法混合成有意义的结果。 */
    public static void closestGradients(Transform a, Transform b, float x,
        OutlineGradient[] outlineFaces, OutlineGradient[] overlayFaces, OutlineGradient[] mains)
    {
        Transform closest = x < 0.5F ? a : b;

        if (!(closest instanceof BoneOutlineHolder holder))
        {
            Arrays.fill(outlineFaces, null);
            Arrays.fill(overlayFaces, null);
            Arrays.fill(mains, null);

            return;
        }

        mains[0] = copyGradient(holder.bbspp_cml$getOutlineGradient(0));
        mains[1] = copyGradient(holder.bbspp_cml$getOutlineGradient(1));

        for (int i = 0; i < BoneOutlineHolder.FACE_COUNT; i++)
        {
            outlineFaces[i] = copyGradient(holder.bbspp_cml$getOutlineFaceGradient(0, i));
            overlayFaces[i] = copyGradient(holder.bbspp_cml$getOutlineFaceGradient(1, i));
        }
    }

    private static OutlineGradient copyGradient(OutlineGradient gradient)
    {
        return gradient == null ? null : gradient.copy();
    }
}
