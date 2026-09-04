package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link UIClipsPanel} 当前的 inspector panel。
 */
@Mixin(value = UIClipsPanel.class, remap = false)
public interface UIClipsPanelAccessor
{
    @Accessor(value = "panel", remap = false)
    UIClip bbspp_cml$getClipPanel();

    @Accessor(value = "target", remap = false)
    UIElement bbspp_cml$getTarget();
}
