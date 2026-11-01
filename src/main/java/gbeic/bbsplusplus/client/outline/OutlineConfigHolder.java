package gbeic.bbsplusplus.client.outline;

import java.util.List;

/**
 * Access to the per-model outline settings stored on BBS's {@code ModelConfig}.
 *
 * <p>Injected by {@code ModelConfigOutlineMixin}. The method names are prefixed so they can
 * never collide with a future BBS property of the same concept.</p>
 */
public interface OutlineConfigHolder
{
    boolean bbspp$isOutlineEnabled();

    void bbspp$setOutlineEnabled(boolean enabled);

    /** Global outline colour, ARGB. */
    int bbspp$getOutlineColor();

    void bbspp$setOutlineColor(int argb);

    /**
     * Global outline width in <b>quarter model pixels</b> (4 = one model pixel). The renderer
     * expands the shell by {@code 0.25 * width} model pixels on every side.
     */
    float bbspp$getOutlineWidth();

    void bbspp$setOutlineWidth(float width);

    /** Global glow strength, 0..255; used by the LabPBR emission path where available. */
    float bbspp$getOutlineGlow();

    void bbspp$setOutlineGlow(float glow);

    /** Cubes whose longest axis is at most this (model pixels) may be treated as decals. 0 disables. */
    float bbspp$getOutlineDecalSize();

    void bbspp$setOutlineDecalSize(float size);

    List<OutlineLimbValue> bbspp$getOutlineLimbs();

    /** True when the whole model or at least one limb is outlined. */
    default boolean bbspp$hasOutline()
    {
        return this.bbspp$isOutlineEnabled() || !this.bbspp$getOutlineLimbs().isEmpty();
    }

    /** The outline override for this bone, or null when the bone uses the global outline. */
    OutlineLimbValue bbspp$getOutlineLimb(String bone);

    /** Add or remove the outline override for one bone, with undo support. */
    void bbspp$setOutlineLimbEnabled(String bone, boolean enabled);
}
