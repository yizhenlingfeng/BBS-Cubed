package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.utils.colors.Color;

/**
 * Per-bone outline settings. They live on {@code PoseTransform} (like glint and PBR) so they are
 * saved with the pose/keyframe instead of the model config.
 *
 * <p>Every bone owns two independent shells. Layer 0 is the plain outline; its accessors keep the
 * un-suffixed names so existing saves and call sites are untouched. Layer 1 is the "overlay" ring,
 * drawn while {@link #bbspp_cml$getOutlineOverlay()} is on, with its own width and colour.</p>
 *
 * <p>Both layers carry a per-face colour table of {@link #FACE_COUNT} ARGB entries indexed by
 * {@code FACE_FRONT} .. {@code FACE_BOTTOM}. {@code 0} is the sentinel for "not customised, follow
 * this layer's main colour", so old saves render exactly as before.</p>
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

    /** Whether the second (overlay) shell is drawn on top of the main one. Off by default. */
    public static final boolean DEFAULT_OVERLAY = false;

    /**
     * Default extra thickness of the overlay shell, in the same 1/4-model-pixel unit as
     * {@link #DEFAULT_WIDTH}. It does not reuse {@link #DEFAULT_WIDTH} on purpose: only the rim
     * sticking out past the inner layer is visible, so a quarter-pixel overlay reads as "the toggle
     * does nothing". A whole model pixel thickens the rim visibly right away.
     */
    public static final float DEFAULT_OVERLAY_WIDTH = 4F;

    /**
     * The overlay thickness older saves still carry, because the writer always stored the field.
     * Readers map it to {@link #DEFAULT_OVERLAY_WIDTH} so those saves get the visible result
     * without a migration pass.
     */
    public static final float LEGACY_OVERLAY_WIDTH = 1F;

    /** Number of faces a shell has, indexed the same way {@code OutlineGeometry.FACE_*} does. */
    public static final int FACE_COUNT = 6;

    /**
     * Sentinel for "this face is not customised". Because it doubles as a fully transparent ARGB
     * value, a customised face can never be invisible; use the layer-wide width/colour if a face
     * has to disappear.
     */
    public static final int DEFAULT_FACE_COLOR = 0;

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

    /**
     * ARGB colour of one face of the main shell, or {@link #DEFAULT_FACE_COLOR} when it follows
     * {@link #bbspp_cml$getOutlineColor()}. {@code face} is one of the {@code FACE_*} indexes.
     */
    int bbspp_cml$getOutlineFaceColor(int face);

    void bbspp_cml$setOutlineFaceColor(int face, int argb);

    /* ------------------------------------------------------------------ overlay layer */

    boolean bbspp_cml$getOutlineOverlay();

    void bbspp_cml$setOutlineOverlay(boolean overlay);

    Color bbspp_cml$getOverlayColor();

    void bbspp_cml$setOverlayColor(Color color);

    /**
     * Extra thickness grown <em>outside</em> the main shell, in the same "4 ~= 1 model pixel" unit
     * as {@link #bbspp_cml$getOutlineWidth()}. The overlay never sits flush with the main shell:
     * both are inward-facing boxes with no depth write, so a shared surface would z-fight.
     */
    float bbspp_cml$getOverlayWidth();

    void bbspp_cml$setOverlayWidth(float width);

    float bbspp_cml$getOverlayGlow();

    void bbspp_cml$setOverlayGlow(float glow);

    float bbspp_cml$getOverlayVanillaGlow();

    void bbspp_cml$setOverlayVanillaGlow(float glow);

    boolean bbspp_cml$getOverlayFaceCull();

    void bbspp_cml$setOverlayFaceCull(boolean faceCull);

    int bbspp_cml$getOverlayFaceColor(int face);

    void bbspp_cml$setOverlayFaceColor(int face, int argb);

    /* ------------------------------------------------------------------ layer dispatch */

    /**
     * Whether a layer contributes a shell. The two layers are independent on purpose: the overlay
     * keeps drawing when the plain outline is switched off, which is what lets a bone carry only a
     * second ring (say, a thin bright one) without the thick base ring underneath it.
     */
    default boolean bbspp_cml$hasOutlineLayer(int layer)
    {
        return layer == 0 ? this.bbspp_cml$getOutline() : this.bbspp_cml$getOutlineOverlay();
    }

    /** Switches a layer on or off; used by {@code ModelMixin} to mirror both layers at once. */
    default void bbspp_cml$setOutlineLayer(int layer, boolean enabled)
    {
        if (layer == 0)
        {
            this.bbspp_cml$setOutline(enabled);
        }
        else
        {
            this.bbspp_cml$setOutlineOverlay(enabled);
        }
    }

    default Color bbspp_cml$getOutlineColor(int layer)
    {
        return layer == 0 ? this.bbspp_cml$getOutlineColor() : this.bbspp_cml$getOverlayColor();
    }

    default void bbspp_cml$setOutlineColor(int layer, Color color)
    {
        if (layer == 0)
        {
            this.bbspp_cml$setOutlineColor(color);
        }
        else
        {
            this.bbspp_cml$setOverlayColor(color);
        }
    }

    /**
     * Thickness a layer stores, in the same unit as {@link #bbspp_cml$getOutlineWidth()}. This is
     * the raw value the editor shows and the value {@code ModelMixin} copies bone -> group; the
     * overlay keeps a <em>delta</em> here, so widening the base ring widens the overlay ring with
     * it. Use {@link #bbspp_cml$getOutlineShellWidth(int)} when the absolute radius is needed.
     */
    default float bbspp_cml$getOutlineWidth(int layer)
    {
        return layer == 0 ? this.bbspp_cml$getOutlineWidth() : this.bbspp_cml$getOverlayWidth();
    }

    default void bbspp_cml$setOutlineWidth(int layer, float width)
    {
        if (layer == 0)
        {
            this.bbspp_cml$setOutlineWidth(width);
        }
        else
        {
            this.bbspp_cml$setOverlayWidth(width);
        }
    }

    /**
     * Absolute shell radius a layer has to emit at. Both shells are inward-facing boxes with no
     * depth write, so a shared surface would z-fight; growing the overlay <em>outside</em> the main
     * ring keeps the two as clean nested rings. When the main layer is off the delta alone is the
     * radius.
     */
    default float bbspp_cml$getOutlineShellWidth(int layer)
    {
        if (layer == 0)
        {
            return this.bbspp_cml$getOutlineWidth();
        }

        float overlay = this.bbspp_cml$getOverlayWidth();

        return this.bbspp_cml$getOutline() ? this.bbspp_cml$getOutlineWidth() + overlay : overlay;
    }

    default float bbspp_cml$getOutlineGlow(int layer)
    {
        return layer == 0 ? this.bbspp_cml$getOutlineGlow() : this.bbspp_cml$getOverlayGlow();
    }

    default void bbspp_cml$setOutlineGlow(int layer, float glow)
    {
        if (layer == 0)
        {
            this.bbspp_cml$setOutlineGlow(glow);
        }
        else
        {
            this.bbspp_cml$setOverlayGlow(glow);
        }
    }

    default float bbspp_cml$getOutlineVanillaGlow(int layer)
    {
        return layer == 0 ? this.bbspp_cml$getOutlineVanillaGlow() : this.bbspp_cml$getOverlayVanillaGlow();
    }

    default void bbspp_cml$setOutlineVanillaGlow(int layer, float glow)
    {
        if (layer == 0)
        {
            this.bbspp_cml$setOutlineVanillaGlow(glow);
        }
        else
        {
            this.bbspp_cml$setOverlayVanillaGlow(glow);
        }
    }

    default boolean bbspp_cml$getOutlineFaceCull(int layer)
    {
        return layer == 0 ? this.bbspp_cml$getOutlineFaceCull() : this.bbspp_cml$getOverlayFaceCull();
    }

    default void bbspp_cml$setOutlineFaceCull(int layer, boolean faceCull)
    {
        if (layer == 0)
        {
            this.bbspp_cml$setOutlineFaceCull(faceCull);
        }
        else
        {
            this.bbspp_cml$setOverlayFaceCull(faceCull);
        }
    }

    default int bbspp_cml$getOutlineFaceColor(int layer, int face)
    {
        return layer == 0 ? this.bbspp_cml$getOutlineFaceColor(face) : this.bbspp_cml$getOverlayFaceColor(face);
    }

    default void bbspp_cml$setOutlineFaceColor(int layer, int face, int argb)
    {
        if (layer == 0)
        {
            this.bbspp_cml$setOutlineFaceColor(face, argb);
        }
        else
        {
            this.bbspp_cml$setOverlayFaceColor(face, argb);
        }
    }

    /** Whether any face of a layer carries its own colour; used to skip work in the hot path. */
    default boolean bbspp_cml$hasOutlineFaceColors(int layer)
    {
        for (int i = 0; i < FACE_COUNT; i++)
        {
            if (this.bbspp_cml$getOutlineFaceColor(layer, i) != DEFAULT_FACE_COLOR)
            {
                return true;
            }
        }

        return false;
    }

    /** Copies the whole face table of {@code layer} out, so the geometry layer can hold onto it. */
    default int[] bbspp_cml$getOutlineFaceColors(int layer)
    {
        int[] faces = new int[FACE_COUNT];

        for (int i = 0; i < FACE_COUNT; i++)
        {
            faces[i] = this.bbspp_cml$getOutlineFaceColor(layer, i);
        }

        return faces;
    }
}
