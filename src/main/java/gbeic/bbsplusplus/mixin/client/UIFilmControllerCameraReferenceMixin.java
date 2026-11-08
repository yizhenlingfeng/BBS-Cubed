package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.client.ui.picking.LimbPickMode;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 肢体选择模式下的摄像机参考线。
 *
 * <p>影片编辑器里「摄像机参考线」（可见性菜单里那一项，本质是
 * {@code BBSSettings.recordingCameraPreview}）由 {@code UIFilmController#renderFrame} 负责绘制。
 * 按住肢体选择按键时把这一项的取值读成 false，参考线就不会画出来；
 * 不改设置本身，松开按键后立即恢复用户原来的开关。</p>
 *
 * <p>注意：{@code renderFrame} 里 {@code recordingCameraPreview.get()} 是同方法内第一个
 * {@code ValueBoolean.get()} 调用（后面 {@code motionPath.enabled.get()} 同类），故用 ordinal = 0 定位。</p>
 */
@Mixin(value = UIFilmController.class, remap = false)
public class UIFilmControllerCameraReferenceMixin
{
    @Redirect(
        method = "renderFrame",
        at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/settings/values/numeric/ValueBoolean;get()Ljava/lang/Object;", ordinal = 0)
    )
    private Object bbspp$hideCameraReferenceWhilePicking(ValueBoolean cameraPreview)
    {
        if (LimbPickMode.held())
        {
            return Boolean.FALSE;
        }

        return cameraPreview.get();
    }
}
