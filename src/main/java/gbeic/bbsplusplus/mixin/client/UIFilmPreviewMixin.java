package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.premiere.PremiereExportActions;
import gbeic.bbsplusplus.premiere.PremiereUIKeys;
import gbeic.bbsplusplus.settings.CMLSettings;
import gbeic.bbsplusplus.ui.forms.SnowUIKeys;
import gbeic.bbsplusplus.utils.BonePriority;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UIFilmPreview;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 影片预览界面扩展：添加骨骼优先级、Premiere 导出按钮，
 * 并为跟随轨道模式补齐 perspective 右键菜单与滚轮缩放。
 */
@Mixin(value = UIFilmPreview.class, remap = false)
public abstract class UIFilmPreviewMixin
{
    @Unique
    private static final int BBS_FOLLOW_ORBIT_MODE = 6;

    @Shadow
    private UIFilmPanel panel;

    @Unique
    private UIIcon bbspp_cml$bonePriority;

    @Unique
    private UIIcon bbspp_cml$premiereExport;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$addMonitorButtons(UIFilmPanel panel, CallbackInfo ci)
    {
        UIFilmPreview self = (UIFilmPreview) (Object) this;

        this.bbspp_cml$bonePriority = new UIIcon(Icons.LIMB, (b) -> BonePriority.openMenu(self.getContext(), panel));
        this.bbspp_cml$bonePriority.tooltip(SnowUIKeys.BONE_PRIORITY);
        self.icons.addBefore(self.onionSkin, this.bbspp_cml$bonePriority);

        if (CMLSettings.premiereExportEnabled != null && CMLSettings.premiereExportEnabled.get())
        {
            this.bbspp_cml$premiereExport = new UIIcon(Icons.SOUND, (b) -> PremiereExportActions.onClick(panel));
            this.bbspp_cml$premiereExport.tooltip(PremiereUIKeys.EXPORT, Direction.LEFT);
            this.bbspp_cml$premiereExport.context((menu) ->
            {
                menu.action(Icons.GEAR, PremiereUIKeys.EXPORT_SETTINGS, () -> PremiereExportActions.openSettings(panel));
            });
            self.icons.add(this.bbspp_cml$premiereExport);
        }

        /* 原版 perspective 右键菜单只在 getPovMode() == 2 时显示传送中心、
         * 附加、正交三个选项。跟随轨道模式应使用与原版轨道相同的右键菜单，
         * 因此追加一个 consumer，在跟随轨道模式下添加相同的选项。
         * 原版 consumer 处理模式2，此处 consumer 处理模式6，互不重复。 */
        self.perspective.context((menu) ->
        {
            UIFilmController controller = this.panel.getController();
            if (controller.getPovMode() == BBS_FOLLOW_ORBIT_MODE)
            {
                menu.action(Icons.MOVE_TO, UIKeys.FILM_REPLAY_ORBIT_TELEPORT_TO_RECORDING, controller::teleportOrbitPivotToReplay);
                menu.action(Icons.LINK, UIKeys.FILM_CONTROLLER_KEYS_ATTACH_ORBIT, controller.orbit.isAttached(), controller::toggleOrbitAttachment);
                menu.action(Icons.FRUSTUM, UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_ORTHO, controller.orbit.isOrtho(), controller.orbit::toggleOrtho);
            }
        });
    }

    @Inject(method = "subMouseScrolled", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp_snow$zoomFollowOrbit(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        UIFilmPreview self = (UIFilmPreview) (Object) this;
        Area area = self.getViewport();

        if (area.isInside(context) && !this.panel.isFlying() && this.panel.getController().getPovMode() == BBS_FOLLOW_ORBIT_MODE)
        {
            cir.setReturnValue(this.panel.getController().zoomOrbit(context.mouseWheel));
        }
    }

}
