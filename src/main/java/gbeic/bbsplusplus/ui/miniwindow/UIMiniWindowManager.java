package gbeic.bbsplusplus.ui.miniwindow;

import gbeic.bbsplusplus.BBSFSloveCMLClient;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 会话级小窗:undock / redock / 合并 / reassert。
 */
public class UIMiniWindowManager
{
    public static final String EMPTY_LAYOUT_PLACEHOLDER = "_bbspp_empty";
    private final IMiniWindowDockHost host;
    private final List<UIMiniWindow> windows = new ArrayList<>();
    private final Map<String, UIMiniWindow.MiniWindowState> sessionStates = new HashMap<>();
    private final Set<UIMiniWindow> pendingRedocks = new HashSet<>();
    private int cascade;

    public UIMiniWindowManager(IMiniWindowDockHost host)
    {
        this.host = host;
    }

    public boolean isFloating(String panelId)
    {
        return this.host.hostIsFloating(panelId);
    }

    public void undock(String panelId)
    {
        if (panelId == null
            || EMPTY_LAYOUT_PLACEHOLDER.equals(panelId)
            || this.host.hostIsFloating(panelId)
            || !this.host.hostCanUndockPanel(panelId))
        {
            return;
        }

        UIElement panel = this.host.hostGetDockPanel(panelId);
        UIElement layer = this.host.hostGetMiniWindowLayer();

        if (panel == null || layer == null)
        {
            return;
        }

        layer.setVisible(true);
        layer.setEnabled(true);

        EditorLayoutNode snapshot = this.host.hostGetLayoutRoot();
        UIMiniWindow window = null;

        try
        {
            int px = panel.area.x;
            int py = panel.area.y;
            int pw = Math.max(UIMiniWindow.DEFAULT_W, panel.area.w > 40 ? panel.area.w : UIMiniWindow.DEFAULT_W);
            int ph = Math.max(UIMiniWindow.DEFAULT_H, panel.area.h > 40 ? panel.area.h : UIMiniWindow.DEFAULT_H);

            UIMiniWindow.MiniWindowState remembered = this.sessionStates.get(panelId);

            window = this.createWindow();

            int layerX = layer.area != null ? layer.area.x : 0;
            int layerY = layer.area != null ? layer.area.y : 0;
            int x = remembered != null ? remembered.x : Math.max(24, px - layerX);
            int y = remembered != null ? remembered.y : Math.max(24, py - layerY);
            int w = remembered != null ? Math.max(UIResizeHandles.MIN_W, remembered.w) : pw;
            int h = remembered != null ? Math.max(UIMiniWindow.TITLE_H + 40, remembered.h) : ph;

            if (remembered == null)
            {
                x += this.cascade * 18;
                y += this.cascade * 18;
                this.cascade = (this.cascade + 1) % 8;
            }

            this.host.hostFloatingPanelIds().add(panelId);
            window.placeOn(layer, x, y, w, h);
            layer.add(window);
            this.windows.add(window);
            window.addPanel(panelId, panel);
            this.configureViewportInput(window);

            if (remembered != null)
            {
                window.applyState(remembered);
                window.setActive(panelId);
            }

            EditorLayoutNode root = this.host.hostGetLayoutRoot();
            String layoutPanelId = this.host.hostLayoutPanelId(panelId);

            if (root != null && layoutPanelId != null)
            {
                EditorLayoutNode next = safeRemovePanel(root, layoutPanelId);

                if (next != root)
                {
                    this.host.hostSetLayoutRoot(next);
                }
            }

            /* 先重建 dock,再 reassert 小窗内容,最后做宿主侧嵌入(editArea 检视器等) */
            this.host.hostRefreshLayout();
            this.reassertFloatingPanels();
            this.host.hostOnPanelFloated(panelId);
            this.reassertFloatingPanels();
            this.bringToFront(window);
            this.resizeWindow(window);
        }
        catch (Throwable t)
        {
            BBSFSloveCMLClient.LOGGER.warn("[MiniWindow] 面板 {} 转小窗失败,回滚到停靠状态", panelId, t);
            this.rollbackUndock(panelId, window, snapshot);
        }
    }

    public void redock(UIMiniWindow window)
    {
        if (window == null || !this.windows.contains(window) || !this.pendingRedocks.add(window))
        {
            return;
        }

        /* The restore button is a child of the window being removed. BBS 2.4 mutates its UI tree
         * immediately, so restoring inside that button's mouse callback leaves the current input
         * traversal pointing at detached elements. Always run the complete restore after the
         * current mouse dispatch has returned. */
        try
        {
            MinecraftClient.getInstance().send(() ->
            {
                this.pendingRedocks.remove(window);

                if (this.windows.contains(window))
                {
                    this.redockNow(window);
                }
            });
        }
        catch (Throwable t)
        {
            this.pendingRedocks.remove(window);
            BBSFSloveCMLClient.LOGGER.warn("[MiniWindow] 还原任务无法投递到主线程,改为立即还原", t);
            this.redockNow(window);
        }
    }

    private void redockNow(UIMiniWindow window)
    {
        if (window == null || !this.windows.contains(window))
        {
            return;
        }

        try
        {
            UIMiniWindow.MiniWindowState state = window.captureState();
            List<String> panelIds = new ArrayList<>(window.getPanelIds());
            Map<String, UIElement> restored = new HashMap<>();

            /* 合并小窗还原时，最后处理当前标签，让共享 main 最终选中用户正在看的编辑器。 */
            if (state.activeId != null && panelIds.remove(state.activeId))
            {
                panelIds.add(state.activeId);
            }

            for (String panelId : panelIds)
            {
                this.sessionStates.put(panelId, state);
                UIElement panel = window.removePanel(panelId);

                this.host.hostFloatingPanelIds().remove(panelId);

                if (panel != null)
                {
                    panel.removeFromParent();
                    panel.resetFlex();
                    restored.put(panelId, panel);
                }

                this.insertPanelBack(panelId);
            }

            this.closeWindowShell(window);

            for (String panelId : panelIds)
            {
                UIElement panel = restored.get(panelId);

                if (panel != null)
                {
                    /* BBS 的布局刷新只重算已在真实 UI 树中的面板。必须先挂回，
                     * 否则锁定布局和共享 main 会按“缺少该面板”的状态完成刷新。 */
                    this.host.hostReattachPanel(panelId, panel);
                }
            }

            for (String panelId : panelIds)
            {
                /* 摄像机/回放必须先恢复为当前编辑器。锁定布局会在刷新期间跳过
                 * 隐藏子树的尺寸计算，若刷新后才切换，面板会可见但保持空区域。 */
                this.host.hostOnPanelDocked(panelId);
            }

            /* 当前编辑器和可见状态都已恢复，再统一计算 area 与输入命中树。 */
            this.host.hostRefreshLayout();

            /* 原版布局刷新会再次执行可见性筛选。刷新后重放一次停靠钩子，
             * 确保锁定布局下的当前编辑器、时间线和输入区域使用最终 bounds。 */
            for (String panelId : panelIds)
            {
                this.host.hostOnPanelDocked(panelId);
            }

            this.reassertFloatingPanels();
            this.host.hostBringMiniWindowLayerToFront();
        }
        catch (Throwable t)
        {
            BBSFSloveCMLClient.LOGGER.warn("[MiniWindow] 还原小窗 {} 失败,尝试刷新布局兜底", window.getPanelIds(), t);

            try
            {
                this.host.hostRefreshLayout();
            }
            catch (Throwable inner)
            {
                /* 预期内:还原失败后的兜底刷新也可能失败,只记录调试日志 */
                BBSFSloveCMLClient.LOGGER.debug("[MiniWindow] 还原失败后的布局兜底刷新也失败", inner);
            }
        }
    }

    /**
     * 按 panelId 还原:找到包含该面板的小窗,整体 redock。
     * 供 dock 侧右键"还原窗口"菜单 / 顶栏切换按钮还原入口调用。
     */
    public void redockPanel(String panelId)
    {
        if (panelId == null)
        {
            return;
        }

        for (UIMiniWindow window : new ArrayList<>(this.windows))
        {
            if (window.containsPanel(panelId))
            {
                this.redock(window);

                return;
            }
        }
    }

    /** 拖动中:指针进入另一小窗时高光;松手时仅在指针落在目标窗内才合并。 */
    public void updateMergeHighlight(UIMiniWindow source)
    {
        UIMiniWindow target = this.findMergeTargetUnderCursor(source);

        for (UIMiniWindow window : this.windows)
        {
            window.setMergeHighlight(window == target);
        }
    }

    public void tryMergeOnDrop(UIMiniWindow source)
    {
        if (source == null || source.getPanelIds().isEmpty())
        {
            return;
        }

        UIMiniWindow target = this.findMergeTargetUnderCursor(source);

        for (UIMiniWindow window : this.windows)
        {
            window.setMergeHighlight(false);
        }

        if (target == null)
        {
            return;
        }

        boolean passthrough = false;

        for (String panelId : new ArrayList<>(source.getPanelIds()))
        {
            UIElement panel = source.removePanel(panelId);

            if (panel != null)
            {
                target.addPanel(panelId, panel);
            }
        }

        /* 仅当合并后仍含 preview 时才开内容穿透,避免非监视器页点穿 */
        for (String panelId : target.getPanelIds())
        {
            if (this.host.hostShouldPassthroughContentInput(panelId))
            {
                passthrough = true;
                break;
            }
        }

        target.contentInputPassthrough(passthrough);
        this.configureViewportInput(target);
        this.closeWindowShell(source);
        this.focusWindow(target);
        target.rebindAllPanels();
        this.resizeWindow(target);
    }

    private UIMiniWindow findMergeTargetUnderCursor(UIMiniWindow source)
    {
        if (source == null || source.getContext() == null)
        {
            return null;
        }

        int mx = source.getContext().mouseX;
        int my = source.getContext().mouseY;

        /* 后添加的窗口在上层,倒序优先 */
        for (int i = this.windows.size() - 1; i >= 0; i--)
        {
            UIMiniWindow other = this.windows.get(i);

            if (other == source || !other.isVisible())
            {
                continue;
            }

            if (other.area.isInside(mx, my))
            {
                return other;
            }
        }

        return null;
    }

    private void insertPanelBack(String panelId)
    {
        String layoutPanelId = this.host.hostLayoutPanelId(panelId);

        if (layoutPanelId == null || EMPTY_LAYOUT_PLACEHOLDER.equals(layoutPanelId))
        {
            return;
        }

        EditorLayoutNode root = this.host.hostGetLayoutRoot();

        if (root == null || isPlaceholderOnly(root))
        {
            this.host.hostSetLayoutRoot(new EditorLayoutNode.PanelNode(layoutPanelId));
            return;
        }

        if (containsPanel(root, layoutPanelId))
        {
            return;
        }

        String anchor = firstRealPanelId(root);

        if (anchor == null)
        {
            this.host.hostSetLayoutRoot(new EditorLayoutNode.PanelNode(panelId));
            return;
        }

        EditorLayoutNode next = EditorLayoutNode.copyWithInsertSplitAt(root, anchor, layoutPanelId, EditorLayoutNode.EDGE_RIGHT);

        if (next != null)
        {
            this.host.hostSetLayoutRoot(next);
        }
    }

    public void bringToFront(UIMiniWindow window)
    {
        if (window == null)
        {
            return;
        }

        UIElement parent = this.host.hostGetMiniWindowLayer();

        if (parent == null)
        {
            return;
        }

        window.removeFromParent();
        parent.add(window);
        parent.resize();
        this.host.hostBringMiniWindowLayerToFront();
    }

    public void clear()
    {
        this.pendingRedocks.clear();

        for (UIMiniWindow window : new ArrayList<>(this.windows))
        {
            try
            {
                this.redockNow(window);
            }
            catch (Throwable t)
            {
                BBSFSloveCMLClient.LOGGER.warn("[MiniWindow] 清理时还原小窗失败,直接关闭窗壳", t);
                this.closeWindowShell(window);
            }
        }

        this.windows.clear();
        this.host.hostFloatingPanelIds().clear();

        try
        {
            this.host.hostRefreshLayout();
        }
        catch (Throwable t)
        {
            BBSFSloveCMLClient.LOGGER.warn("[MiniWindow] 清理小窗后刷新宿主布局失败", t);
        }
    }

    public void reassertFloatingPanels()
    {
        UIElement layer = this.host.hostGetMiniWindowLayer();

        if (layer != null)
        {
            boolean hasWindows = !this.windows.isEmpty();

            layer.setVisible(hasWindows);
            layer.setEnabled(hasWindows);
        }

        if (this.windows.isEmpty())
        {
            return;
        }

        for (UIMiniWindow window : new ArrayList<>(this.windows))
        {
            /* 直接用小窗内已持有的 panel 引用,避免 main→selectedEditor 映射错位 */
            window.rebindAllPanels();
            window.setVisible(true);
            window.setEnabled(true);

            if (layer != null && window.getParent() != layer)
            {
                window.removeFromParent();
                layer.add(window);
            }

            window.resize();
        }

        this.host.hostBringMiniWindowLayerToFront();
    }

    /**
     * 切换摄像机/回放后,若 main 已浮动则把新编辑器装进对应小窗。
     */
    public void replaceFloatingPanel(String panelId, UIElement panel)
    {
        if (panelId == null || panel == null || !this.host.hostIsFloating(panelId))
        {
            return;
        }

        for (UIMiniWindow window : this.windows)
        {
            if (!window.containsPanel(panelId))
            {
                continue;
            }

            UIElement old = window.removePanel(panelId);

            if (old != null && old != panel)
            {
                old.setVisible(false);
            }

            window.addPanel(panelId, panel);
            this.configureViewportInput(window);
            window.rebindAllPanels();
            this.resizeWindow(window);
            return;
        }
    }

    public EditorLayoutNode stripFloatingFromLayout(EditorLayoutNode root)
    {
        if (root == null)
        {
            return new EditorLayoutNode.PanelNode(EMPTY_LAYOUT_PLACEHOLDER);
        }

        if (this.host.hostFloatingPanelIds().isEmpty())
        {
            return root;
        }

        EditorLayoutNode out = root;

        for (String id : new HashSet<>(this.host.hostFloatingPanelIds()))
        {
            String layoutPanelId = this.host.hostLayoutPanelId(id);

            if (layoutPanelId == null)
            {
                continue;
            }

            EditorLayoutNode next = EditorLayoutNode.copyWithRemovedPanel(out, layoutPanelId);

            if (next != null)
            {
                out = next;
            }
        }

        if (out != null && containsPanel(out, EMPTY_LAYOUT_PLACEHOLDER) && firstRealPanelId(out) != null)
        {
            EditorLayoutNode cleaned = EditorLayoutNode.copyWithRemovedPanel(out, EMPTY_LAYOUT_PLACEHOLDER);

            if (cleaned != null)
            {
                out = cleaned;
            }
        }

        return out != null ? out : new EditorLayoutNode.PanelNode(EMPTY_LAYOUT_PLACEHOLDER);
    }

    private void rollbackUndock(String panelId, UIMiniWindow window, EditorLayoutNode snapshot)
    {
        this.host.hostFloatingPanelIds().remove(panelId);

        if (window != null)
        {
            UIElement restored = window.removePanel(panelId);

            if (restored != null)
            {
                restored.removeFromParent();
                restored.resetFlex();
                this.host.hostReattachPanel(panelId, restored);
            }

            this.closeWindowShell(window);
        }

        if (snapshot != null)
        {
            this.host.hostSetLayoutRoot(snapshot);
        }

        this.host.hostOnPanelDocked(panelId);

        try
        {
            this.host.hostRefreshLayout();
        }
        catch (Throwable t)
        {
            /* 预期内:回滚时宿主布局可能已不完整,刷新失败不再扩散,只记录调试日志 */
            BBSFSloveCMLClient.LOGGER.debug("[MiniWindow] 回滚 undock 后刷新宿主布局失败", t);
        }
    }

    private static EditorLayoutNode safeRemovePanel(EditorLayoutNode root, String panelId)
    {
        if (root == null)
        {
            return new EditorLayoutNode.PanelNode(EMPTY_LAYOUT_PLACEHOLDER);
        }

        EditorLayoutNode next = EditorLayoutNode.copyWithRemovedPanel(root, panelId);

        return next != null ? next : new EditorLayoutNode.PanelNode(EMPTY_LAYOUT_PLACEHOLDER);
    }

    private UIMiniWindow createWindow()
    {
        UIMiniWindow window = new UIMiniWindow();

        window.iconLookup(this::iconOf);
        window.onRestore(this::redock);
        window.onFocus(this::focusWindow);
        window.onDragMove(this::updateMergeHighlight);
        window.onDragEnd(this::tryMergeOnDrop);

        return window;
    }

    private void focusWindow(UIMiniWindow window)
    {
        this.bringToFront(window);

        if (window != null)
        {
            this.host.hostOnPanelFocused(window.getActiveId());
        }
    }

    /** 重新合并/切换 tab 后，按窗口当前持有的 panel 绑定完整的视口输入通道。 */
    private void configureViewportInput(UIMiniWindow window)
    {
        boolean passthrough = false;

        for (String panelId : window.getPanelIds())
        {
            if (!this.host.hostShouldPassthroughContentInput(panelId))
            {
                continue;
            }

            passthrough = true;
            window.forwardViewportClick(this.host.hostViewportClickForwarder(panelId));
            window.forwardViewportScroll(this.host.hostViewportScrollForwarder(panelId));
            window.forwardViewportRelease(this.host.hostViewportReleaseForwarder(panelId));
            break;
        }

        window.contentInputPassthrough(passthrough);
    }

    private Icon iconOf(String panelId)
    {
        Icon icon = this.host.hostResolvePanelIcon(panelId);

        return icon != null ? icon : Icons.FILE;
    }

    private void closeWindowShell(UIMiniWindow window)
    {
        if (window != null)
        {
            window.removeFromParent();
            this.windows.remove(window);
        }
    }

    private void resizeWindow(UIMiniWindow window)
    {
        if (window.getParent() != null)
        {
            window.getParent().resize();
        }
        else
        {
            window.resize();
        }
    }

    private static boolean isPlaceholderOnly(EditorLayoutNode node)
    {
        return node instanceof EditorLayoutNode.PanelNode
            && EMPTY_LAYOUT_PLACEHOLDER.equals(((EditorLayoutNode.PanelNode) node).getPanelId());
    }

    private static boolean containsPanel(EditorLayoutNode node, String panelId)
    {
        if (node == null || panelId == null)
        {
            return false;
        }

        if (node instanceof EditorLayoutNode.PanelNode)
        {
            return panelId.equals(((EditorLayoutNode.PanelNode) node).getPanelId());
        }

        if (node instanceof EditorLayoutNode.StackNode)
        {
            return ((EditorLayoutNode.StackNode) node).containsPanel(panelId);
        }

        if (node instanceof EditorLayoutNode.SplitterNode s)
        {
            return containsPanel(s.getFirst(), panelId) || containsPanel(s.getSecond(), panelId);
        }

        return false;
    }

    private static String firstRealPanelId(EditorLayoutNode node)
    {
        if (node instanceof EditorLayoutNode.PanelNode)
        {
            String id = ((EditorLayoutNode.PanelNode) node).getPanelId();

            return EMPTY_LAYOUT_PLACEHOLDER.equals(id) ? null : id;
        }

        if (node instanceof EditorLayoutNode.StackNode)
        {
            for (String id : ((EditorLayoutNode.StackNode) node).getPanelIds())
            {
                if (!EMPTY_LAYOUT_PLACEHOLDER.equals(id))
                {
                    return id;
                }
            }

            return null;
        }

        if (node instanceof EditorLayoutNode.SplitterNode s)
        {
            String first = firstRealPanelId(s.getFirst());

            return first != null ? first : firstRealPanelId(s.getSecond());
        }

        return null;
    }
}
