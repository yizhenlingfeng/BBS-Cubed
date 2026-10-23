package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.client.ui.film.TrackDragHost;
import gbeic.bbsplusplus.client.ui.film.TrackOrderStore;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.film.replays.overlays.UIAnimationToPoseOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UITimelineCategoryBar;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 原版「动画转姿势关键帧」只挂在根回放的 {@code pose} 轨道上
 * ({@code sheet.id.equals("pose")})。身体附件(body part)的 pose 轨道 id 形如
 * {@code parts/0/pose} 或 {@code &lt;path&gt;/pose}，因此菜单不出现。
 *
 * <p>在 {@link UIReplaysEditor#updateChannelsList()} 重建 keyframe 编辑器后叠加
 * context 菜单：对任意非 overlay 的 pose 轨道(根轨原版已有，这里跳过以免重复)
 * 解析 sheet 所属 {@link ModelForm}，调用同一套
 * {@link UIReplaysEditorUtils#animationToPoseKeyframes}。</p>
 *
 * <p>注意:不要在 layoutBottomToggles 上挂转小窗菜单——该方法每次 resize 都会调用,
 * context() 是追加而非替换,会刷出超长菜单;动作时间轴也不是独立 dock 面板。</p>
 */
@Mixin(value = UIReplaysEditor.class, remap = false)
public abstract class UIReplaysEditorMixin
{
    @Shadow
    public UIKeyframeEditor keyframeEditor;

    @Shadow
    private UIFilmPanel filmPanel;

    @Shadow
    private Replay replay;

    @Inject(method = "updateChannelsList", at = @At("TAIL"), remap = false)
    private void bbspp_cml$addBodyPartAnimationToPose(CallbackInfo ci)
    {
        if (this.keyframeEditor == null || this.filmPanel == null || this.replay == null)
        {
            return;
        }

        UIKeyframeEditor editor = this.keyframeEditor;
        UIFilmPanel panel = this.filmPanel;

        editor.view.context((menu) ->
        {
            if (this.replay == null || this.replay.form.get() == null)
            {
                return;
            }

            int mouseY = editor.getContext().mouseY;
            UIKeyframeSheet sheet = editor.view.getGraph().getSheet(mouseY);

            if (sheet == null
                || sheet.channel.getFactory() != KeyframeFactories.POSE
                || sheet.id == null
                || sheet.id.equals("pose")
                || !sheet.id.endsWith(FormUtils.PATH_SEPARATOR + "pose")
                || sheet.id.contains("pose_overlay"))
            {
                return;
            }

            Form sheetForm = sheet.property != null ? FormUtils.getForm(sheet.property) : null;

            if (!(sheetForm instanceof ModelForm modelForm))
            {
                return;
            }

            ModelInstance model = ModelFormRenderer.getModel(modelForm);

            if (model == null)
            {
                return;
            }

            menu.action(Icons.POSE, UIKeys.FILM_REPLAY_CONTEXT_ANIMATION_TO_KEYFRAMES, () ->
            {
                UIOverlay.addOverlay(
                    editor.getContext(),
                    new UIAnimationToPoseOverlayPanel(
                        (animationKey, onlyKeyframes, length, step) ->
                        {
                            int current = panel.getCursor();
                            IEntity entity = panel.getController().getCurrentEntity();

                            UIReplaysEditorUtils.animationToPoseKeyframes(
                                editor, sheet, modelForm, entity, current,
                                animationKey, onlyKeyframes, length, step);
                        },
                        modelForm, sheet),
                    200, 197
                );
            });
        });
    }

    /* ==================== BBS++：轨道排序锁定按钮 ====================
     *
     * 在「展开/折叠所有分组」按钮（sectionsToggle，y=-40）上方加一个
     * 锁定/解锁切换（y=-60）：解锁后可在轨道列表名称列左键拖动轨道行重排
     * （见 UIKeyframeDopeSheetMixin），重新锁定只恢复"不可拖"状态——
     * 顺序本身在每次拖动提交时已持久化（TrackOrderStore）。
     * 右键按钮弹出「恢复默认排序」。
     */

    @Shadow
    public UITimelineCategoryBar iconBar;

    @Shadow
    private boolean timelineVisible;

    @Shadow
    private boolean actionsMode;

    @Shadow
    public abstract void updateChannelsList();

    @Unique
    private UIIcon bbspp$orderLock;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp$addOrderLockButton(UIFilmPanel filmPanel, CallbackInfo ci)
    {
        UIElement self = (UIElement) (Object) this;

        /* 图标用 Supplier 驱动：始终实时反映当前锁定状态（闭锁/开锁） */
        this.bbspp$orderLock = new UIIcon(() -> TrackOrderStore.isUnlocked() ? Icons.UNLOCKED : Icons.LOCKED, (b) ->
        {
            boolean unlock = !TrackOrderStore.isUnlocked();

            TrackOrderStore.setUnlocked(unlock);
            this.bbspp$orderLock.tooltip(L10n.lang(unlock
                ? "bbspp.ui.film.replays.track_order_unlocked"
                : "bbspp.ui.film.replays.track_order_locked"), Direction.RIGHT);

            /* 重新上锁时中止进行中的拖动，避免松手后把旧拖动提交到锁定状态 */
            if (!unlock && this.keyframeEditor != null
                && this.keyframeEditor.view.getGraph() instanceof TrackDragHost host)
            {
                host.bbspp$cancelTrackDrag();
            }
        });
        this.bbspp$orderLock.highlight(() -> TrackOrderStore.isUnlocked(), Direction.LEFT);
        this.bbspp$orderLock.tooltip(L10n.lang("bbspp.ui.film.replays.track_order_locked"), Direction.RIGHT);

        /* 与 sectionsToggle(-40)、actionsToggle(-20) 同列，置于其上方 */
        this.bbspp$orderLock.relative(self).x(0).y(1F, -60).wh(20, 20);

        this.bbspp$orderLock.context((menu) ->
        {
            menu.action(Icons.REFRESH, L10n.lang("bbspp.ui.film.replays.track_order_reset"), () ->
            {
                TrackOrderStore.reset();
                this.updateChannelsList();
            });
        });

        self.add(this.bbspp$orderLock);
    }

    /**
     * bringBarToFront 会把分类栏（带不透明 chrome 背景）、分组/动作按钮和部件头重新置顶。
     * 锁定按钮必须跟着置顶，否则分类栏的背景会把它整个盖住——按钮仍可悬停/点击
     * （tooltip 与解锁都正常），但锁头图标永远看不见。
     */
    @Inject(method = "bringBarToFront", at = @At("TAIL"), remap = false)
    private void bbspp$bringOrderLockToFront(CallbackInfo ci)
    {
        if (this.bbspp$orderLock != null)
        {
            this.bbspp$orderLock.removeFromParent();
            ((UIElement) (Object) this).add(this.bbspp$orderLock);
        }
    }

    /** 每次轨道列表重建（切分类/切回放/改过滤器/恢复默认）后重放自定义顺序。 */
    @Inject(method = "updateChannelsList", at = @At("TAIL"), remap = false)
    private void bbspp$applySavedTrackOrder(CallbackInfo ci)
    {
        if (this.keyframeEditor == null)
        {
            return;
        }

        IUIKeyframeGraph graph = this.keyframeEditor.view.getGraph();

        if (graph instanceof UIKeyframeDopeSheet dope && TrackOrderStore.apply(dope.getSheets()))
        {
            ((UIKeyframeDopeSheetAccessor) dope).bbspp$updateScrollSize();
        }
    }

    /** 锁定按钮可见性：与 sectionsToggle 同步（时间轴隐藏/编辑轨道 overlay 打开/按钮列放不下时隐藏），非表格时间轴时禁用。 */
    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void bbspp$updateOrderLockVisibility(UIContext context, CallbackInfo ci)
    {
        if (this.bbspp$orderLock == null)
        {
            return;
        }

        boolean notEditing = this.keyframeEditor == null || !this.keyframeEditor.view.isEditing();
        boolean foldingButtonFits = true;

        for (UIIcon button : this.iconBar.getChildren(UIIcon.class))
        {
            if (button.isVisible() && button.area.ey() >= this.bbspp$orderLock.area.y)
            {
                foldingButtonFits = false;
                break;
            }
        }

        this.bbspp$orderLock.setVisible(this.timelineVisible && !this.actionsMode && notEditing && foldingButtonFits);

        boolean isDopeSheet = this.keyframeEditor != null
            && this.keyframeEditor.view.getGraph() == this.keyframeEditor.view.getDopeSheet();

        this.bbspp$orderLock.setEnabled(isDopeSheet);
    }
}
