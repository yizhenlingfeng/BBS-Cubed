package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.replays.UIReplaysListPanel;
import mchorse.bbs_mod.ui.utils.Scroll;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 撤销/重做回放数据时保持左侧回放列表的滚动位置。
 *
 * <p>原版 {@code UIReplaysEditor.applyUndoData(...)} 通过 {@code setReplay(..., true, ...)}
 * 触发 {@code UIReplayList.scrollToReplay(...)}，会把选中的回放行滚到列表顶部。这里在撤销前
 * 记录滚动位置，撤销完成且回放未切换时恢复；撤销本身切换了回放则保留原版的定位行为。</p>
 */
@Mixin(value = UIReplaysEditor.class, remap = false)
public abstract class UIReplaysEditorUndoScrollMixin
{
    @Shadow
    public UIReplaysListPanel replaysList;

    @Shadow
    private Replay replay;

    @Unique
    private Replay bbspp$beforeReplay;

    @Unique
    private double bbspp$listScroll;

    @Unique
    private boolean bbspp$hasListScroll;

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

        this.bbspp$beforeReplay = null;
        this.bbspp$listScroll = 0D;
        this.bbspp$hasListScroll = false;

        if (beforeReplay == null || beforeReplay != this.replay || !hasListScroll
            || this.replaysList == null || this.replaysList.replays == null)
        {
            return;
        }

        Scroll scroll = this.replaysList.replays.scroll;

        if (scroll != null)
        {
            scroll.setScroll(listScroll);
        }
    }
}
