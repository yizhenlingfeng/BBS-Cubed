package gbeic.bbsplusplus.ui.miniwindow;

import gbeic.bbsplusplus.BBSFSloveCMLClient;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 影片(UIFilmPanel)与粒子(UIDockLayout)两套小窗宿主 mixin 的公共状态与协议逻辑:
 * 浮动面板集合、布局 stash/恢复、miniLayer 挂接与置顶、分隔柄置顶、
 * 还原/转小窗的安全入口。宿主差异通过 {@link DockHostAdapter} 由各宿主注入。
 *
 * <p>时序约定:support 可在宿主构造期间被惰性创建,此时 {@link #manager()} 为 null,
 * 等价于原实现中 miniManager 尚未在 &lt;init&gt; TAIL 赋值的阶段;所有受 manager
 * 空判保护的路径保持与原实现一致的生效时机。{@link #activate()} 对应原 &lt;init&gt; TAIL。</p>
 */
public class MiniWindowDockHostSupport
{
    private final IMiniWindowDockHost host;
    private final DockHostAdapter adapter;

    private final Set<String> floatingIds = new HashSet<>();
    private final Map<String, UIElement> stashedFloating = new HashMap<>();

    private UIMiniWindowManager manager;
    private UIElement miniLayer;
    private UIDraggable prioritizedSplitterHandle;

    /**
     * 宿主特有操作,由各宿主 support 实现(或 mixin 以 lambda 提供)。
     */
    public interface DockHostAdapter
    {
        Map<String, UIElement> panelById();

        /**
         * 2.4 布局把注册表面板全部铺排,浮动面板需在布局期临时摘除注册表;
         * 2.5 UIDockLayout 以布局树为唯一事实来源,槽位自动隐藏,无需注册表
         * stash(快照式 panelById 的宿主必须返回 false)。
         */
        default boolean usesRegistryStash()
        {
            return true;
        }

        List<UIDraggable> splitterHandles();

        /** 分隔柄重新置顶时挂到哪个容器(影片: editor;粒子: dock 自身)。 */
        UIElement splitterHandleContainer();

        /** miniLayer 挂接锚点(影片: 面板自身;粒子: dock 父级,无父级回退 dock 自身)。 */
        UIElement miniLayerAnchor();

        /** stash 阶段要跳过的面板(影片: main 壳仍被 preview 等子节点依赖,不 stash)。 */
        default boolean skipStash(String panelId)
        {
            return false;
        }

        /** 分隔柄实际被重挂后的宿主回调(粒子: 还原按钮重新置顶)。 */
        default void onSplitterHandlesReattached()
        {
        }
    }

    public MiniWindowDockHostSupport(IMiniWindowDockHost host, DockHostAdapter adapter)
    {
        this.host = host;
        this.adapter = adapter;
    }

    /** 对应原 mixin &lt;init&gt; TAIL:创建 manager,此后受 manager 保护的路径才生效。 */
    public void activate()
    {
        if (this.manager == null)
        {
            this.manager = new UIMiniWindowManager(this.host);
        }
    }

    /** 宿主构造完成前(activate 之前)为 null,语义与原 @Unique miniManager 字段一致。 */
    public UIMiniWindowManager manager()
    {
        return this.manager;
    }

    public Set<String> floatingIds()
    {
        return this.floatingIds;
    }

    /** 转小窗安全入口:menu action 用,失败仅记录日志不再扩散。 */
    public void undockPanelSafely(String panelId)
    {
        try
        {
            if (this.manager != null)
            {
                this.manager.undock(panelId);
            }
        }
        catch (Throwable t)
        {
            BBSFSloveCMLClient.LOGGER.warn("[MiniWindow] 面板 {} 转小窗失败", panelId, t);
        }
    }

    /** 还原小窗安全入口:menu action / 还原按钮用。 */
    public void redockPanelSafely(String panelId)
    {
        try
        {
            if (this.manager != null)
            {
                this.manager.redockPanel(panelId);
            }
        }
        catch (Throwable t)
        {
            BBSFSloveCMLClient.LOGGER.warn("[MiniWindow] 还原小窗面板 {} 失败", panelId, t);
        }
    }

    /**
     * 惰性创建 miniLayer 并确保挂到宿主锚点。
     * 空尺寸层只提供小窗坐标系,不铺满宿主、不渲染背景,不能覆盖宿主的输入区域。
     */
    public UIElement getMiniWindowLayer()
    {
        UIElement anchor = this.adapter.miniLayerAnchor();

        if (this.miniLayer == null)
        {
            this.miniLayer = new UIElement();
            this.miniLayer.culled = false;
            this.miniLayer.w(0).h(0);
        }

        if (!this.miniLayer.hasParent() || this.miniLayer.getParent() != anchor)
        {
            this.miniLayer.removeFromParent();
            /* 不 full(anchor):保持 0×0 area,避免盖住宿主内容与点击 */
            this.miniLayer.relative(anchor).x(0).y(0).w(0).h(0);
            anchor.add(this.miniLayer);
        }

        return this.miniLayer;
    }

    /** 只创建不挂接(粒子宿主 init 时 dock 尚无父级,由 getMiniWindowLayer 懒挂)。 */
    public void createMiniLayerDetached()
    {
        if (this.miniLayer == null)
        {
            this.miniLayer = new UIElement();
            this.miniLayer.culled = false;
            this.miniLayer.w(0).h(0);
        }
    }

    /** 把浮动层重排到其父级末尾(=最上层);尚未挂接时按锚点补挂。 */
    public void ensureMiniLayerOnTop()
    {
        if (this.miniLayer == null)
        {
            return;
        }

        UIElement parent = this.miniLayer.getParent();

        if (parent == null)
        {
            this.getMiniWindowLayer();

            return;
        }

        /* 已在最顶层时不动树:BBS 的分隔柄 render→applySplitterDrag→setupFlex 链会在
         * 渲染期内触发本同步,无条件 removeFromParent+add 会踩中正在遍历的
         * children 迭代器(UIElement.render 的增强 for),导致 ConcurrentModificationException。 */
        List<IUIElement> siblings = parent.getChildren();

        if (siblings.isEmpty() || siblings.get(siblings.size() - 1) == this.miniLayer)
        {
            return;
        }

        this.miniLayer.removeFromParent();
        this.miniLayer.relative(parent).x(0).y(0).w(0).h(0);
        parent.add(this.miniLayer);
    }

    /**
     * 布局重建前:浮动面板从 panelById 暂时摘除,避免被 setupFlex 当停靠面板铺排。
     * 布局根为空时先用 manager 生成剔除浮动面板后的布局树。
     * 2.5 树驱动宿主(usesRegistryStash=false)只做布局根兜底。
     */
    public void stashFloatingBeforeLayout()
    {
        if (this.host.hostGetLayoutRoot() == null && this.manager != null)
        {
            this.host.hostSetLayoutRoot(this.manager.stripFloatingFromLayout(null));
        }

        this.stashedFloating.clear();

        if (!this.adapter.usesRegistryStash())
        {
            return;
        }

        Map<String, UIElement> panelById = this.adapter.panelById();

        for (String id : new HashSet<>(this.floatingIds))
        {
            if (this.adapter.skipStash(id))
            {
                continue;
            }

            UIElement panel = panelById.remove(id);

            if (panel != null)
            {
                this.stashedFloating.put(id, panel);
            }
        }
    }

    /**
     * 布局重建后:stash 的浮动面板放回 panelById。
     *
     * @param reattachDetachedDocked 粒子宿主:把仍 detached 的停靠面板挂回 dock(还原后常见)
     */
    public void restoreStashedAfterLayout(boolean reattachDetachedDocked)
    {
        Map<String, UIElement> panelById = this.adapter.panelById();

        try
        {
            if (this.adapter.usesRegistryStash())
            {
                panelById.putAll(this.stashedFloating);
            }

            if (reattachDetachedDocked)
            {
                for (Map.Entry<String, UIElement> entry : panelById.entrySet())
                {
                    if (this.floatingIds.contains(entry.getKey()))
                    {
                        continue;
                    }

                    UIElement panel = entry.getValue();

                    if (panel != null && panel.getParent() == null)
                    {
                        this.host.hostReattachPanel(entry.getKey(), panel);
                        panel.setVisible(true);
                        panel.setEnabled(true);
                    }
                }
            }
        }
        finally
        {
            this.stashedFloating.clear();
        }
    }

    /** Keep splitter hitboxes above the panel contents and panel drag strips. */
    public void prioritizeSplitterHandles()
    {
        List<UIDraggable> handles = this.adapter.splitterHandles();

        if (handles == null || handles.isEmpty())
        {
            this.prioritizedSplitterHandle = null;

            return;
        }

        UIDraggable last = handles.get(handles.size() - 1);

        if (last == this.prioritizedSplitterHandle)
        {
            return;
        }

        UIElement container = this.adapter.splitterHandleContainer();

        for (UIDraggable handle : handles)
        {
            handle.removeFromParent();
            container.add(handle);
        }

        this.prioritizedSplitterHandle = last;
        this.adapter.onSplitterHandlesReattached();
    }

    /** hostClearMiniWindows 公共部分:关全部小窗并清空浮动/stash 状态。 */
    public void clearMiniWindows()
    {
        if (this.manager != null)
        {
            this.manager.clear();
        }

        this.floatingIds.clear();
        this.stashedFloating.clear();
    }
}
