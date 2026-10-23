package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.ui.film.FilmToolbarOverflow;
import gbeic.bbsplusplus.ui.film.UIToolOverflowButton;
import gbeic.bbsplusplus.ui.film.UIToolOverflowPopup;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UIFilmPreview;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 影片预览工具栏的「工具收纳」按钮：三横线图标，固定在横条最右侧。
 *
 * <ul>
 *   <li>悬停向上展开收纳的工具；左键固定/解除固定；右键弹出配置清单，
 *       勾选哪些工具收进去（还可以一键全部还原）。</li>
 *   <li>被收纳的工具从横条隐藏（RowResizer 跳过不可见子元素，横条自动重新
 *       居中），在展开栏里出现镜像按钮，点击走原按钮的完整点击管线。</li>
 *   <li>开启「自动隐藏」时，展开的收纳栏也算作工具栏的一部分：鼠标悬停在
 *       展开栏上时整条工具栏保持可见（见 {@code updateIconsVisibility} 注入）。</li>
 * </ul>
 */
@Mixin(value = UIFilmPreview.class, remap = false)
public abstract class UIFilmPreviewOverflowMixin
{
    @Shadow
    public UIElement icons;

    @Shadow
    private boolean iconsVisible;

    @Unique
    private UIToolOverflowPopup bbspp$overflowPopup;

    @Unique
    private UIToolOverflowButton bbspp$overflowButton;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp$addOverflowButton(UIFilmPanel panel, CallbackInfo ci)
    {
        UIFilmPreview self = (UIFilmPreview) (Object) this;

        /* 内置工具按工具栏顺序注册；扩展按钮由各自 Mixin 注册（order 20 起） */
        FilmToolbarOverflow.register(self, "onion_skin", self.onionSkin, UIKeys.FILM_CONTROLLER_ONION_SKIN_TITLE, 0);
        FilmToolbarOverflow.register(self, "motion_path", self.motionPath, UIKeys.FILM_CONTROLLER_MOTION_PATH_TITLE, 1);
        FilmToolbarOverflow.register(self, "teleport", self.teleport, UIKeys.FILM_TELEPORT_TITLE, 2);
        FilmToolbarOverflow.register(self, "flight", self.flight, UIKeys.CAMERA_EDITOR_KEYS_MODES_FLIGHT, 3);
        FilmToolbarOverflow.register(self, "plause", self.plause, UIKeys.CAMERA_EDITOR_KEYS_EDITOR_PLAUSE, 4);
        FilmToolbarOverflow.register(self, "control", self.control, UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_CONTROL, 5);
        FilmToolbarOverflow.register(self, "perspective", self.perspective, UIKeys.FILM_CONTROLLER_KEYS_CHANGE_CAMERA_MODE, 6);
        FilmToolbarOverflow.register(self, "record_replay", self.recordReplay, UIKeys.FILM_REPLAY_RECORD, 7);
        FilmToolbarOverflow.register(self, "record_video", self.recordVideo, UIKeys.CAMERA_TOOLTIPS_RECORD, 8);

        UIToolOverflowPopup popup = new UIToolOverflowPopup(self);
        UIToolOverflowButton overflow = new UIToolOverflowButton(self, popup);

        overflow.tooltip(FilmToolbarOverflow.OVERFLOW_TOOLTIP, Direction.LEFT);
        /* 固定（常驻显示）时沿用 BBS 的选中标记：主题色底衬 + 下缘条，与洋葱皮开关一致 */
        overflow.highlight(popup::isPinned, Direction.BOTTOM);
        overflow.context((menu) ->
        {
            for (FilmToolbarOverflow.Tool tool : FilmToolbarOverflow.getTools(self))
            {
                menu.action(tool.button.getIcon(), tool.label, !FilmToolbarOverflow.isHidden(tool.id), () ->
                {
                    FilmToolbarOverflow.setHidden(tool.id, !FilmToolbarOverflow.isHidden(tool.id));
                    FilmToolbarOverflow.syncVisibility(self);
                    popup.rebuild();
                });
            }

            menu.action(Icons.REFRESH, FilmToolbarOverflow.RESET_LABEL, () ->
            {
                FilmToolbarOverflow.resetAll();
                FilmToolbarOverflow.syncVisibility(self);
                popup.rebuild();
            });
        });

        popup.anchorTo(overflow);

        this.bbspp$overflowPopup = popup;
        this.bbspp$overflowButton = overflow;

        self.add(popup);
        self.icons.add(overflow);

        /* 打开影片编辑器即恢复上次的收纳状态 */
        FilmToolbarOverflow.syncVisibility(self);
    }

    /**
     * 把收纳按钮钉在横条最右侧。
     * <p>
     * 骨骼优先级、Premiere 导出、光影控制等扩展按钮同样是在构造器尾部由各自的 Mixin
     * 追加进横条的，而它们分属不同的 Mixin 配置，应用顺序不受本配置控制——谁后执行
     * 谁就排在右边，收纳按钮因此可能被挤到中间。render HEAD 时构造器回调都已跑完、
     * 且尚未进入子元素遍历，在这里差量地把按钮移到末尾既确定又安全。
     * </p>
     */
    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void bbspp$keepOverflowRightmost(UIContext context, CallbackInfo ci)
    {
        UIToolOverflowButton overflow = this.bbspp$overflowButton;
        List<IUIElement> children = this.icons.getChildren();

        if (overflow == null || children.isEmpty() || children.get(children.size() - 1) == overflow)
        {
            return;
        }

        overflow.removeFromParent();
        this.icons.add(overflow);
        this.icons.resize();
    }

    /**
     * 自动隐藏与展开栏的联动：鼠标悬停在展开的收纳栏上时也算「在工具栏上」，
     * 整条工具栏保持可见；反之整条工具栏被自动隐藏时，收纳栏跟着收起。
     */
    @Inject(method = "updateIconsVisibility", at = @At("TAIL"), remap = false)
    private void bbspp$overflowCountsAsToolbar(UIContext context, CallbackInfo ci)
    {
        UIToolOverflowPopup popup = this.bbspp$overflowPopup;

        if (popup == null)
        {
            return;
        }

        if (popup.isExpanded())
        {
            if (!this.iconsVisible)
            {
                this.iconsVisible = true;
            }

            this.icons.setVisible(true);
        }
        else if (!this.iconsVisible)
        {
            popup.forceCollapse();
        }
    }
}
