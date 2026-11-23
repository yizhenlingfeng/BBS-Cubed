package gbeic.bbsplusplus.mixin.client.accessor;

import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link UIClipsPanel} 的私有 {@code panel} 字段，供剪辑检查器嵌入小窗时取当前剪辑面板。
 */
@Mixin(value = UIClipsPanel.class, remap = false)
public interface UIClipsPanelAccess
{
    @Accessor(value = "panel", remap = false)
    UIClip bbspp_cml$getClipPanel();
}
