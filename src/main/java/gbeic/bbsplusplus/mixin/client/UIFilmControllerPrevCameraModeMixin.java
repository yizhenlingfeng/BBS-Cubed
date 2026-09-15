package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.utils.UIUtils;
import gbeic.bbsplusplus.ui.forms.SnowUIKeys;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * R 键：在当前镜头模式与上一个镜头模式之间来回切换。
 *
 * <p>轨道控制器（{@code OrbitFilmCameraController}）是 {@link UIFilmController} 的常驻成员，
 * 切换 pov 模式只翻开关，不会重置轨道的角度/距离/绑定实体，因此切回后构图完全保留。</p>
 *
 * <p><b>不接管任何原版行为</b>：本 Mixin 只在原版 {@code setPov} 的 HEAD 记录切换前的模式，
 * 并注册一个额外的快捷键。原版的 {@code toggleOrbitMode}（鼠标循环/右键菜单）、
 * {@code handleCamera}（各模式相机处理）、{@code getPovMode}/{@code getOrbitModeIcon}
 * 全部保持原样不动 —— 这里曾属于「跟随轨道」模式 6 的实现，模式 6 与它对原版轨道模式的
 * 全部改写已一并移除。</p>
 */
@Mixin(value = UIFilmController.class, remap = false)
public abstract class UIFilmControllerPrevCameraModeMixin
{
    /** 上一个镜头模式（-1 = 尚未记录） */
    @Unique
    private int bbspp$prevPov = -1;

    @Shadow
    private int pov;

    @Shadow
    public abstract void setPov(int pov);

    @Shadow
    public abstract int getPovMode();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bbspp$registerPrevCameraModeKey(CallbackInfo ci)
    {
        /* 注册「与上一个镜头模式循环」快捷键（默认 R 键），归入影片控制器栏 */
        ((UIFilmController) (Object) this).keys()
            .register(SnowUIKeys.TOGGLE_PREVIOUS_CAMERA_MODE_KEY, this::bbspp$cyclePrevCameraMode)
            .category(UIKeys.FILM_CONTROLLER_KEYS_CATEGORY);
    }

    @Inject(method = "setPov", at = @At("HEAD"))
    private void bbspp$recordPrevPov(int pov, CallbackInfo ci)
    {
        /* 记录切换前的旧模式，供循环快捷键使用。
         * 仅当模式真正发生变化时才记录，避免重复 setPov 覆盖掉历史。 */
        if (this.pov != pov)
        {
            this.bbspp$prevPov = this.pov;
        }
    }

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
