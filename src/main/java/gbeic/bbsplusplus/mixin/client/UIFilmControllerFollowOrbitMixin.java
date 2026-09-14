package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.controller.OrbitFilmCameraController;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import gbeic.bbsplusplus.settings.CMLSettings;
import gbeic.bbsplusplus.ui.forms.SnowUIKeys;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds an orbit mode whose center follows the selected replay's rendered model anchor. */
@Mixin(value = UIFilmController.class, remap = false)
public abstract class UIFilmControllerFollowOrbitMixin
{
    @Unique
    private static final int BBS_FOLLOW_ORBIT_MODE = 6;

    @Unique
    private boolean bbspp_snow$restoreFollowOrbit = CMLSettings.followOrbitMode != null && CMLSettings.followOrbitMode.get();

    /** 上一个镜头模式(-1 = 尚未记录),用于 R 键在两个最近模式间循环 */
    @Unique
    private int bbspp$prevPov = -1;

    @Shadow
    private int pov;

    @Shadow
    private IEntity controlled;

    @Shadow
    @Final
    public OrbitFilmCameraController orbit;

    @Shadow
    public abstract void setPov(int pov);

    @Shadow
    public abstract int getPovMode();

    @Shadow
    public abstract Icon getOrbitModeIcon(int povMode);


    @Inject(method = "<init>", at = @At("RETURN"))
    private void bbspp_snow$restoreFollowOrbitMode(CallbackInfo ci)
    {
        /* 注册"与上一个镜头模式循环"快捷键(默认 R 键),归入影片控制器栏 */
        ((UIFilmController) (Object) this).keys()
            .register(SnowUIKeys.TOGGLE_PREVIOUS_CAMERA_MODE_KEY, this::bbspp$cyclePrevCameraMode)
            .category(UIKeys.FILM_CONTROLLER_KEYS_CATEGORY);

        if (this.bbspp_snow$restoreFollowOrbit)
        {
            this.setPov(BBS_FOLLOW_ORBIT_MODE);
        }
    }

    @Inject(method = "setPov", at = @At("HEAD"))
    private void bbspp$recordPrevPov(int pov, CallbackInfo ci)
    {
        /* 记录切换前的旧模式,供循环快捷键使用。
         * 仅当模式真正发生变化时才记录,避免重复 setPov 覆盖掉历史。 */
        if (this.pov != pov)
        {
            this.bbspp$prevPov = this.pov;
        }
    }

    @Inject(method = "setPov", at = @At("RETURN"))
    private void bbspp_snow$saveFollowOrbitMode(int pov, CallbackInfo ci)
    {
        if (CMLSettings.followOrbitMode != null)
        {
            CMLSettings.followOrbitMode.set(pov == BBS_FOLLOW_ORBIT_MODE);
        }
    }

    @Inject(method = "getPovMode", at = @At("HEAD"), cancellable = true)
    private void bbspp_cml$getFollowOrbitMode(CallbackInfoReturnable<Integer> cir)
    {
        if (this.pov == BBS_FOLLOW_ORBIT_MODE)
        {
            cir.setReturnValue(BBS_FOLLOW_ORBIT_MODE);
        }
    }

    @Inject(method = "getOrbitModeIcon(I)Lmchorse/bbs_mod/ui/utils/icons/Icon;", at = @At("HEAD"), cancellable = true)
    private void bbspp_cml$getFollowOrbitIcon(int povMode, CallbackInfoReturnable<Icon> cir)
    {
        if (povMode == BBS_FOLLOW_ORBIT_MODE)
        {
            cir.setReturnValue(Icons.ORBIT);
        }
    }

    @Inject(method = "toggleOrbitMode", at = @At("HEAD"), cancellable = true)
    private void bbspp_cml$showFollowOrbitMode(CallbackInfo ci)
    {
        if (this.controlled != null)
        {
            int direction = Window.isShiftPressed() ? -1 : 1;
            int mode = Math.floorMod(this.getPovMode() + direction, 7);

            this.setPov(mode);
            ci.cancel();

            return;
        }

        ((UIFilmController) (Object) this).getContext().replaceContextMenu((menu) ->
        {
            menu.autoKeys();
            menu.action(this.getOrbitModeIcon(0), UIKeys.FILM_REPLAY_ORBIT_CAMERA, this.pov == 0, () -> this.setPov(0));
            menu.action(this.getOrbitModeIcon(1), UIKeys.FILM_REPLAY_ORBIT_FREE, this.pov == 1, () -> this.setPov(1));
            menu.action(this.getOrbitModeIcon(2), UIKeys.FILM_REPLAY_ORBIT_ORBIT, this.pov == 2, () -> this.setPov(2));
            menu.action(Icons.ORBIT, L10n.lang("bbspp.ui.film.follow_orbit"), this.pov == BBS_FOLLOW_ORBIT_MODE, () -> this.setPov(BBS_FOLLOW_ORBIT_MODE));
            menu.action(this.getOrbitModeIcon(3), UIKeys.FILM_REPLAY_ORBIT_FIRST_PERSON, this.pov == 3, () -> this.setPov(3));
            menu.action(this.getOrbitModeIcon(4), UIKeys.FILM_REPLAY_ORBIT_THIRD_PERSON_BACK, this.pov == 4, () -> this.setPov(4));
            menu.action(this.getOrbitModeIcon(5), UIKeys.FILM_REPLAY_ORBIT_THIRD_PERSON_FRONT, this.pov == 5, () -> this.setPov(5));
        });

        ci.cancel();
    }

    @Inject(method = "handleCamera", at = @At("HEAD"), cancellable = true)
    private void bbspp_cml$handleFollowOrbit(Camera camera, float transition, CallbackInfo ci)
    {
        int mode = this.getPovMode();

        if (mode == UIFilmController.CAMERA_MODE_ORBIT)
        {
            if (this.orbit.isAttached())
            {
                this.orbit.toggleAttachment();
            }

            return;
        }

        if (mode != BBS_FOLLOW_ORBIT_MODE)
        {
            return;
        }

        if (!this.orbit.isAttached())
        {
            this.orbit.toggleAttachment();
        }

        this.orbit.setup(camera, transition);

        UIFilmController self = (UIFilmController) (Object) this;

        if (!self.panel.isFlying())
        {
            camera.fov = BBSSettings.getFov();
        }

        ci.cancel();
    }

    /**
     * R 键:在当前镜头模式与上一个镜头模式之间来回切换。
     * 轨道控制器(OrbitFilmCameraController)是 UIFilmController 的常驻成员,
     * 切换 pov 模式只翻开关,不会重置轨道的角度/距离/绑定实体,因此切回后构图完全保留。
     */
    @Unique
    private void bbspp$cyclePrevCameraMode()
    {
        if (this.bbspp$prevPov < 0 || this.bbspp$prevPov == this.getPovMode())
        {
            return;
        }

        this.setPov(this.bbspp$prevPov);
        UIUtils.playClick();
    }
}
