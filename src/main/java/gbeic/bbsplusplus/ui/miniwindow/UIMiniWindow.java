package gbeic.bbsplusplus.ui.miniwindow;

import gbeic.bbsplusplus.ui.forms.SnowUIKeys;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.resizers.Flex;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 非模态浮动小窗。
 * 顶栏: tabs 左 + 还原/折叠 右。
 * mousePropagation=PASS: 释放不吞,相机能松手; 空白点击在 subMouseClicked 里显式拦截。
 * 渲染时仅 clip content,顶栏 tabs/icons 不被裁掉。
 */
public class UIMiniWindow extends UIElement
{
    public static final int TITLE_H = 20;
    public static final int TAB_W = 20;
    public static final int DEFAULT_W = 320;
    public static final int DEFAULT_H = 240;
    public static final int EDGE_SLACK = 48;

    public final UIElement titleBar;
    public final UIElement leftTabs;
    public final UIElement icons;
    public final UIElement content;
    public final UIIcon restore;
    public final UIIcon collapse;

    private final Map<String, PanelEntry> entries = new LinkedHashMap<>();
    private final List<UIIcon> tabButtons = new ArrayList<>();

    private String activeId;
    private boolean collapsed;
    private int expandedH = DEFAULT_H;
    private boolean moving;
    private int lastX;
    private int lastY;
    private Consumer<UIMiniWindow> onRestore;
    private Consumer<UIMiniWindow> onFocus;
    private Consumer<UIMiniWindow> onDragEnd;
    private Consumer<UIMiniWindow> onDragMove;
    private Function<String, Icon> iconLookup = (id) -> Icons.FILE;
    private boolean mergeHighlight;
    /** 监视器等:内容区未命中子控件的点击/滚轮转发给相机 orbitUI(而非 PASS 穿透后方 UI) */
    private boolean contentInputPassthrough;
    /**
     * 视口输入转发器:监视器小窗内容区空白点击/滚轮,显式转发给相机(Dashboard orbitUI),
     * 由 UIMiniWindow 自己消费事件(true),避免 PASS 穿透到后方 dock UI,同时保证飞行 orbit 正常。
     * 返回 true=相机消费了该输入(小窗 return true 大家都拿不到后方);false=未消费(小窗仍 return true 拦截)。
     */
    private java.util.function.Predicate<UIContext> forwardViewportClick;
    private java.util.function.Predicate<UIContext> forwardViewportScroll;
    /**
     * 视口释放转发器:orbit 启动后释放鼠标时主动调,确保 orbitUI.mouseReleased → orbit.release 清状态。
     * 防止中键 pan 卡住持续抢占左右键。
     */
    private java.util.function.Consumer<UIContext> forwardViewportRelease;

    public UIMiniWindow()
    {
        this.mouseEventPropagataion(EventPropagation.PASS);
        this.markContainer();

        this.titleBar = new UIElement();
        this.leftTabs = new UIElement();
        this.icons = new UIElement();
        this.content = new UIElement();
        /* content 本身不吞事件,子面板各自处理;窗级空白拦截在 subMouseClicked */
        this.content.mouseEventPropagataion(EventPropagation.PASS);

        this.restore = new UIIcon(Icons.OUT, (b) ->
        {
            if (this.onRestore != null)
            {
                this.onRestore.accept(this);
            }
        });
        this.restore.tooltip(SnowUIKeys.MINI_WINDOW_RESTORE, Direction.BOTTOM);

        this.collapse = new UIIcon(() -> this.collapsed ? Icons.MAXIMIZE : Icons.MINIMIZE, (b) -> this.toggleCollapsed());
        this.collapse.tooltip(SnowUIKeys.MINI_WINDOW_COLLAPSE, Direction.BOTTOM);

        this.titleBar.relative(this).xy(0, 0).w(1F).h(TITLE_H);
        this.leftTabs.relative(this).x(0).y(0).h(TITLE_H).row(0);
        this.icons.relative(this).x(1F, -40).y(0).w(40).h(TITLE_H).row(0);
        this.content.relative(this).x(0).y(TITLE_H).w(1F).h(1F, -TITLE_H);

        this.icons.add(this.restore, this.collapse);
        /* 先挂缩放柄,再挂顶栏 chrome,保证标题/图标优先命中 */
        UIResizeHandles.attach(this, UIResizeHandles.MIN_W, TITLE_H + 40, TITLE_H);
        this.add(this.titleBar, this.content, this.leftTabs, this.icons);
    }

    public UIMiniWindow onRestore(Consumer<UIMiniWindow> callback)
    {
        this.onRestore = callback;
        return this;
    }

    public UIMiniWindow onFocus(Consumer<UIMiniWindow> callback)
    {
        this.onFocus = callback;
        return this;
    }

    public UIMiniWindow onDragEnd(Consumer<UIMiniWindow> callback)
    {
        this.onDragEnd = callback;
        return this;
    }

    public UIMiniWindow onDragMove(Consumer<UIMiniWindow> callback)
    {
        this.onDragMove = callback;
        return this;
    }

    public UIMiniWindow iconLookup(Function<String, Icon> iconLookup)
    {
        this.iconLookup = iconLookup != null ? iconLookup : (id) -> Icons.FILE;
        return this;
    }

    public UIMiniWindow contentInputPassthrough(boolean passthrough)
    {
        this.contentInputPassthrough = passthrough;
        return this;
    }

    public boolean isContentInputPassthrough()
    {
        return this.contentInputPassthrough;
    }

    /**
     * 设置视口输入转发器:仅监视器(preview)小窗用,film 宿主在 undock preview 时
     * 传入把点击/滚轮转给 dashboard.orbitUI 的回调。返回 true 表示相机已消费。
     */
    public UIMiniWindow forwardViewportClick(java.util.function.Predicate<UIContext> forwarder)
    {
        this.forwardViewportClick = forwarder;

        return this;
    }

    public UIMiniWindow forwardViewportScroll(java.util.function.Predicate<UIContext> forwarder)
    {
        this.forwardViewportScroll = forwarder;

        return this;
    }

    /**
     * 设置视口释放转发器:监视器小窗释放鼠标时主动调 orbitUI.mouseReleased 清状态。
     */
    public UIMiniWindow forwardViewportRelease(java.util.function.Consumer<UIContext> forwarder)
    {
        this.forwardViewportRelease = forwarder;

        return this;
    }

    public void setMergeHighlight(boolean highlight)
    {
        this.mergeHighlight = highlight;
    }

    public boolean isMergeHighlight()
    {
        return this.mergeHighlight;
    }

    public boolean isCollapsed()
    {
        return this.collapsed;
    }

    public boolean isMoving()
    {
        return this.moving;
    }

    public String getActiveId()
    {
        return this.activeId;
    }

    public List<String> getPanelIds()
    {
        return new ArrayList<>(this.entries.keySet());
    }

    public boolean containsPanel(String panelId)
    {
        return this.entries.containsKey(panelId);
    }

    public void placeOn(UIElement host, int x, int y, int w, int h)
    {
        this.setVisible(true);
        this.setEnabled(true);
        this.relative(host)
            .x(Math.max(0, x))
            .y(Math.max(0, y))
            .w(Math.max(UIResizeHandles.MIN_W, w))
            .h(Math.max(TITLE_H + 40, h));
        this.expandedH = Math.max(TITLE_H + 40, h);
        this.content.setVisible(true);
        this.leftTabs.setVisible(true);
        this.collapsed = false;
    }

    public void addPanel(String panelId, UIElement panel)
    {
        if (panelId == null || panel == null || this.entries.containsKey(panelId))
        {
            return;
        }

        this.bindPanelToContent(panel);
        this.entries.put(panelId, new PanelEntry(panelId, panel));
        this.rebuildTabs();

        if (this.activeId == null)
        {
            this.setActive(panelId);
        }
        else
        {
            panel.setVisible(false);
            this.resize();
        }
    }

    public UIElement removePanel(String panelId)
    {
        PanelEntry entry = this.entries.remove(panelId);

        if (entry == null)
        {
            return null;
        }

        entry.panel.removeFromParent();
        entry.panel.setVisible(true);
        this.rebuildTabs();

        if (panelId.equals(this.activeId))
        {
            this.activeId = null;

            if (!this.entries.isEmpty())
            {
                this.setActive(this.entries.keySet().iterator().next());
            }
        }

        return entry.panel;
    }

    public void setActive(String panelId)
    {
        if (!this.entries.containsKey(panelId))
        {
            return;
        }

        this.activeId = panelId;

        for (PanelEntry entry : this.entries.values())
        {
            boolean show = entry.id.equals(panelId) && !this.collapsed;

            if (show)
            {
                this.bindPanelToContent(entry.panel);
            }

            entry.panel.setVisible(show);
        }

        this.rebuildTabs();
        this.resize();
    }

    public void toggleCollapsed()
    {
        this.setCollapsed(!this.collapsed);
    }

    public void setCollapsed(boolean collapsed)
    {
        if (this.collapsed == collapsed)
        {
            return;
        }

        /* 必须先更新标志,否则 expand 时 setActive 仍按 collapsed=true 把面板隐藏 */
        this.collapsed = collapsed;

        if (collapsed)
        {
            this.expandedH = Math.max(TITLE_H + 40, this.area.h > 0 ? this.area.h : Math.max(this.getFlex().h.offset, DEFAULT_H));
            this.getFlex().h.set(0, TITLE_H);
            this.content.setVisible(false);

            for (PanelEntry entry : this.entries.values())
            {
                entry.panel.setVisible(false);
            }
        }
        else
        {
            this.getFlex().h.set(0, Math.max(TITLE_H + 40, this.expandedH));
            this.content.setVisible(true);

            if (this.activeId != null && this.entries.containsKey(this.activeId))
            {
                this.setActive(this.activeId);
            }
            else if (!this.entries.isEmpty())
            {
                this.setActive(this.entries.keySet().iterator().next());
            }

            this.rebindAllPanels();
        }

        this.leftTabs.setVisible(true);

        if (this.getParent() != null)
        {
            this.getParent().resize();
        }
        else
        {
            this.resize();
        }
    }

    public MiniWindowState captureState()
    {
        Flex flex = this.getFlex();
        int w = Math.max(this.area.w, flex.w.offset);
        int h = this.collapsed ? this.expandedH : Math.max(this.area.h, flex.h.offset);

        return new MiniWindowState(flex.x.offset, flex.y.offset, h, w, this.collapsed,
            new ArrayList<>(this.entries.keySet()), this.activeId);
    }

    public void applyState(MiniWindowState state)
    {
        if (state == null)
        {
            return;
        }

        this.getFlex().x.offset = state.x;
        this.getFlex().y.offset = state.y;
        this.getFlex().w.set(0, Math.max(UIResizeHandles.MIN_W, state.w));
        this.expandedH = Math.max(TITLE_H + 40, state.h);
        this.collapsed = state.collapsed;
        this.getFlex().h.set(0, state.collapsed ? TITLE_H : this.expandedH);
        this.content.setVisible(!state.collapsed);
        this.leftTabs.setVisible(true);

        if (state.activeId != null && this.entries.containsKey(state.activeId))
        {
            this.setActive(state.activeId);
        }
        else if (!state.collapsed && !this.entries.isEmpty())
        {
            this.setActive(this.entries.keySet().iterator().next());
        }
    }

    public void rebindAllPanels()
    {
        for (PanelEntry entry : this.entries.values())
        {
            boolean show = entry.id.equals(this.activeId) && !this.collapsed;

            this.bindPanelToContent(entry.panel);
            entry.panel.setVisible(show);
        }

        this.content.setVisible(!this.collapsed);
        this.resize();
    }

    private void bindPanelToContent(UIElement panel)
    {
        panel.removeFromParent();
        panel.resetFlex();
        panel.full(this.content);
        this.content.add(panel);
    }

    private void rebuildTabs()
    {
        for (UIIcon tab : this.tabButtons)
        {
            tab.removeFromParent();
        }

        this.tabButtons.clear();
        this.leftTabs.removeAll();

        int count = 0;

        for (String id : this.entries.keySet())
        {
            final String panelId = id;
            /* Supplier 图标:布局后/图标表就绪后仍能取到正确图,避免首帧空 icon */
            UIIcon tab = new UIIcon(() ->
            {
                Icon icon = this.iconLookup.apply(panelId);

                return icon != null ? icon : Icons.FILE;
            }, (b) ->
            {
                this.setActive(panelId);

                if (this.onFocus != null)
                {
                    this.onFocus.accept(this);
                }
            });

            tab.wh(TAB_W, TITLE_H);
            this.tabButtons.add(tab);
            this.leftTabs.add(tab);
            count++;
        }

        this.leftTabs.w(Math.max(TAB_W, count * TAB_W));
        this.leftTabs.setVisible(true);
        this.icons.setVisible(true);
        this.leftTabs.resize();
        this.icons.resize();
    }

    private void softClampToParent()
    {
        UIElement parent = this.getParent();

        if (parent == null || parent.area.w <= 0 || parent.area.h <= 0)
        {
            return;
        }

        Flex flex = this.getFlex();
        int w = Math.max(this.area.w > 0 ? this.area.w : flex.w.offset, UIResizeHandles.MIN_W);
        int minX = EDGE_SLACK - w;
        int maxX = parent.area.w - EDGE_SLACK;
        int minY = 0;
        int maxY = Math.max(0, parent.area.h - Math.min(TITLE_H, EDGE_SLACK));

        flex.x.offset = MathUtils.clamp(flex.x.offset, minX, maxX);
        flex.y.offset = MathUtils.clamp(flex.y.offset, minY, maxY);
    }

    private boolean isTitleDragArea(UIContext context)
    {
        if (context.mouseX < this.area.x || context.mouseX >= this.area.ex()
            || context.mouseY < this.area.y || context.mouseY >= this.area.y + TITLE_H)
        {
            return false;
        }

        return !this.icons.area.isInside(context) && !this.leftTabs.area.isInside(context);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return super.subMouseClicked(context);
        }

        if (context.mouseButton == 0 && this.isTitleDragArea(context))
        {
            if (Window.isCtrlPressed())
            {
                this.getFlex().x.offset = 40;
                this.getFlex().y.offset = 40;

                if (this.getParent() != null)
                {
                    this.getParent().resize();
                }

                return true;
            }

            this.moving = true;
            this.lastX = context.mouseX;
            this.lastY = context.mouseY;

            return true;
        }

        /* 监视器小窗内容区空白:显式转发给相机 orbitUI,而非 PASS 穿透后方 dock。
         * 相机消费与否,小窗都 return true 拦截,后方 UI 收不到(Bug1 保持修复)。
         * 相机在飞行/镜头/自由模式都能正常启动 orbit(方案A 闭环)。 */
        if (this.contentInputPassthrough
            && "preview".equals(this.activeId)
            && this.content.area.isInside(context)
            && this.forwardViewportClick != null)
        {
            this.forwardViewportClick.test(context);
        }

        /* 窗内空白一律拦截,不穿透到后方 UI(含监视器);子控件已优先处理 */
        return true;
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return false;
        }

        /* 监视器飞行滚轮:转发给 orbitUI,小窗仍 return true 不穿透后方 */
        if (this.contentInputPassthrough
            && "preview".equals(this.activeId)
            && this.content.area.isInside(context)
            && this.forwardViewportScroll != null)
        {
            this.forwardViewportScroll.test(context);
        }

        /* 窗体上的滚轮不穿透到后方 UI */
        return true;
    }

    @Override
    protected IUIElement childrenMouseClicked(UIContext context)
    {
        /* 子控件（时间线/属性输入框）消费事件时，原来的 subMouseClicked 不会执行，
         * 导致点击后台小窗无法置顶。先聚焦，再让正常的子控件分发继续。 */
        if (this.area.isInside(context) && this.onFocus != null)
        {
            this.onFocus.accept(this);
        }

        return super.childrenMouseClicked(context);
    }

    @Override
    protected IUIElement childrenMouseReleased(UIContext context)
    {
        IUIElement handled = super.childrenMouseReleased(context);

        /* 子控件可能消费 release；相机释放不能依赖 PASS 链路。 */
        this.forwardViewportRelease(context);

        return handled;
    }

    private void forwardViewportRelease(UIContext context)
    {
        if (this.contentInputPassthrough
            && "preview".equals(this.activeId)
            && this.forwardViewportRelease != null)
        {
            this.forwardViewportRelease.accept(context);
        }
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        boolean wasMoving = this.moving;

        this.moving = false;
        this.mergeHighlight = false;

        if (wasMoving && this.onDragEnd != null)
        {
            this.onDragEnd.accept(this);
        }

        /* false + PASS: 不吞释放,相机 orbit 能 stop */
        return false;
    }

    @Override
    public void render(UIContext context)
    {
        if (this.moving && (context.mouseX != this.lastX || context.mouseY != this.lastY))
        {
            int dx = context.mouseX - this.lastX;
            int dy = context.mouseY - this.lastY;

            this.getFlex().x.offset += dx;
            this.getFlex().y.offset += dy;
            this.softClampToParent();

            if (this.getParent() != null)
            {
                this.getParent().resize();
            }
            else
            {
                this.resize();
            }

            this.lastX = context.mouseX;
            this.lastY = context.mouseY;

            if (this.onDragMove != null)
            {
                this.onDragMove.accept(this);
            }
        }

        this.renderChrome(context);

        /*
         * 只裁剪 content,不裁剪 title tabs/icons。
         * 之前 super.render 整体 clip 到 content.area,顶栏图标在裁剪外导致不可见,
         * 折叠后走无 clip 分支又恢复——与报告一致。
         */
        for (mchorse.bbs_mod.ui.framework.elements.IUIElement element : this.getChildren())
        {
            if (!element.isVisible() || !element.canBeRendered(context.getViewport()))
            {
                continue;
            }

            if (element == this.content && !this.collapsed && this.content.area.w > 0 && this.content.area.h > 0)
            {
                context.batcher.clip(this.content.area, context);
                element.render(context);
                context.batcher.unclip(context);
            }
            else
            {
                element.render(context);
            }
        }

        this.renderTabHighlights(context);

        if (this.mergeHighlight)
        {
            int fill = BBSSettings.primaryColor(Colors.A25);
            int border = BBSSettings.primaryColor(Colors.A50);

            this.area.render(context.batcher, fill);
            int t = 2;

            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + t, border);
            context.batcher.box(this.area.x, this.area.ey() - t, this.area.ex(), this.area.ey(), border);
            context.batcher.box(this.area.x, this.area.y, this.area.x + t, this.area.ey(), border);
            context.batcher.box(this.area.ex() - t, this.area.y, this.area.ex(), this.area.ey(), border);
        }
    }

    private void renderChrome(UIContext context)
    {
        context.batcher.dropShadow(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 10,
            BBSSettings.panelShadowOpaqueColor(), BBSSettings.panelShadowTransparentColor());
        this.area.render(context.batcher, BBSSettings.raisedSurface());
        this.titleBar.area.render(context.batcher, BBSSettings.chromeSurface());

        if (!this.collapsed && this.content.area.w > 0 && this.content.area.h > 0)
        {
            this.content.area.render(context.batcher, BBSSettings.deepSurface());
        }

        if (this.isTitleDragArea(context))
        {
            context.batcher.icon(Icons.ALL_DIRECTIONS, Colors.GRAY, this.area.mx(), this.area.y + TITLE_H / 2, 0.5F, 0.5F);
        }
    }

    private void renderTabHighlights(UIContext context)
    {
        List<String> ids = new ArrayList<>(this.entries.keySet());

        for (int i = 0; i < this.tabButtons.size() && i < ids.size(); i++)
        {
            if (ids.get(i).equals(this.activeId))
            {
                UIDashboardPanels.renderHighlight(context.batcher, this.tabButtons.get(i).area, Direction.BOTTOM);
            }
        }
    }

    private static final class PanelEntry
    {
        final String id;
        final UIElement panel;

        PanelEntry(String id, UIElement panel)
        {
            this.id = id;
            this.panel = panel;
        }
    }

    public static final class MiniWindowState
    {
        public final int x, y, h, w;
        public final boolean collapsed;
        public final List<String> panelIds;
        public final String activeId;

        public MiniWindowState(int x, int y, int h, int w, boolean collapsed, List<String> panelIds, String activeId)
        {
            this.x = x;
            this.y = y;
            this.h = h;
            this.w = w;
            this.collapsed = collapsed;
            this.panelIds = panelIds;
            this.activeId = activeId;
        }
    }
}
