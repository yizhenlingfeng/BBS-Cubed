package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.TransparentDockLayout;
import gbeic.bbsplusplus.ui.miniwindow.IMiniWindowDockHost;
import gbeic.bbsplusplus.ui.miniwindow.UIDockLayoutMiniWindowSupport;
import gbeic.bbsplusplus.ui.miniwindow.UIMiniWindowManager;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.layout.UIDockLayout;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;

/**
 * UIDockLayout 小窗宿主注入薄壳:业务逻辑全部在 {@link UIDockLayoutMiniWindowSupport},
 * 本类只保留注入点与 duck 接口的一行委托。
 *
 * <p>2.5 起影片编辑器与动画状态编辑器也使用 UIDockLayout,本宿主对全部
 * dock 实例生效:面板注册于 slotById、布局树为唯一事实来源,浮动面板由
 * ensureRegisteredPanels RETURN 钩子从树中剥离(槽位随之自动隐藏)。</p>
 */
@Mixin(value = UIDockLayout.class, remap = false)
public abstract class UIDockLayoutMiniWindowMixin implements IMiniWindowDockHost, TransparentDockLayout
{
    @Shadow
    @Final
    private List<UIDraggable> splitterHandles;

    @Unique
    private UIDockLayoutMiniWindowSupport bbspp_cml$support;

    /**
     * 惰性创建纯状态 support(无副作用);manager 仍由 &lt;init&gt; TAIL 的
     * activate 创建,受 manager 保护路径的生效时机与原实现一致。
     */
    @Unique
    private UIDockLayoutMiniWindowSupport bbspp_cml$support()
    {
        if (this.bbspp_cml$support == null)
        {
            this.bbspp_cml$support = new UIDockLayoutMiniWindowSupport(
                (UIDockLayout) (Object) this, this, () -> this.splitterHandles);
        }

        return this.bbspp_cml$support;
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$initMiniManager(CallbackInfo ci)
    {
        this.bbspp_cml$support().activate();
    }

    @Override
    public void bbspp_cml$setTransparentBase(boolean transparent)
    {
        this.bbspp_cml$support().setTransparentBase(transparent);
    }

    /**
     * 2.4 挂在 renderPanelSurfaces;2.5 该职责拆到 renderCanvas(整块底板),
     * 透明底 dock 只需取消底板绘制,槽位自带 deepSurface。
     */
    @Inject(method = "renderCanvas", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp_cml$renderTransparentDockSurfaces(UIContext context, CallbackInfo ci)
    {
        if (this.bbspp_cml$support().renderTransparentDockSurfaces(context))
        {
            ci.cancel();
        }
    }

    @Inject(method = "createPanelDragHandle", at = @At("RETURN"), remap = false)
    private void bbspp_cml$attachHandleContext(String panelId, CallbackInfoReturnable<UIDraggable> cir)
    {
        this.bbspp_cml$support().attachHandleContext(cir.getReturnValue(), panelId);
    }

    @Inject(method = "ensureRegisteredPanels", at = @At("RETURN"), cancellable = true, remap = false)
    private void bbspp_cml$stripFloatingFromEnsure(EditorLayoutNode root, CallbackInfoReturnable<EditorLayoutNode> cir)
    {
        UIMiniWindowManager manager = this.bbspp_cml$support().manager();

        if (manager != null)
        {
            cir.setReturnValue(manager.stripFloatingFromLayout(cir.getReturnValue()));
        }
    }

    @Inject(method = "setupFlex", at = @At("HEAD"), remap = false)
    private void bbspp_cml$stashFloatingBeforeLayout(boolean resize, CallbackInfo ci)
    {
        this.bbspp_cml$support().common().stashFloatingBeforeLayout();
    }

    @Inject(method = "setupFlex", at = @At("TAIL"), remap = false)
    private void bbspp_cml$restoreFloatingAfterLayout(boolean resize, CallbackInfo ci)
    {
        /* setupFlex 可能由 BBS 分隔柄 render→applySplitterDrag 链路在渲染期内执行,
         * 恢复浮动面板涉及 removeFromParent+add,必须推到帧外防 CME。 */
        MinecraftClient.getInstance().send(() -> this.bbspp_cml$support().restoreFloatingAfterLayout());
    }

    @Inject(method = "resetLayout", at = @At("HEAD"), remap = false)
    private void bbspp_cml$clearMiniWindowsOnReset(CallbackInfo ci)
    {
        this.hostClearMiniWindows();
    }

    /* ---------- 分隔柄硬锁守卫(2.5 从 UIFilmPanel 迁来) ---------- */

    @Shadow
    private void clearSplitterDragState()
    {
    }

    @Unique
    private boolean bbspp_cml$splittersHardLocked()
    {
        return gbeic.bbsplusplus.ui.film.FilmVisibilityController.preventsLockedLayoutResizing((UIDockLayout) (Object) this);
    }

    @Inject(method = "createSplitterHandle", at = @At("RETURN"), remap = false)
    private void bbspp_cml$guardSplitterHandle(int index, CallbackInfoReturnable<UIDraggable> cir)
    {
        UIDraggable handle = cir.getReturnValue();

        if (handle != null)
        {
            handle.enabled(() -> !this.bbspp_cml$splittersHardLocked() && mchorse.bbs_mod.BBSSettings.editorResizablePanels.get());
        }
    }

    @Inject(method = "beginSplitterDrag", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp_cml$blockSplitterBegin(int index, int mouseX, int mouseY, CallbackInfo ci)
    {
        if (this.bbspp_cml$splittersHardLocked())
        {
            this.clearSplitterDragState();
            ci.cancel();
        }
    }

    @Inject(method = "applySplitterDrag", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp_cml$blockSplitterApply(int mouseX, int mouseY, CallbackInfo ci)
    {
        if (this.bbspp_cml$splittersHardLocked())
        {
            this.clearSplitterDragState();
            ci.cancel();
        }
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
    public boolean hostCanUndockPanel(String panelId)
    {
        return this.bbspp_cml$support().hostCanUndockPanel(panelId);
    }

    @Override
    public EditorLayoutNode hostGetLayoutRoot()
    {
        return this.bbspp_cml$support().hostGetLayoutRoot();
    }

    @Override
    public void hostSetLayoutRoot(EditorLayoutNode root)
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
    public void hostOnPanelDocked(String panelId)
    {
        this.bbspp_cml$support().hostOnPanelDocked(panelId);
    }
}
