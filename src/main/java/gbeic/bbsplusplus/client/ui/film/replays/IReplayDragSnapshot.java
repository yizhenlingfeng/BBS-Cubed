package gbeic.bbsplusplus.client.ui.film.replays;

import mchorse.bbs_mod.ui.framework.UIContext;

/**
 * 由 {@code UIReplayListCategoryMixin} 实现，供通用 {@code UIElement#subMouseClicked}
 * 注入在真实回放列表实例上缓存拖拽前的多选快照。
 */
public interface IReplayDragSnapshot
{
    void bbspp$captureDragSelection(UIContext context);
}
