package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.film.controller.OrbitViewGizmo;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让轨道视图小部件(OrbitViewGizmo,即 XYZ 轴向选择球)在跟随轨道模式下也可用。
 *
 * <p>原版 {@code isActive()} 硬编码只在 {@code getPovMode() == 2}
 * (CAMERA_MODE_ORBIT) 时激活。跟随轨道是模式 6,导致小部件不显示、
 * 点击轴向球无法调用 {@code snapToAxis}。本 Mixin 在原版返回 false 时
 * 补检跟随轨道模式,使 XYZ 轴方向选择在跟随轨道下同样生效。</p>
 *
 * <p>另外,{@code UIFilmPreview} 在某些生命周期下持有 controller 为 null 的
 * OrbitViewGizmo 实例,原版 {@code isActive()} 会直接 NPE 崩溃。HEAD 守卫
 * 在 controller 为 null 时提前返回 false,避免崩溃。</p>
 */
@Mixin(value = OrbitViewGizmo.class, remap = false)
public abstract class OrbitViewGizmoFollowMixin
{
    @Shadow
    private final UIFilmController controller = null;

    private static final int BBS_FOLLOW_ORBIT_MODE = 6;

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp_cml$guardNullController(CallbackInfoReturnable<Boolean> cir)
    {
        if (this.controller == null)
        {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isActive", at = @At("RETURN"), cancellable = true, remap = false)
    private void bbspp_cml$activateForFollowOrbit(CallbackInfoReturnable<Boolean> cir)
    {
        if (cir.getReturnValueZ())
        {
            return;
        }

        if (this.controller == null)
        {
            return;
        }

        if (!BBSSettings.editorOrbitGizmo.get())
        {
            return;
        }

        if (this.controller.getPovMode() != BBS_FOLLOW_ORBIT_MODE)
        {
            return;
        }

        if (this.controller.panel.isFlying())
        {
            return;
        }

        cir.setReturnValue(true);
    }
}
