package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.BBSFSloveCMLClient;
import gbeic.bbsplusplus.ui.miniwindow.IMiniWindowDockHost;
import gbeic.bbsplusplus.ui.miniwindow.IMiniWindowDockHostRef;
import gbeic.bbsplusplus.ui.miniwindow.UIFilmPanelMiniWindowSupport;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 影片小窗宿主注入薄壳:业务逻辑全部在 {@link UIFilmPanelMiniWindowSupport},
 * 本类只保留注入点与 duck 接口的一行委托。
 *
 * <p>2.5 适配:UIFilmPanel 的面板停靠迁移到共享 UIDockLayout(由
 * UIDockLayoutMiniWindowMixin 提供通用小窗宿主,覆盖 editArea/main/preview/
 * replaysList/replayProps);本宿主只保留影片特有部分——摄像机/回放两个
 * 虚拟时间线编辑器的独立小窗、检查器 embed 与浮动时间线可见性维持。</p>
 */
@Mixin(value = UIFilmPanel.class, remap = false)
public abstract class UIFilmPanelMiniWindowMixin implements IMiniWindowDockHost, IMiniWindowDockHostRef
{
    @Unique
    private UIFilmPanelMiniWindowSupport bbspp_cml$support;

    @Unique
    private UIFilmPanelMiniWindowSupport bbspp_cml$support()
    {
        if (this.bbspp_cml$support == null)
        {
            this.bbspp_cml$support = new UIFilmPanelMiniWindowSupport((UIFilmPanel) (Object) this, this);
        }

        return this.bbspp_cml$support;
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$initMiniManager(CallbackInfo ci)
    {
        this.bbspp_cml$support().activate();
    }

    /** 原版会在切换、锁定和常规刷新时重新隐藏未选中的主编辑器;每次布局重建后同步影片侧浮动状态。 */
    @Inject(method = "updateMainEditorVisibility", at = @At("TAIL"), remap = false)
    private void bbspp_cml$keepFloatingEditorsVisible(boolean hasFilm, CallbackInfo ci)
    {
        /* BBS 的分隔柄 render→applySplitterDrag→setupFlex 链会在渲染期内执行本方法,
         * 而同步涉及重排/移挂子节点(removeFromParent+add),必须推到下一帧渲染前执行,
         * 否则踩中 UIElement.render 正在遍历的 children 迭代器(CME)。 */
        MinecraftClient.getInstance().send(this::bbspp_cml$runAfterDockLayoutSync);
    }

    @Unique
    private void bbspp_cml$runAfterDockLayoutSync()
    {
        try
        {
            this.bbspp_cml$support().afterDockLayoutSync();
        }
        catch (Throwable t)
        {
            BBSFSloveCMLClient.LOGGER.warn("[MiniWindow] 布局同步(延迟)失败", t);
        }
    }

    @Inject(method = "disappear", at = @At("HEAD"), remap = false, require = 0)
    private void bbspp_cml$clearMiniWindowsOnLeave(CallbackInfo ci)
    {
        this.hostClearMiniWindows();
    }

    @Inject(method = "resetFilmLayout", at = @At("HEAD"), remap = false)
    private void bbspp_cml$clearMiniWindowsOnReset(CallbackInfo ci)
    {
        this.hostClearMiniWindows();
    }

    /* 必须带 descriptor: showPanel 有 int 重载, 否则会误注入 showPanel(I)V 崩溃 */
    @Inject(method = "showPanel(Lmchorse/bbs_mod/ui/framework/elements/UIElement;)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$afterShowPanel(UIElement element, CallbackInfo ci)
    {
        /* 同上:showPanel 可能由渲染链(分隔柄 drag→布局重建)触发,同步须移到帧外。 */
        MinecraftClient.getInstance().send(() -> this.bbspp_cml$support().afterShowPanel(element));
    }

    @Override
    public UIElement hostGetDockPanel(String panelId)
    {
        return this.bbspp_cml$support().hostGetDockPanel(panelId);
    }

    @Override
    public Icon hostResolvePanelIcon(String panelId)
    {
        return this.bbspp_cml$support().hostResolvePanelIcon(panelId);
    }

    @Override
    public String hostLayoutPanelId(String panelId)
    {
        return this.bbspp_cml$support().hostLayoutPanelId(panelId);
    }

    @Override
    public boolean hostCanUndockPanel(String panelId)
    {
        return this.bbspp_cml$support().hostCanUndockPanel(panelId);
    }

    @Override
    public mchorse.bbs_mod.settings.values.ui.EditorLayoutNode hostGetLayoutRoot()
    {
        return this.bbspp_cml$support().hostGetLayoutRoot();
    }

    @Override
    public void hostSetLayoutRoot(mchorse.bbs_mod.settings.values.ui.EditorLayoutNode root)
    {
        this.bbspp_cml$support().hostSetLayoutRoot(root);
    }

    @Override
    public void hostRefreshLayout()
    {
        this.bbspp_cml$support().hostRefreshLayout();
    }

    @Override
    public UIElement hostGetMiniWindowLayer()
    {
        return this.bbspp_cml$support().common().getMiniWindowLayer();
    }

    @Override
    public void hostBringMiniWindowLayerToFront()
    {
        this.bbspp_cml$support().common().ensureMiniLayerOnTop();
    }

    @Override
    public Set<String> hostFloatingPanelIds()
    {
        return this.bbspp_cml$support().common().floatingIds();
    }

    @Override
    public void hostClearMiniWindows()
    {
        this.bbspp_cml$support().hostClearMiniWindows();
    }

    @Override
    public void hostReattachPanel(String panelId, UIElement panel)
    {
        this.bbspp_cml$support().hostReattachPanel(panelId, panel);
    }

    @Override
    public void hostOnPanelFloated(String panelId)
    {
        this.bbspp_cml$support().hostOnPanelFloated(panelId);
    }

    @Override
    public void hostOnPanelDocked(String panelId)
    {
        this.bbspp_cml$support().hostOnPanelDocked(panelId);
    }

    @Override
    public void hostOnPanelFocused(String panelId)
    {
        this.bbspp_cml$support().hostOnPanelFocused(panelId);
    }

    @Override
    public boolean hostShouldPassthroughContentInput(String panelId)
    {
        return this.bbspp_cml$support().hostShouldPassthroughContentInput(panelId);
    }

    @Override
    public Predicate<mchorse.bbs_mod.ui.framework.UIContext> hostViewportClickForwarder(String panelId)
    {
        return this.bbspp_cml$support().hostViewportClickForwarder(panelId);
    }

    @Override
    public Predicate<mchorse.bbs_mod.ui.framework.UIContext> hostViewportScrollForwarder(String panelId)
    {
        return this.bbspp_cml$support().hostViewportScrollForwarder(panelId);
    }

    @Override
    public Consumer<mchorse.bbs_mod.ui.framework.UIContext> hostViewportReleaseForwarder(String panelId)
    {
        return this.bbspp_cml$support().hostViewportReleaseForwarder(panelId);
    }

    @Override
    public boolean bbspp_cml$isEditAreaFloating()
    {
        return this.bbspp_cml$support().isEditAreaFloating();
    }

    @Override
    public boolean bbspp_cml$shouldEmbedInspectors()
    {
        return this.bbspp_cml$support().shouldEmbedInspectors();
    }

    @Override
    public void bbspp_cml$selectInspectorOwner(UIElement editor)
    {
        this.bbspp_cml$support().selectInspectorOwner(editor);
    }

    @Override
    public void bbspp_cml$refreshInspectorOwner()
    {
        this.bbspp_cml$support().refreshInspectorOwner();
    }

    @Override
    public void bbspp_cml$prepareInspectorRebuild()
    {
        this.bbspp_cml$support().prepareInspectorRebuild();
    }

    @Override
    public UIElement bbspp_cml$editAreaPanel()
    {
        return this.bbspp_cml$support().editAreaPanel();
    }
}
