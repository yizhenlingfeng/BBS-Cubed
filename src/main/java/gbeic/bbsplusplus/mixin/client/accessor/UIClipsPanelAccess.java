package gbeic.bbsplusplus.mixin.client.accessor;

import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = UIClipsPanel.class, remap = false)
public interface UIClipsPanelAccess
{
    @Accessor(value = "panel", remap = false)
    UIClip bbspp_cml$getClipPanel();
}