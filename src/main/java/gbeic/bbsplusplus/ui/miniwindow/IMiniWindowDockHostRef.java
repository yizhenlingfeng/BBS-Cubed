package gbeic.bbsplusplus.ui.miniwindow;

import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * UIFilmPanel 暴露给 inspector 重新 embed 用的最小接口。
 * 由 {@code UIFilmPanelMiniWindowMixin} 实现。
 */
public interface IMiniWindowDockHostRef
{
    /** editArea panelId 是否处于浮动状态(PANEL_EDIT ∈ floatingIds)。 */
    boolean bbspp_cml$isEditAreaFloating();

    /** 主编辑器或效果面板任一浮动时，检查器应挂在停靠的 editArea 上。 */
    default boolean bbspp_cml$shouldEmbedInspectors()
    {
        return this.bbspp_cml$isEditAreaFloating();
    }

    /** 记录最后一次操作来自哪个编辑器，用于选择共享效果面板的内容。 */
    default void bbspp_cml$selectInspectorOwner(UIElement editor)
    {
    }

    /** inspector 被后台重建后，按当前 owner 重新挂载，但不改变优先级。 */
    default void bbspp_cml$refreshInspectorOwner()
    {
    }

    /** 回放重建 keyframeEditor 前，先把旧的 inspector 从共享 editArea 归位。 */
    default void bbspp_cml$prepareInspectorRebuild()
    {
    }

    /** 取 editArea panel UIElement(可能是小窗内的 editArea、或 editor 内的 editArea)。 */
    UIElement bbspp_cml$editAreaPanel();
}
