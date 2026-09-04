package gbeic.bbsplusplus.ui.miniwindow;

import gbeic.bbsplusplus.BBSFSloveCMLClient;
import gbeic.bbsplusplus.api.UIClipsPanelAccessor;
import gbeic.bbsplusplus.api.UIFilmPanelLayoutAccessor;
import gbeic.bbsplusplus.api.UIDockLayoutAccessor;
import gbeic.bbsplusplus.ui.forms.SnowUIKeys;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 影片小窗宿主(UIFilmPanel)的业务逻辑,从 UIFilmPanelMiniWindowMixin 下沉。
 * miniLayer 挂在 UIFilmPanel 根上,小窗保持在 editor 内全部 dock 与 controller 上方。
 *
 * <p>2.5 适配:UIFilmPanel 的停靠面板(editArea/main/preview/replaysList/replayProps)
 * 迁移到共享 UIDockLayout,通用小窗(浮动/还原/拖柄菜单)由
 * {@link UIDockLayoutMiniWindowSupport} 提供;本宿主只保留影片特有部分——
 * 摄像机/回放两个虚拟时间线编辑器的独立小窗(main 壳内子编辑器,不占布局节点)、
 * 检查器(inspector)的 embed/restore、浮动时间线可见性维持、监视器小窗的
 * 视口输入转发(orbit 相机)。公共部分见 {@link MiniWindowDockHostSupport}。</p>
 */
public class UIFilmPanelMiniWindowSupport implements MiniWindowDockHostSupport.DockHostAdapter
{
    public static final String PANEL_MAIN = "main";
    public static final String PANEL_CAMERA = "cameraEditor";
    public static final String PANEL_REPLAY = "replayEditor";
    public static final String PANEL_PREVIEW = "preview";
    public static final String PANEL_EDIT = "editArea";
    public static final String PANEL_REPLAYS = "replaysList";
    public static final String PANEL_PROPS = "replayProps";

    private final UIFilmPanel panel;
    private final MiniWindowDockHostSupport common;

    /** 共享 editArea 当前应显示的检查器所属编辑器。最后一次选中/聚焦的时间线优先。 */
    private UIElement inspectorOwner;

    public UIFilmPanelMiniWindowSupport(UIFilmPanel panel, IMiniWindowDockHost host)
    {
        this.panel = panel;
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

    /** dock 宿主(2.5 影片面板的停靠系统),可能尚未构造完成时为 null。 */
    private IMiniWindowDockHost dockHost()
    {
        return this.panel.dock instanceof IMiniWindowDockHost host ? host : null;
    }

    private boolean isDockFloating(String panelId)
    {
        IMiniWindowDockHost host = this.dockHost();

        return host != null && host.hostIsFloating(panelId);
    }

    /* ---------- DockHostAdapter ---------- */

    /**
     * 只读快照:虚拟面板(摄像机/回放时间线)+ dock 注册面板(2.5 走 getPanel)。
     * 本宿主 usesRegistryStash=false,快照可安全使用。
     */
    @Override
    public Map<String, UIElement> panelById()
    {
        Map<String, UIElement> out = new LinkedHashMap<>();

        out.put(PANEL_CAMERA, this.panel.cameraEditor);
        out.put(PANEL_REPLAY, this.panel.replayEditor);

        if (this.panel.dock != null)
        {
            for (String id : ((UIDockLayoutAccessor) (Object) this.panel.dock).bbspp_cml$getSlotById().keySet())
            {
                UIElement dockPanel = this.panel.dock.getPanel(id);

                if (dockPanel != null)
                {
                    out.put(id, dockPanel);
                }
            }
        }

        return out;
    }

    @Override
    public boolean usesRegistryStash()
    {
        return false;
    }

    @Override
    public List<UIDraggable> splitterHandles()
    {
        return List.of();
    }

    @Override
    public UIElement splitterHandleContainer()
    {
        return this.panel;
    }

    @Override
    public UIElement miniLayerAnchor()
    {
        return this.panel;
    }

    /* ---------- 注入回调对应的业务实现 ---------- */

    /** 对应 &lt;init&gt; TAIL:创建 manager、挂 miniLayer、初始化检查器归属与按钮右键菜单。 */
    public void activate()
    {
        this.common.activate();
        /* 空尺寸层只提供小窗坐标系,不能覆盖 editor 的分隔柄输入区域。 */
        this.common.getMiniWindowLayer();

        this.inspectorOwner = this.panel.cameraEditor;
        this.attachSwitchButtonContext();
    }

    private void attachSwitchButtonContext()
    {
        /* 右键摄像机/回放按钮:各自转为独立小窗,不切换当前正在查看的编辑器。 */
        this.attachUndockContext(this.panel.openCameraEditor, PANEL_CAMERA,
            () -> this.panel.cameraEditor != null,
            null);
        this.attachUndockContext(this.panel.openReplayEditor, PANEL_REPLAY,
            () -> this.panel.replayEditor != null,
            null);
    }

    /**
     * 右键顶栏切换按钮:转小窗 / 还原小窗。
     * @param available 为 false 时不显示转小窗(已在目标编辑器 / 已浮动 / 布局锁定);
     *                  已浮动时改为显示"还原窗口"。
     */
    private void attachUndockContext(UIIcon button, String panelId, BooleanSupplier available, Runnable ensureSelected)
    {
        if (button == null)
        {
            return;
        }

        button.context((menu) ->
        {
            if (this.common.floatingIds().contains(panelId))
            {
                /* 已浮动:提供还原入口,右键切换按钮即可还原 */
                menu.action(Icons.OUT, SnowUIKeys.MINI_WINDOW_RESTORE, () -> this.common.redockPanelSafely(panelId));

                return;
            }

            if (this.isLayoutLocked() || (available != null && !available.getAsBoolean()))
            {
                return;
            }

            menu.action(Icons.EXTERNAL, SnowUIKeys.MINI_WINDOW_CONVERT, () ->
            {
                try
                {
                    if (ensureSelected != null)
                    {
                        ensureSelected.run();
                    }

                    if (this.common.manager() != null)
                    {
                        this.common.manager().undock(panelId);
                    }
                }
                catch (Throwable t)
                {
                    BBSFSloveCMLClient.LOGGER.warn("[MiniWindow] 面板 {} 转小窗失败", panelId, t);
                }
            });
        });
    }

    private boolean isLayoutLocked()
    {
        return this.panel.dock == null || this.panel.dock.isLocked();
    }

    /** 对应 showPanel(UIElement) TAIL:切换摄像机/回放后同步检查器与浮动时间线。 */
    public void afterShowPanel(UIElement element)
    {
        if (element == this.panel.cameraEditor || element == this.panel.replayEditor)
        {
            this.selectInspectorOwner(element);
        }

        if (this.common.manager() == null)
        {
            return;
        }

        try
        {
            if (!this.common.floatingIds().isEmpty())
            {
                this.common.manager().reassertFloatingPanels();
            }

            this.forceFloatingMainTimelineVisible();
            this.syncInspectorParents();
            this.common.ensureMiniLayerOnTop();
        }
        catch (Throwable t)
        {
            BBSFSloveCMLClient.LOGGER.warn("[MiniWindow] showPanel 后同步浮动小窗失败", t);
        }
    }

    /**
     * 对应 updateMainEditorVisibility TAIL:每次布局重建(onChanged 链)后的
     * 影片侧同步——浮动时间线可见性、main 子节点顺序、检查器归属与偏移。
     */
    public void afterDockLayoutSync()
    {
        this.forceFloatingMainTimelineVisible();
        this.normalizeMainChildOrder();
        this.syncInspectorParents();
        this.refreshOffsets();
        this.common.ensureMiniLayerOnTop();
    }

    /* ---------- IMiniWindowDockHost 委托实现 ---------- */

    public UIElement hostGetDockPanel(String panelId)
    {
        if (PANEL_CAMERA.equals(panelId))
        {
            return this.panel.cameraEditor;
        }

        if (PANEL_REPLAY.equals(panelId))
        {
            return this.panel.replayEditor;
        }

        if (this.panel.dock != null)
        {
            return this.panel.dock.getPanel(panelId);
        }

        return null;
    }

    public Icon hostResolvePanelIcon(String panelId)
    {
        if (panelId == null)
        {
            return Icons.FILE;
        }

        Icon dockIcon = this.panel.dock != null
            ? ((UIDockLayoutAccessor) (Object) this.panel.dock).bbspp_cml$getIconById().get(panelId)
            : null;

        if (dockIcon != null)
        {
            return dockIcon;
        }

        return switch (panelId)
        {
            case PANEL_CAMERA -> Icons.FRUSTUM;
            case PANEL_REPLAY -> Icons.SCENE;
            case PANEL_MAIN -> Icons.FILM;
            default -> Icons.FILE;
        };
    }

    public String hostLayoutPanelId(String panelId)
    {
        /* 摄像机/回放是 main 壳的子编辑器,不在布局树中生成虚拟节点;
         * dock 面板的布局节点由 dock 宿主自己管理。 */
        return null;
    }

    public boolean hostCanUndockPanel(String panelId)
    {
        /* 本宿主只负责摄像机/回放两个虚拟面板;dock 面板走 dock 宿主。 */
        return PANEL_CAMERA.equals(panelId) || PANEL_REPLAY.equals(panelId);
    }

    public EditorLayoutNode hostGetLayoutRoot()
    {
        return this.access().bbspp_cml$getCurrentFilmLayoutRoot();
    }

    public void hostSetLayoutRoot(EditorLayoutNode root)
    {
        this.access().bbspp_cml$setCurrentFilmLayoutRoot(root);
    }

    public void hostRefreshLayout()
    {
        /* 虚拟面板浮动不动 dock 布局树,刷影片面板与编辑器 bounds 即可。 */
        this.panel.resize();

        if (this.panel.editor != null)
        {
            this.panel.editor.resize();
        }
    }

    public void hostClearMiniWindows()
    {
        this.common.clearMiniWindows();

        IMiniWindowDockHost dockHost = this.dockHost();

        if (dockHost != null)
        {
            dockHost.hostClearMiniWindows();
        }

        this.inspectorOwner = this.inspectorEditorFor(this.getSelectedMainEditor());
        this.refreshOffsets();
    }

    public void hostReattachPanel(String panelId, UIElement panel)
    {
        if (panel == null)
        {
            return;
        }

        /* 仅摄像机/回放时间线编辑器允许挂回 main 壳;dock 面板(dock 槽内面板)
         * 由 dock 宿主管,误走这里会把整块面板 full() 进轨道容器。 */
        if (!PANEL_CAMERA.equals(panelId) && !PANEL_REPLAY.equals(panelId))
        {
            return;
        }

        panel.removeFromParent();
        panel.resetFlex();
        panel.setVisible(true);
        panel.setEnabled(true);

        /* 独立时间线编辑器回到共享 main 壳原位置,不向布局树新增节点。 */
        if (this.panel.main != null)
        {
            this.panel.main.setVisible(true);
        }

        panel.full(this.panel.main);
        this.panel.main.add(panel);
        this.normalizeMainChildOrder();
    }

    public void hostOnPanelFloated(String panelId)
    {
        this.handoffDockedMainEditor(panelId);

        this.forceFloatingMainTimelineVisible();
        this.syncInspectorParents();

        this.normalizeMainChildOrder();
        this.refreshOffsets();
        this.common.ensureMiniLayerOnTop();
    }

    public void hostOnPanelDocked(String panelId)
    {
        UIElement mainEditor = null;

        if (PANEL_CAMERA.equals(panelId) && this.panel.cameraEditor != null)
        {
            mainEditor = this.panel.cameraEditor;
        }
        else if (PANEL_REPLAY.equals(panelId) && this.panel.replayEditor != null)
        {
            mainEditor = this.panel.replayEditor;
        }

        if (mainEditor != null)
        {
            /* showPanel 在"仍是当前编辑器且 visible=true"时会直接返回。浮动期间
             * selectedMainEditorPanel 可能保留旧引用,因此先隐藏以强制原版完整同步
             * 时间线、属性面板、预览尺寸和输入命中状态。 */
            mainEditor.setVisible(false);
            this.panel.showPanel(mainEditor);
        }

        if (this.panel.main != null)
        {
            this.panel.main.setVisible(true);
        }

        UIElement panel = this.hostGetDockPanel(panelId);

        if (panel != null)
        {
            /* dock 面板由 dock 宿主挂接;hostReattachPanel 仅放行摄像机/回放。 */
            if (panel.getParent() == null)
            {
                this.hostReattachPanel(panelId, panel);
            }

            panel.setEnabled(true);
            panel.setVisible(true);
        }

        this.syncInspectorParents();

        this.normalizeMainChildOrder();
        this.refreshOffsets();
        this.common.ensureMiniLayerOnTop();

        /* redock 可能发生在一次鼠标事件分发中,显式刷新最终父子树的 bounds。 */
        if (this.panel.main != null)
        {
            this.panel.main.resize();
        }

        if (panel != null)
        {
            panel.resize();
        }

        if (this.panel.editor != null)
        {
            this.panel.editor.resize();
        }
    }

    public void hostOnPanelFocused(String panelId)
    {
        if (PANEL_CAMERA.equals(panelId))
        {
            this.selectInspectorOwner(this.panel.cameraEditor);
        }
        else if (PANEL_REPLAY.equals(panelId))
        {
            this.selectInspectorOwner(this.inspectorEditorFor(this.panel.replayEditor));
        }
    }

    public boolean hostShouldPassthroughContentInput(String panelId)
    {
        return PANEL_PREVIEW.equals(panelId) && this.isDockFloating(PANEL_PREVIEW);
    }

    /* ---------- IMiniWindowDockHostRef 委托实现 ---------- */

    public boolean isEditAreaFloating()
    {
        return this.isDockFloating(PANEL_EDIT);
    }

    public boolean shouldEmbedInspectors()
    {
        return this.common.floatingIds().contains(PANEL_CAMERA)
            || this.common.floatingIds().contains(PANEL_REPLAY)
            || this.isDockFloating(PANEL_EDIT)
            || this.isDockFloating(PANEL_MAIN);
    }

    public void selectInspectorOwner(UIElement editor)
    {
        UIElement candidate = this.inspectorEditorFor(editor);

        if (candidate != this.panel.cameraEditor && candidate != this.panel.actionEditor && candidate != this.panel.replayEditor)
        {
            return;
        }

        /* 最后操作的编辑器拥有检查器。无选中项时保持空白,不能继续显示另一编辑器的旧面板。 */
        this.inspectorOwner = candidate;

        if (this.shouldEmbedInspectors())
        {
            this.syncInspectorParents();
        }
    }

    public void refreshInspectorOwner()
    {
        if (this.shouldEmbedInspectors())
        {
            this.syncInspectorParents();
        }
    }

    public void prepareInspectorRebuild()
    {
        if (this.shouldEmbedInspectors())
        {
            this.restoreInspectorsFromEditArea();
        }
    }

    public UIElement editAreaPanel()
    {
        /* editArea 本身始终是停靠树里的独立 panel,即使它所在的窗口正在浮动。 */
        if (this.panel.dock != null)
        {
            UIElement dockEdit = this.panel.dock.getPanel(PANEL_EDIT);

            if (dockEdit != null)
            {
                return dockEdit;
            }
        }

        return this.panel.editArea;
    }

    /* ---------- 监视器小窗视口输入转发(orbit 相机) ---------- */

    public Predicate<UIContext> hostViewportClickForwarder(String panelId)
    {
        /* 仅监视器(preview)小窗:把内容区空白点击/滚轮转发给 Dashboard orbitUI,
         * 让飞行/镜头/自由模式都能在监视器小窗内启动/操控相机 orbit。
         * 小窗自身仍消费事件(true),不 PASS 穿透到后方 dock UI。 */
        if (!PANEL_PREVIEW.equals(panelId) || !this.isDockFloating(PANEL_PREVIEW))
        {
            return null;
        }

        return this::forwardViewportInputToOrbit;
    }

    public Predicate<UIContext> hostViewportScrollForwarder(String panelId)
    {
        return PANEL_PREVIEW.equals(panelId) && this.isDockFloating(PANEL_PREVIEW)
            ? this::forwardViewportScrollToOrbit
            : null;
    }

    public Consumer<UIContext> hostViewportReleaseForwarder(String panelId)
    {
        /* 监视器小窗释放鼠标时主动调 orbitUI.mouseReleased → orbit.release,
         * 防止中键 pan 启动后状态卡住持续抢占左右键。 */
        if (!PANEL_PREVIEW.equals(panelId) || !this.isDockFloating(PANEL_PREVIEW))
        {
            return null;
        }

        return this::forwardViewportReleaseToOrbit;
    }

    private void forwardViewportReleaseToOrbit(UIContext context)
    {
        try
        {
            if (this.panel.dashboard == null || this.panel.dashboard.orbitUI == null)
            {
                return;
            }

            this.panel.dashboard.orbitUI.mouseReleased(context);

            /* filmController.orbit 也可能处于 orbiting 态,同步 stop(controller 是 private,走 getController) */
            try
            {
                mchorse.bbs_mod.ui.film.controller.UIFilmController controller = this.panel.getController();

                if (controller != null && controller.orbit != null)
                {
                    controller.orbit.stop();
                }
            }
            catch (Throwable t)
            {
                /* 预期内:控制器可能尚未就绪,只记录调试日志 */
                BBSFSloveCMLClient.LOGGER.debug("[MiniWindow] 同步停止 filmController.orbit 失败", t);
            }
        }
        catch (Throwable t)
        {
            BBSFSloveCMLClient.LOGGER.warn("[MiniWindow] 监视器小窗释放事件转发 orbitUI 失败", t);
        }
    }

    /**
     * 把视口输入转发给 Dashboard orbitUI(飞行相机)。
     * click:orbitUI.mouseClicked 决定能否启动(返回非null=消费);
     * scroll:orbitUI.mouseScrolled 决定能否消费。
     * 转发后无论消费与否,小窗 subMouseClicked/subMouseScrolled 都 return true 拦截后方。
     */
    private boolean forwardViewportInputToOrbit(UIContext context)
    {
        try
        {
            if (this.panel.dashboard == null || this.panel.dashboard.orbitUI == null)
            {
                return false;
            }

            mchorse.bbs_mod.ui.framework.elements.IUIElement consumed = this.panel.dashboard.orbitUI.mouseClicked(context);

            return consumed != null;
        }
        catch (Throwable t)
        {
            BBSFSloveCMLClient.LOGGER.warn("[MiniWindow] 监视器小窗点击转发 orbitUI 失败", t);
        }

        return false;
    }

    private boolean forwardViewportScrollToOrbit(UIContext context)
    {
        try
        {
            if (this.panel.dashboard == null || this.panel.dashboard.orbitUI == null)
            {
                return false;
            }

            return this.panel.dashboard.orbitUI.mouseScrolled(context) != null;
        }
        catch (Throwable t)
        {
            BBSFSloveCMLClient.LOGGER.warn("[MiniWindow] 监视器小窗滚轮转发 orbitUI 失败", t);
            return false;
        }
    }

    /* ---------- 影片特有:main 壳/时间线/检查器维护 ---------- */

    /**
     * 固定 main 子节点绘制/命中顺序:效果面板在下,时间线编辑器在上。
     * reattach 用 add() 会把面板顶到最后,导致 editArea 盖住时间线。
     * 只重排仍挂在 main 下的子节点,不把小窗里的编辑器拽回来。
     */
    private void normalizeMainChildOrder()
    {
        if (this.panel.main == null)
        {
            return;
        }

        /* 2.5:editArea/preview/replaysList/replayProps 已是 dock 槽面板(parent 不是 main),
         * 旧数组里的这些条目是 2.4 残留,只保留 main 壳内的三个编辑器。 */
        UIElement[] ordered = {
            this.panel.cameraEditor,
            this.panel.actionEditor,
            this.panel.replayEditor
        };

        for (UIElement child : ordered)
        {
            if (child == null || child.getParent() != this.panel.main)
            {
                continue;
            }

            child.removeFromParent();
            this.panel.main.add(child);
        }
    }

    /** 原版只显示 selectedMainEditor;独立浮动的摄像机/回放时间线必须继续更新。 */
    public void forceFloatingMainTimelineVisible()
    {
        try
        {
            if (this.common.floatingIds().contains(PANEL_CAMERA) && this.panel.cameraEditor != null)
            {
                this.panel.cameraEditor.setVisible(true);
                this.panel.cameraEditor.setEnabled(true);
                this.panel.cameraEditor.setTimelineVisible(true);
            }

            if (this.common.floatingIds().contains(PANEL_REPLAY) && this.panel.replayEditor != null)
            {
                this.panel.replayEditor.setVisible(true);
                this.panel.replayEditor.setEnabled(true);
                this.panel.replayEditor.setTimelineVisible(true);
            }

            /* main 经 dock 宿主浮动时,壳内仍应显示当前选中的时间线编辑器。 */
            if (this.isDockFloating(PANEL_MAIN))
            {
                UIElement selected = this.getSelectedMainEditor();

                if (selected != null)
                {
                    selected.setVisible(true);
                    selected.setEnabled(true);

                    if (selected == this.panel.cameraEditor && this.panel.cameraEditor != null)
                    {
                        this.panel.cameraEditor.setTimelineVisible(true);
                    }
                    else if (selected == this.panel.replayEditor && this.panel.replayEditor != null)
                    {
                        this.panel.replayEditor.setTimelineVisible(true);
                    }
                }
            }
        }
        catch (Throwable t)
        {
            BBSFSloveCMLClient.LOGGER.warn("[MiniWindow] 维持浮动时间线可见性失败", t);
        }
    }

    /**
     * 摄像机和回放共用 main。当前编辑器浮动后,main 必须立即交给仍停靠的另一方,
     * 否则原版会继续隐藏并禁用下层编辑器。
     */
    private void handoffDockedMainEditor(String panelId)
    {
        UIElement selected = this.getSelectedMainEditor();
        UIElement replacement = null;

        if (PANEL_CAMERA.equals(panelId)
            && selected == this.panel.cameraEditor
            && !this.common.floatingIds().contains(PANEL_REPLAY))
        {
            replacement = this.panel.replayEditor;
        }
        else if (PANEL_REPLAY.equals(panelId)
            && selected == this.panel.replayEditor
            && !this.common.floatingIds().contains(PANEL_CAMERA))
        {
            replacement = this.panel.cameraEditor;
        }

        if (replacement != null)
        {
            this.panel.showPanel(replacement);
        }

        this.forceFloatingMainTimelineVisible();
    }

    private void setExclusiveInspectorVisibility(UIElement owner)
    {
        try
        {
            if (this.panel.cameraEditor != null)
            {
                this.panel.cameraEditor.setPropertiesVisible(owner == this.panel.cameraEditor);
            }

            if (this.panel.actionEditor != null)
            {
                this.panel.actionEditor.setPropertiesVisible(owner == this.panel.actionEditor);
            }

            if (this.panel.replayEditor != null)
            {
                this.panel.replayEditor.setPropertiesVisible(owner == this.panel.replayEditor);
            }
        }
        catch (Throwable t)
        {
            BBSFSloveCMLClient.LOGGER.warn("[MiniWindow] 设置检查器互斥可见性失败", t);
        }
    }

    private void embedInspectorsIntoEditArea()
    {
        UIElement editArea = this.editAreaPanel();

        if (editArea == null)
        {
            return;
        }

        UIElement selected = this.getInspectorOwner();

        this.setExclusiveInspectorVisibility(selected);

        if (selected == this.panel.replayEditor)
        {
            this.embedKeyframeInspector(editArea);
        }
        else
        {
            Object clips = selected == this.panel.actionEditor ? this.panel.actionEditor : this.panel.cameraEditor;

            this.moveClipInspector(clips, editArea);
        }

        editArea.resize();
        this.refreshOffsets();
    }

    private void syncInspectorParents()
    {
        if (this.shouldEmbedInspectors())
        {
            this.embedInspectorsIntoEditArea();

            return;
        }

        /* 无浮动面板(常态):2.5 原版把剪辑/关键帧检查器原生挂在 editArea
         * (pickClip/pickKeyframe 的 target)。此处只把"已存在但没挂回 editArea"的
         * 检查器补挂回去,绝不 full() 进 main 内的编辑器(那会铺满并盖住轨道图层)。
         * 目标:检查器永远留在 editArea,拖动分隔柄只改尺寸不改父级。 */
        this.reassertInspectorsIntoEditArea();
    }

    /** 把"存在但父级不在 editArea 子树"的剪辑/关键帧检查器补挂回 editArea。
     *  只纠正父节点,不修改可见性——可见性由原版 pickClip/pickKeyframe 控制,
     *  避免切换编辑器(如相机→模型)时旧检查器被强制显示而浮在新编辑器之上。 */
    private void reassertInspectorsIntoEditArea()
    {
        UIElement editArea = this.editAreaPanel();

        if (editArea == null)
        {
            return;
        }

        this.reassertClipInspector(this.panel.cameraEditor, editArea);
        this.reassertClipInspector(this.panel.actionEditor, editArea);

        try
        {
            if (this.panel.replayEditor != null && this.panel.replayEditor.keyframeEditor != null)
            {
                UIElement editor = this.panel.replayEditor.keyframeEditor.editor;

                if (editor != null && editor.getParent() != editArea && !this.isDescendantOf(editor, editArea))
                {
                    editor.removeFromParent();
                    editor.resetFlex();
                    editor.full(editArea);
                    editArea.add(editor);
                    editArea.resize();
                }
            }
        }
        catch (Throwable t)
        {
            BBSFSloveCMLClient.LOGGER.warn("[MiniWindow] 重挂回放关键帧检查器到 editArea 失败", t);
        }
    }

    private void reassertClipInspector(Object clipsPanel, UIElement editArea)
    {
        if (!(clipsPanel instanceof UIClipsPanelAccessor clips))
        {
            return;
        }

        try
        {
            UIElement panel = clips.bbspp_cml$getClipPanel();

            if (panel == null || panel.getParent() == editArea)
            {
                return;
            }

            /* 已在 editArea 子树内(例如经典条编辑期间),不移动也不强制可见。 */
            if (this.isDescendantOf(panel, editArea))
            {
                return;
            }

            panel.removeFromParent();
            panel.resetFlex();
            panel.full(editArea);
            editArea.add(panel);
            editArea.resize();
        }
        catch (Throwable t)
        {
            BBSFSloveCMLClient.LOGGER.warn("[MiniWindow] 重挂片段检查器到 editArea 失败", t);
        }
    }

    private void embedKeyframeInspector(UIElement editArea)
    {
        try
        {
            if (this.panel.replayEditor == null || this.panel.replayEditor.keyframeEditor == null)
            {
                return;
            }

            if (this.panel.replayEditor.keyframeEditor.editor != null)
            {
                UIElement editor = this.panel.replayEditor.keyframeEditor.editor;

                editor.removeFromParent();
                editor.resetFlex();
                editor.full(editArea);
                editor.setVisible(true);
                editArea.add(editor);
            }

            editArea.resize();
        }
        catch (Throwable t)
        {
            BBSFSloveCMLClient.LOGGER.warn("[MiniWindow] 嵌入回放关键帧检查器到 editArea 失败", t);
        }
    }

    private void moveClipInspector(Object clipsPanel, UIElement editArea)
    {
        if (!(clipsPanel instanceof UIClipsPanelAccessor clips))
        {
            return;
        }

        try
        {
            UIElement panel = clips.bbspp_cml$getClipPanel();

            if (panel == null)
            {
                return;
            }

            panel.removeFromParent();
            panel.resetFlex();
            panel.full(editArea);
            panel.setVisible(true);
            panel.setEnabled(true);
            editArea.add(panel);
        }
        catch (Throwable t)
        {
            BBSFSloveCMLClient.LOGGER.warn("[MiniWindow] 移动片段检查器到 editArea 失败", t);
        }
    }

    private void restoreInspectorsFromEditArea()
    {
        this.restoreClipInspector(this.panel.cameraEditor);
        this.restoreClipInspector(this.panel.actionEditor);

        try
        {
            if (this.panel.replayEditor != null && this.panel.replayEditor.keyframeEditor != null)
            {
                if (this.panel.replayEditor.keyframeEditor.editor != null
                    && this.panel.replayEditor.keyframeEditor.editor.getParent() != this.panel.replayEditor.keyframeEditor)
                {
                    UIElement editor = this.panel.replayEditor.keyframeEditor.editor;

                    editor.removeFromParent();
                    this.panel.replayEditor.keyframeEditor.add(editor);
                    this.panel.replayEditor.keyframeEditor.resize();
                }
            }
        }
        catch (Throwable t)
        {
            BBSFSloveCMLClient.LOGGER.warn("[MiniWindow] 归位回放关键帧检查器失败", t);
        }

        try
        {
            if (this.panel.replayEditor != null)
            {
                this.panel.replayEditor.resize();
            }
        }
        catch (Throwable t)
        {
            /* 预期内:回放编辑器可能处于重建中,只记录调试日志 */
            BBSFSloveCMLClient.LOGGER.debug("[MiniWindow] 刷新回放编辑器布局失败", t);
        }
    }

    private void restoreClipInspector(Object clipsPanel)
    {
        if (!(clipsPanel instanceof UIClipsPanelAccessor clips) || !(clipsPanel instanceof UIElement host))
        {
            return;
        }

        try
        {
            UIElement panel = clips.bbspp_cml$getClipPanel();

            if (panel == null)
            {
                return;
            }

            panel.removeFromParent();
            panel.resetFlex();
            host.add(panel);
            panel.full(host);
            host.resize();
        }
        catch (Throwable t)
        {
            BBSFSloveCMLClient.LOGGER.warn("[MiniWindow] 归位片段检查器失败", t);
        }
    }

    /** node 是否位于 ancestor 的子树内(沿 getParent() 上溯)。BBS 无 isAncestorOf 助手。 */
    private boolean isDescendantOf(UIElement node, UIElement ancestor)
    {
        for (UIElement cur = node; cur != null; cur = cur.getParent())
        {
            if (cur == ancestor)
            {
                return true;
            }
        }

        return false;
    }

    private UIElement getSelectedMainEditor()
    {
        UIElement selected = this.access().bbspp_cml$getSelectedMainEditorPanel();

        return selected == null ? this.panel.cameraEditor : selected;
    }

    private UIElement getInspectorOwner()
    {
        UIElement owner = this.inspectorOwner;

        if (owner == this.panel.cameraEditor || owner == this.panel.actionEditor || owner == this.panel.replayEditor)
        {
            return owner;
        }

        owner = this.inspectorEditorFor(this.getSelectedMainEditor());
        this.inspectorOwner = owner;

        return owner;
    }

    private UIElement inspectorEditorFor(UIElement editor)
    {
        if (editor == this.panel.replayEditor && this.panel.replayEditor != null && this.panel.actionEditor != null)
        {
            try
            {
                if (this.panel.replayEditor.isActionsMode())
                {
                    return this.panel.actionEditor;
                }
            }
            catch (Throwable t)
            {
                /* 预期内:回放编辑器状态未就绪时按普通回放处理,只记录调试日志 */
                BBSFSloveCMLClient.LOGGER.debug("[MiniWindow] 查询回放动作模式失败", t);
            }
        }

        return editor;
    }

    private void refreshOffsets()
    {
        try
        {
            if (this.panel.cameraEditor != null)
            {
                this.panel.cameraEditor.resize();
            }

            if (this.panel.actionEditor != null)
            {
                this.panel.actionEditor.resize();
            }

            if (this.panel.replayEditor != null)
            {
                this.panel.replayEditor.resize();
            }
        }
        catch (Throwable t)
        {
            BBSFSloveCMLClient.LOGGER.warn("[MiniWindow] 刷新编辑面板布局失败", t);
        }
    }

    private UIFilmPanelLayoutAccessor access()
    {
        return (UIFilmPanelLayoutAccessor) (Object) this.panel;
    }
}
