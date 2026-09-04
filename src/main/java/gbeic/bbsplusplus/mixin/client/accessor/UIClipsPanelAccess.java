package gbeic.bbsplusplus.mixin.client.accessor;

import gbeic.bbsplusplus.api.UIClipsPanelAccessor;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = UIClipsPanel.class, remap = false)
public interface UIClipsPanelAccess extends UIClipsPanelAccessor
{
    @Accessor(value = "panel", remap = false)
    UIClip bbspp_cml$getClipPanel();

    @Accessor(value = "target", remap = false)
    UIElement bbspp_cml$getTarget();
}
