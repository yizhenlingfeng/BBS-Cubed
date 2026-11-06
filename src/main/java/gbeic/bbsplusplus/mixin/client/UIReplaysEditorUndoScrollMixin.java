package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.replays.UIReplaysListPanel;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.ui.utils.Scroll;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 撤销/重做回放数据时保持左侧回放列表与右侧轨道列表的滚动位置。
 *
 * <p>回放列表：原版 {@code setReplay(..., true, ...)} 会把选中行滚到顶部，这里在撤销前记录
 * 滚动，撤销完成且回放未切换时恢复。</p>
 *
 * <p>轨道列表：{@code updateChannelsList()} 每次都会重建 {@code UIKeyframeEditor}，并在轨道
 * 建好<b>之前</b>就 {@code copyViewport(...)} 复制了垂直滚动，此后每个 {@code addSheet()} 的
 * {@code Scroll.clamp()} 都会按此时仅一两行高的 {@code scrollSize} 把它压回接近 0，所以轨道
 * 一多，每次撤销都会被拽回顶端。这里改成在重建入口记录、等轨道与 {@code resize()} 全部完成
 * 后再套回；{@code applyUndoData} 层再兜底一次。撤销切换了回放时保留原版定位行为。</p>
 */
@Mixin(value = UIReplaysEditor.class, remap = false)
public abstract class UIReplaysEditorUndoScrollMixin
{
    @Shadow
    public UIReplaysListPanel replaysList;

    @Shadow
    private Replay replay;

    @Shadow
    public UIKeyframeEditor keyframeEditor;

    @Unique
    private Replay bbspp$beforeReplay;

    @Unique
    private double bbspp$listScroll;

    @Unique
    private boolean bbspp$hasListScroll;

    /** applyUndoData 层兜底用的轨道滚动（与重建层分开，避免嵌套调用互相覆盖）。 */
    @Unique
    private double bbspp$undoTrackScroll;

    @Unique
    private boolean bbspp$hasUndoTrackScroll;

    /** updateChannelsList 重建层用的轨道滚动。 */
    @Unique
    private double bbspp$rebuildTrackScroll;

    @Unique
    private boolean bbspp$hasRebuildTrackScroll;

    /** 读取轨道列表的垂直滚动；编辑器或摄影表不可用时返回 {@link Double#NaN}。 */
    @Unique
    private static double bbspp$readTrackScroll(UIKeyframeEditor editor)
    {
        if (editor == null || editor.view == null)
        {
            return Double.NaN;
        }

        UIKeyframeDopeSheet dopeSheet = editor.view.getDopeSheet();
        Scroll scroll = dopeSheet == null ? null : dopeSheet.getYAxis();

        return scroll == null ? Double.NaN : scroll.getScroll();
    }

    /** 把滚动套回（可能是重建后的）轨道列表；{@code NaN} 或编辑器缺失时跳过。 */
    @Unique
    private static void bbspp$writeTrackScroll(UIKeyframeEditor editor, double value)
    {
        if (editor == null || editor.view == null || Double.isNaN(value))
        {
            return;
        }

        UIKeyframeDopeSheet dopeSheet = editor.view.getDopeSheet();
        Scroll scroll = dopeSheet == null ? null : dopeSheet.getYAxis();

        if (scroll != null)
        {
            scroll.setScroll(value);
        }
    }

    /* ==================== 轨道列表重建：修正 copyViewport 的时机 ==================== */

    @Inject(method = "updateChannelsList()V", at = @At("HEAD"), remap = false)
    private void bbspp$captureRebuildTrackScroll(CallbackInfo ci)
    {
        this.bbspp$rebuildTrackScroll = bbspp$readTrackScroll(this.keyframeEditor);
        this.bbspp$hasRebuildTrackScroll = !Double.isNaN(this.bbspp$rebuildTrackScroll);
    }

    @Inject(method = "updateChannelsList()V", at = @At("RETURN"), remap = false)
    private void bbspp$restoreRebuildTrackScroll(CallbackInfo ci)
    {
        double value = this.bbspp$rebuildTrackScroll;
        boolean has = this.bbspp$hasRebuildTrackScroll;

        this.bbspp$rebuildTrackScroll = Double.NaN;
        this.bbspp$hasRebuildTrackScroll = false;

        if (has)
        {
            bbspp$writeTrackScroll(this.keyframeEditor, value);
        }
    }

    /* ==================== 撤销/重做：列表与轨道预览位置兜底 ==================== */

    @Inject(
        method = "applyUndoData(Lmchorse/bbs_mod/data/types/MapType;)V",
        at = @At("HEAD"),
        remap = false
    )
    private void bbspp$captureListScroll(MapType data, CallbackInfo ci)
    {
        this.bbspp$beforeReplay = this.replay;
        this.bbspp$listScroll = 0D;
        this.bbspp$hasListScroll = false;

        if (this.replaysList != null && this.replaysList.replays != null)
        {
            Scroll scroll = this.replaysList.replays.scroll;

            if (scroll != null && scroll.area != null && scroll.area.h > 0)
            {
                this.bbspp$listScroll = scroll.getScroll();
                this.bbspp$hasListScroll = true;
            }
        }

        this.bbspp$undoTrackScroll = bbspp$readTrackScroll(this.keyframeEditor);
        this.bbspp$hasUndoTrackScroll = !Double.isNaN(this.bbspp$undoTrackScroll);
    }

    @Inject(
        method = "applyUndoData(Lmchorse/bbs_mod/data/types/MapType;)V",
        at = @At("RETURN"),
        remap = false
    )
    private void bbspp$restoreListScroll(MapType data, CallbackInfo ci)
    {
        Replay beforeReplay = this.bbspp$beforeReplay;
        double listScroll = this.bbspp$listScroll;
        boolean hasListScroll = this.bbspp$hasListScroll;
        double trackScroll = this.bbspp$undoTrackScroll;
        boolean hasTrackScroll = this.bbspp$hasUndoTrackScroll;

        this.bbspp$beforeReplay = null;
        this.bbspp$listScroll = 0D;
        this.bbspp$hasListScroll = false;
        this.bbspp$undoTrackScroll = Double.NaN;
        this.bbspp$hasUndoTrackScroll = false;

        /* 撤销本身切换了回放：保留原版的定位行为 */
        if (beforeReplay == null || beforeReplay != this.replay)
        {
            return;
        }

        if (hasListScroll && this.replaysList != null && this.replaysList.replays != null)
        {
            Scroll scroll = this.replaysList.replays.scroll;

            if (scroll != null)
            {
                scroll.setScroll(listScroll);
            }
        }

        if (hasTrackScroll)
        {
            bbspp$writeTrackScroll(this.keyframeEditor, trackScroll);
        }
    }
}
