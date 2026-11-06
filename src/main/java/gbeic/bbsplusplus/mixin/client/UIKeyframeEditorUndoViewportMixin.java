package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.util.TimelineViewportKeeper;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.ui.utils.Scroll;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 撤销/重做关键帧后保持时间轴的视口不变。
 *
 * <p>{@code UIKeyframeEditor.applyUndoData(MapType)} 会调用 {@code UIKeyframes.applyState(...)}，
 * 后者用撤销条目里快照的 {@code x_min}/{@code x_max} 重新 {@code view()} 时间轴，并用
 * {@code currentGraph.restoreState(...)} 把轨道列表的垂直滚动也套回快照值；编辑之后的平移、缩放
 * 与滚动因此被丢弃。这里进入时抓当前视口、退出时原样恢复，关键帧选中与图状态的恢复仍保持原版行为。</p>
 *
 * <p>回放编辑器会在撤销时重建整个 {@code UIKeyframeEditor}（见
 * {@code UIReplaysEditorUndoScrollMixin}），新建元素的 {@code Scroll.area} 可能尚未布局，因此
 * 抓垂直滚动时不再要求可视高度大于 0，避免把「保持当前滚动」整个跳过。</p>
 */
@Mixin(value = UIKeyframeEditor.class, remap = false)
public abstract class UIKeyframeEditorUndoViewportMixin
{
    @Shadow
    public UIKeyframes view;

    @Unique
    private double[] bbspp$viewport;

    @Unique
    private double bbspp$trackScroll;

    @Unique
    private boolean bbspp$hasTrackScroll;

    @Inject(
        method = "applyUndoData(Lmchorse/bbs_mod/data/types/MapType;)V",
        at = @At("HEAD"),
        remap = false
    )
    private void bbspp$captureViewport(MapType data, CallbackInfo ci)
    {
        this.bbspp$viewport = TimelineViewportKeeper.capture(this.view == null ? null : this.view.getXAxis());
        this.bbspp$trackScroll = 0D;
        this.bbspp$hasTrackScroll = false;

        if (this.view != null)
        {
            UIKeyframeDopeSheet dopeSheet = this.view.getDopeSheet();
            Scroll scroll = dopeSheet == null ? null : dopeSheet.getYAxis();

            if (scroll != null)
            {
                this.bbspp$trackScroll = scroll.getScroll();
                this.bbspp$hasTrackScroll = true;
            }
        }
    }

    @Inject(
        method = "applyUndoData(Lmchorse/bbs_mod/data/types/MapType;)V",
        at = @At("RETURN"),
        remap = false
    )
    private void bbspp$restoreViewport(MapType data, CallbackInfo ci)
    {
        double[] viewport = this.bbspp$viewport;
        double trackScroll = this.bbspp$trackScroll;
        boolean hasTrackScroll = this.bbspp$hasTrackScroll;

        this.bbspp$viewport = null;
        this.bbspp$trackScroll = 0D;
        this.bbspp$hasTrackScroll = false;

        if (this.view != null)
        {
            TimelineViewportKeeper.restore(this.view.getXAxis(), viewport);

            if (hasTrackScroll)
            {
                UIKeyframeDopeSheet dopeSheet = this.view.getDopeSheet();
                Scroll scroll = dopeSheet == null ? null : dopeSheet.getYAxis();

                if (scroll != null)
                {
                    scroll.setScroll(trackScroll);
                }
            }
        }
    }
}
