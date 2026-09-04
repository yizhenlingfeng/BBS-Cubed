package gbeic.bbsplusplus.ui.miniwindow;

import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.icons.Icon;

import java.util.Set;

/**
 * 影片 / 粒子 dock 小窗宿主。方法名避开 BBS 私有名防递归。
 */
public interface IMiniWindowDockHost
{
    UIElement hostGetDockPanel(String panelId);

    Icon hostResolvePanelIcon(String panelId);

    EditorLayoutNode hostGetLayoutRoot();

    void hostSetLayoutRoot(EditorLayoutNode root);

    void hostRefreshLayout();

    /** 优先影片 editor / dock 自身,保证 z-order 与输入正确。 */
    UIElement hostGetMiniWindowLayer();

    /** 在一次点击、切换或布局刷新结束后，把整个浮动层重新放到停靠内容之上。 */
    default void hostBringMiniWindowLayerToFront()
    {
    }

    void hostReattachPanel(String panelId, UIElement panel);

    Set<String> hostFloatingPanelIds();

    /**
     * 返回小窗面板在持久布局树中的 panelId。返回 null 表示它只是布局面板的子内容，
     * 浮动和还原时不应增删布局节点。
     */
    default String hostLayoutPanelId(String panelId)
    {
        return panelId;
    }

    default boolean hostIsFloating(String panelId)
    {
        return panelId != null && this.hostFloatingPanelIds().contains(panelId);
    }

    /** Whether this panel represents real content that can be moved into a mini window. */
    default boolean hostCanUndockPanel(String panelId)
    {
        return true;
    }

    void hostClearMiniWindows();

    /** 浮动后刷新参数面板 top offset 等。 */
    default void hostOnPanelFloated(String panelId)
    {
    }

    default void hostOnPanelDocked(String panelId)
    {
    }

    /** 小窗或其中的标签页获得焦点。 */
    default void hostOnPanelFocused(String panelId)
    {
    }

    /**
     * 监视器等:内容区空白点击/滚轮应穿透到相机。
     */
    default boolean hostShouldPassthroughContentInput(String panelId)
    {
        return false;
    }

    /** 点击转发器。与滚轮分开，避免 UIContext 保留旧 mouseWheel 值造成误判。 */
    default java.util.function.Predicate<mchorse.bbs_mod.ui.framework.UIContext> hostViewportClickForwarder(String panelId)
    {
        return null;
    }

    /** 滚轮转发器。 */
    default java.util.function.Predicate<mchorse.bbs_mod.ui.framework.UIContext> hostViewportScrollForwarder(String panelId)
    {
        return null;
    }

    /**
     * 方案A-补:监视器小窗的鼠标释放转发器。
     * orbit 启动后,释放事件必须送达 orbitUI.mouseReleased → orbit.release 清 dragging,
     * 否则中键(尤其)的 pan 状态卡住,会持续抢占左右键导致失效。
     * 返回 null 表示宿主不接管释放(退回原 PASS 链路)。
     */
    default java.util.function.Consumer<mchorse.bbs_mod.ui.framework.UIContext> hostViewportReleaseForwarder(String panelId)
    {
        return null;
    }
}
