package gbeic.bbsplusplus.client.ui.film;

/**
 * Duck 接口：由 {@code UIKeyframeDopeSheetMixin} 实现并混入
 * {@code UIKeyframeDopeSheet}，用于在用户重新点击锁定按钮（或解锁状态被关闭）
 * 时，立刻中止正在进行/已蓄力的轨道拖动，避免释放时把旧拖动提交到新状态。
 */
public interface TrackDragHost
{
    /** 中止当前轨道拖动（清除蓄力/拖动/插入线状态，不做任何提交）。 */
    void bbspp$cancelTrackDrag();
}
