package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.utils.colors.Color;

/**
 * Per-bone outline settings. They live on {@code PoseTransform} (like glint and PBR) so they are
 * saved with the pose/keyframe instead of the model config.
 */
public interface BoneOutlineHolder
{
    public static final float DEFAULT_WIDTH = 1F;

    /**
     * LabPBR emission strength, 0..254. {@code 0} means "no emission"; {@code 1..254} is written
     * linearly into the alpha of the {@code _s} companion texture (255 is reserved by LabPBR for
     * "no emission"). Only visible while a shader pack that declares the {@code normals} /
     * {@code specular} samplers is active.
     */
    public static final float DEFAULT_GLOW = 0F;

    /**
     * Vanilla full-bright overlay strength, 0..255. This is the additive layer that reproduces the
     * old "glow" look without any shader pack; keep it separate from {@link #DEFAULT_GLOW}, which
     * only ever feeds the LabPBR emission channel.
     *
     * <p>The editor exposes it as a plain on/off switch, because the layer reads as either
     * "there" or "not there" and a continuous slider only produced two perceptible states.</p>
     */
    public static final float DEFAULT_VANILLA_GLOW = 0F;

    /** The strength the switch writes when it is turned on. */
    public static final float VANILLA_GLOW_ON = 255F;

    /** The value legacy saves fall back to, so the pre-split "glow on" look survives the upgrade. */
    public static final float LEGACY_VANILLA_GLOW = VANILLA_GLOW_ON;

    public static final float DEFAULT_DECAL = 4F;

    /**
     * Whether the outline shell of this bone keeps face culling on.
     *
     * <p>The shell is an inward-facing box, so with culling on only the far walls survive and the
     * body hides all but the silhouette. That reads as a clean rim on solid limbs, but a cube that
     * is flat on one axis (a decal plane, a card) has no thickness to hide those walls behind, so
     * from some angles the rim disappears entirely. Turning this off emits the shell double-sided,
     * which keeps the outline visible from both directions at the cost of drawing twice the
     * vertices.</p>
     *
     * <p>Default is on, matching the look every existing save was authored against.</p>
     */
    public static final boolean DEFAULT_FACE_CULL = true;

    boolean bbspp_cml$getOutline();

    void bbspp_cml$setOutline(boolean outline);

    Color bbspp_cml$getOutlineColor();

    void bbspp_cml$setOutlineColor(Color color);

    float bbspp_cml$getOutlineWidth();

    void bbspp_cml$setOutlineWidth(float width);

    float bbspp_cml$getOutlineGlow();

    void bbspp_cml$setOutlineGlow(float glow);

    float bbspp_cml$getOutlineVanillaGlow();

    void bbspp_cml$setOutlineVanillaGlow(float glow);

    float bbspp_cml$getOutlineDecalSize();

    void bbspp_cml$setOutlineDecalSize(float size);

    boolean bbspp_cml$getOutlineFaceCull();

    void bbspp_cml$setOutlineFaceCull(boolean faceCull);
}
