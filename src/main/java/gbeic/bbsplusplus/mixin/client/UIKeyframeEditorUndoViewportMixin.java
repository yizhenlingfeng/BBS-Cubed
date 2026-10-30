package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.util.TimelineViewportKeeper;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 撤销/重做关键帧后保持时间轴的水平视口不变。
 *
 * <p>{@code UIKeyframeEditor.applyUndoData(MapType)} 会调用 {@code UIKeyframes.applyState(...)}，
 * 后者用撤销条目里快照的 {@code x_min}/{@code x_max} 重新 {@code view()} 时间轴，编辑之后的平移与
 * 缩放因此被丢弃。这里进入时抓当前视口、退出时原样恢复，关键帧选中与图状态的恢复仍保持原版行为。</p>
 */
@Mixin(value = UIKeyframeEditor.class, remap = false)
public abstract class UIKeyframeEditorUndoViewportMixin
{
    @Shadow
    public UIKeyframes view;

    @Unique
    private double[] bbspp$viewport;

    @Inject(
        method = "applyUndoData(Lmchorse/bbs_mod/data/types/MapType;)V",
        at = @At("HEAD"),
        remap = false
    )
    private void bbspp$captureViewport(MapType data, CallbackInfo ci)
    {
        this.bbspp$viewport = TimelineViewportKeeper.capture(this.view == null ? null : this.view.getXAxis());
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

        if (this.view != null)
        {
            TimelineViewportKeeper.restore(this.view.getXAxis(), viewport);
        }
    }
}
