package gbeic.bbsplusplus.client.ui.utils;

/**
 * Duck 接口：由 {@code UIActionsConfigEditorMixin} 实现并混入 {@code UIActionsConfigEditor}，
 * 供 {@code UIElementMixin} 在每帧渲染前调用，用来安装 / 补回列表底边的高度抓手。
 * <p>
 * 走渲染前同步而不是构造函数注入，是因为关键帧弹窗（{@code UIActionsConfigKeyframeFactory}）
 * 会按宽度 {@code removeAll()} 重建编辑器的子控件，构造函数里加的抓手会被清掉。
 * </p>
 */
public interface IListHeightGripHost
{
    /** 按当前控件树同步抓手（缺则装上，被清掉则补回，列表被嵌进别的容器则摘掉）。 */
    void bbspp$syncListHeightGrips();
}
