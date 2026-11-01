package gbeic.bbsplusplus.client.outline;

import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.colors.Color;

/**
 * One per-bone outline override. Stored as an element of {@link OutlineLimbList}, so it is
 * serialised into the model's {@code config.json} and participates in undo/redo like BBS's
 * own {@code WeldValue}.
 */
public class OutlineLimbValue extends ValueGroup
{
    /** Target bone / group id. Empty until the user picks one. */
    public final ValueString bone;

    public final ValueColor color;

    /** Width in quarter model pixels, matching the global setting. */
    public final ValueFloat width;

    /** Glow strength, 0..255. */
    public final ValueFloat glow;

    public OutlineLimbValue(String id)
    {
        super(id);

        this.bone = new ValueString("bone", "");
        this.color = new ValueColor("color", Color.white());
        this.width = new ValueFloat("width", 1F, 0F, 10F);
        this.glow = new ValueFloat("glow", 51F, 0F, 255F);

        this.add(this.bone);
        this.add(this.color);
        this.add(this.width);
        this.add(this.glow);
    }
}
