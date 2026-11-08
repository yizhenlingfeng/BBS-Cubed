package routu.bbsuirabbit.mixin;

import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** BBS 2.7 made the preview camera distance private (2.5.2: a public Factor). */
@Mixin(value = UIModelRenderer.class, remap = false)
public interface UIModelRendererAccessor
{
    @Accessor(value = "targetDistance", remap = false)
    float rabbit$getTargetDistance();

    /* UIModelRenderer.updateLogic catches up missed ticks; previews must start at "now". */
    @Accessor(value = "tick", remap = false)
    void rabbit$setTick(long tick);
}
