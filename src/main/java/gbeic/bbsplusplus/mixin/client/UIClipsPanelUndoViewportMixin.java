package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.util.TimelineViewportKeeper;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 撤销/重做剪辑后保持时间轴的水平视口不变。
 *
 * <p>{@code UIClipsPanel.applyUndoData(MapType)} 会用撤销条目里快照的 {@code x_min}/{@code x_max}
 * 重新 {@code view()} 时间轴，编辑之后的平移与缩放因此被丢弃（表现为撤销后时间轴被拽回左边）。
 * 这里进入时抓当前视口、退出时原样恢复，垂直滚动与选中状态的恢复仍保持原版行为。</p>
 */
@Mixin(value = UIClipsPanel.class, remap = false)
public abstract class UIClipsPanelUndoViewportMixin
{
    @Shadow
    public UIClips clips;

    @Unique
    private double[] bbspp$viewport;

    @Inject(
        method = "applyUndoData(Lmchorse/bbs_mod/data/types/MapType;)V",
        at = @At("HEAD"),
        remap = false
    )
    private void bbspp$captureViewport(MapType data, CallbackInfo ci)
    {
        this.bbspp$viewport = TimelineViewportKeeper.capture(this.clips == null ? null : this.clips.getXAxis());
    }

    @Inject(
        method = "applyUndoData(Lmchorse/bbs_mod/data/types/MapType;)V",
        at = @At("RETURN"),
        remap = false
    )
    private void bbspp$restoreViewport(MapType data, CallbackInfo ci)
    {
        double[] viewport = this.bbspp$viewport;

        this.bbspp$viewport = null;

        if (this.clips != null)
        {
            TimelineViewportKeeper.restore(this.clips.getXAxis(), viewport);
        }
    }
}
