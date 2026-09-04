package gbeic.bbsplusplus.ui.miniwindow;

import gbeic.bbsplusplus.BBSFSloveCMLClient;
import gbeic.bbsplusplus.api.UIDockLayoutAccessor;
import gbeic.bbsplusplus.ui.forms.SnowUIKeys;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.layout.UIDockLayout;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * UIDockLayout 小窗宿主的业务逻辑,从 UIDockLayoutMiniWindowMixin 下沉。
 * 小窗挂在 dock 自身之上(miniLayer 懒挂到 dock 父级)。
 *
 * <p>2.5 适配:面板注册表由 slotById 承载(UIDockSlot 包装器),布局树为唯一
 * 事实来源——浮动面板由 ensureRegisteredPanels RETURN 钩子从树中剥离后,
 * 原版 updateTabVisibility 会自动隐藏对应槽位,不再需要 2.4 的注册表
 * stash/恢复。2.5 起影片/动画状态编辑器也使用 UIDockLayout,本宿主对
 * 所有 dock 实例生效。</p>
 *
 * <p>粒子特有部分:常驻"还原窗口"按钮、dock 背景右键还原菜单、透明底 dock
 * (动画状态监视器)的底色绘制与右键穿透。公共部分见 {@link MiniWindowDockHostSupport}。</p>
 */
public class UIDockLayoutMiniWindowSupport implements MiniWindowDockHostSupport.DockHostAdapter
{
    private final UIDockLayout dock;
    private final MiniWindowDockHostSupport common;
    private final Supplier<List<UIDraggable>> splitterHandles;

    private UIIcon restoreButton;
    private boolean transparentBase;

    public UIDockLayoutMiniWindowSupport(UIDockLayout dock, IMiniWindowDockHost host,
        Supplier<List<UIDraggable>> splitterHandles)
    {
        this.dock = dock;
        this.splitterHandles = splitterHandles;
        this.common = new MiniWindowDockHostSupport(host, this);
    }

    public MiniWindowDockHostSupport common()
    {
        return this.common;
    }

    public UIMiniWindowManager manager()
    {
        return this.common.manager();
    }

    /* ---------- DockHostAdapter ---------- */

    /**
     * 只读快照:id → 面板本体(2.5 走原版 getPanel)。
     * 2.5 布局树驱动一切,对快照的写操作无效果也无必要(usesRegistryStash=false)。
     */
    @Override
    public Map<String, UIElement> panelById()
    {
        Map<String, UIElement> out = new LinkedHashMap<>();

        for (String id : this.slotIds())
        {
            UIElement panel = this.dock.getPanel(id);

            if (panel != null)
            {
                out.put(id, panel);
            }
        }

        return out;
    }

    @Override
    public boolean usesRegistryStash()
    {
        /* 2.5:浮动槽位由布局树剥离自动隐藏,无需摘除注册表项。 */
        return false;
    }

    @Override
    public List<UIDraggable> splitterHandles()
    {
        return this.splitterHandles.get();
    }

    @Override
    public UIElement splitterHandleContainer()
    {
        return this.dock;
    }

    @Override
    public UIElement miniLayerAnchor()
    {
        /* miniLayer 挂到 dock 的父级(editor)而非 dock 自身:
         * 与 dock 平级,且 ensureMiniLayerOnTop 把它追到末尾=最上层,
         * 避免被 dock 内子节点盖住小窗标题栏还原图标(粒子宿主历史顽固 bug 根因)。
         * miniLayer 自身 area 设为 0×0,不铺满 host,不渲染背景,不挡 dock 渲染/点击。 */
        UIElement host = this.dock.getParent();

        return host == null ? this.dock : host; /* init/未挂载回退 */
    }

    @Override
    public void onSplitterHandlesReattached()
    {
        this.repositionRestoreButton();
    }

    /* ---------- 注入回调对应的业务实现 ---------- */

    /** 对应 &lt;init&gt; TAIL:创建 manager、备好 miniLayer、注册背景右键菜单与还原按钮。 */
    public void activate()
    {
        this.common.activate();
        /* miniLayer 不在 init 挂:self 此时无父级(editor 尚未 add(dock))。
         * 由 hostGetMiniWindowLayer 懒挂到 dock 的父级(editor),与 dock 平级;
         * 关键:miniLayer **不铺满 host、不设 w/h=0**(空 area),仅作小窗容器,
         * 既不参与渲染(无背景/无 dropShadow),也不挡 dock 的点击与渲染——
         * 避免粒子历史 bug:miniLayer 铺满 editor 盖住 dock 内列表与按键图层。 */
        this.common.createMiniLayerDetached();

        /* dock 自身的右键背景菜单:列出已浮动 panel 的"还原窗口"。
         * 挂在 dock 而非易被 updateTabVisibility 隐藏的子手柄上,survives 所有刷新。 */
        this.dock.context(this::buildDockRestoreMenu);

        this.ensureRestoreButton();
    }

    private void buildDockRestoreMenu(ContextMenuManager menu)
    {
        if (this.common.floatingIds().isEmpty()
            || this.isTransparentPreviewHit(this.dock.getContext()))
        {
            return;
        }

        for (String panelId : new ArrayList<>(this.common.floatingIds()))
        {
            String id = panelId;
            Icon icon = this.hostResolvePanelIcon(id);

            menu.action(icon == null ? Icons.FILE : icon, SnowUIKeys.MINI_WINDOW_RESTORE, () -> this.common.redockPanelSafely(id));
        }
    }

    /**
     * The animation-state monitor is a transparent dock panel backed by the
     * shared form renderer. Its right click must pass through to viewport bone
     * picking instead of opening the dock's floating-window restore menu.
     */
    private boolean isTransparentPreviewHit(UIContext context)
    {
        if (!this.transparentBase || context == null)
        {
            return false;
        }

        for (UIElement panel : this.panelById().values())
        {
            if (panel instanceof INonFloatingDockPanel
                && panel.isVisible()
                && panel.area.isInside(context))
            {
                return true;
            }
        }

        return false;
    }

    public void setTransparentBase(boolean transparent)
    {
        this.transparentBase = transparent;
    }

    /**
     * 对应 renderCanvas HEAD:透明底 dock 取消整块底板绘制即可——
     * 2.5 槽位(UIDockSlot)自带 deepSurface,frameless 面板(监视器)不画底。
     *
     * @return true 表示已接管绘制,注入点应 cancel
     */
    public boolean renderTransparentDockSurfaces(UIContext context)
    {
        return this.transparentBase;
    }

    /**
     * 在 dock 父级(editor)里挂一个常驻"还原窗口"图标按钮(右键亦弹列表),
     * 当小窗铺满 dock 导致 dock 右键命中失效时,仍能从顶栏还原。
     * 仅在 floatingIds 非空时可见。
     */
    private void ensureRestoreButton()
    {
        if (this.restoreButton != null)
        {
            return;
        }

        this.restoreButton = new UIIcon(Icons.OUT, (b) ->
        {
            /* 左键:若有小窗,直接还原第一个(快捷) */
            if (this.common.floatingIds().isEmpty() || this.common.manager() == null)
            {
                return;
            }

            try
            {
                String first = this.common.floatingIds().iterator().next();

                this.common.manager().redockPanel(first);
            }
            catch (Throwable t)
            {
                BBSFSloveCMLClient.LOGGER.warn("[MiniWindow] 还原按钮快捷还原第一个小窗失败", t);
            }
        });

        this.restoreButton.tooltip(SnowUIKeys.MINI_WINDOW_RESTORE, Direction.BOTTOM);
        this.restoreButton.context((menu) ->
        {
            if (this.common.floatingIds().isEmpty())
            {
                return;
            }

            for (String panelId : new ArrayList<>(this.common.floatingIds()))
            {
                String id = panelId;
                Icon icon = this.hostResolvePanelIcon(id);

                menu.action(icon == null ? Icons.FILE : icon, SnowUIKeys.MINI_WINDOW_RESTORE, () -> this.common.redockPanelSafely(id));
            }
        });

        /* 挂到 dock 自身顶栏位置(相对 dock 右上),不铺满不挡 dock 内容 */
        this.restoreButton.relative(this.dock).x(1F, -20).y(0).wh(20, 20);
        this.dock.add(this.restoreButton);
    }

    /** 对应 createPanelDragHandle RETURN:给拖动手柄补"转小窗"右键菜单。 */
    public void attachHandleContext(UIDraggable handle, String panelId)
    {
        if (handle == null || panelId == null)
        {
            return;
        }

        handle.context((menu) ->
        {
            if (this.dock.isLocked()
                || this.common.floatingIds().contains(panelId)
                || !this.hostCanUndockPanel(panelId))
            {
                return;
            }

            menu.action(Icons.EXTERNAL, SnowUIKeys.MINI_WINDOW_CONVERT, () -> this.common.undockPanelSafely(panelId));
        });
    }

    /** 对应 setupFlex TAIL:恢复 stash、挂回 detached 停靠面板并重整 z-order。 */
    public void restoreFloatingAfterLayout()
    {
        this.common.restoreStashedAfterLayout(true);

        if (this.common.manager() != null)
        {
            this.common.manager().reassertFloatingPanels();
        }

        this.common.prioritizeSplitterHandles();
        this.common.ensureMiniLayerOnTop();
        this.refreshRestoreButtonVisibility();
    }

    /** 还原按钮可见性:仅 floatingIds 非空时显示。 */
    private void refreshRestoreButtonVisibility()
    {
        if (this.restoreButton != null)
        {
            this.restoreButton.setVisible(!this.common.floatingIds().isEmpty());
        }
    }

    /** 还原按钮在 add(panel) 后可能被挤出 dock 子节点末尾,重新 anchor 到 dock 右上。 */
    private void repositionRestoreButton()
    {
        if (this.restoreButton != null && this.restoreButton.getParent() == this.dock)
        {
            /* mount() 会在构造注入之后追加面板/拖动句柄,需把常驻按钮送回最上层。 */
            this.restoreButton.removeFromParent();
            this.restoreButton.relative(this.dock).x(1F, -20).y(0).wh(20, 20);
            this.dock.add(this.restoreButton);
        }
    }

    /* ---------- IMiniWindowDockHost 委托实现 ---------- */

    public UIElement hostGetDockPanel(String panelId)
    {
        return this.dock.getPanel(panelId);
    }

    public Icon hostResolvePanelIcon(String panelId)
    {
        Map<String, Icon> icons = this.access().bbspp_cml$getIconById();
        Icon icon = icons != null ? icons.get(panelId) : null;

        return icon != null ? icon : Icons.FILE;
    }

    public boolean hostCanUndockPanel(String panelId)
    {
        UIElement panel = this.dock.getPanel(panelId);

        return panel != null && !(panel instanceof INonFloatingDockPanel);
    }

    public EditorLayoutNode hostGetLayoutRoot()
    {
        return this.dock.getLayoutRoot();
    }

    public void hostSetLayoutRoot(EditorLayoutNode root)
    {
        this.dock.applyLayoutRoot(root);
    }

    public void hostRefreshLayout()
    {
        /* false=完整重建 flex; 必须 resize 才能把 area 从浮动矩形刷成布局格 */
        this.dock.setupFlex(false);
        this.dock.resize();
        this.dock.resize();
        /* 兜底:确保小窗浮动层仍在最上层(editor 末尾子节点) */
        this.common.ensureMiniLayerOnTop();
        this.refreshRestoreButtonVisibility();
        this.repositionRestoreButton();
    }

    public void hostClearMiniWindows()
    {
        this.common.clearMiniWindows();
    }

    public void hostReattachPanel(String panelId, UIElement panel)
    {
        if (panel == null)
        {
            return;
        }

        UIElement slot = this.slotById().get(panelId);

        panel.removeFromParent();
        panel.setVisible(true);
        panel.setEnabled(true);

        if (slot != null)
        {
            /* 2.5:面板属于 UIDockSlot 包装器(原版构造顺序 panel → dragHandle),
             * 重挂回 dragHandle 之前,恢复槽内命中/绘制顺序。 */
            panel.relative(slot).x(0F).y(0F).w(1F).h(1F);

            UIElement before = null;

            for (IUIElement child : slot.getChildren())
            {
                if (child instanceof UIElement element && element != panel)
                {
                    before = element;

                    break;
                }
            }

            if (before != null)
            {
                slot.addBefore(before, panel);
            }
            else
            {
                slot.add(panel);
            }
        }
        else
        {
            this.dock.add(panel);
        }
    }

    public void hostOnPanelDocked(String panelId)
    {
        UIElement panel = this.dock.getPanel(panelId);

        if (panel == null)
        {
            return;
        }

        if (panel.getParent() == null)
        {
            this.hostReattachPanel(panelId, panel);
        }

        panel.setVisible(true);
        panel.setEnabled(true);
        /* 父 dock 先有 area,再刷子面板 */
        this.dock.resize();
        panel.resize();
    }

    private Iterable<String> slotIds()
    {
        return this.slotById().keySet();
    }

    private Map<String, UIElement> slotById()
    {
        return this.access().bbspp_cml$getSlotById();
    }

    private UIDockLayoutAccessor access()
    {
        return (UIDockLayoutAccessor) (Object) this.dock;
    }
}
