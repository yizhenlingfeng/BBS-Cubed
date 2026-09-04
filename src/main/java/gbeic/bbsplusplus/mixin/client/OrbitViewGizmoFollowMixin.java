package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.film.controller.OrbitViewGizmo;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
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
 * <p>注意:此前版本曾在 HEAD 注入 NPE 守卫,通过 {@code @Shadow controller}
 * 判空后提前返回 false。但该 shadow 字段在 Mixin 注入后存在误判为 null 的
 * 风险,导致原版轨道模式下 Gizmo 也被强制隐藏(snow 项目遗留 bug)。
 * {@code OrbitViewGizmo} 实例仅在 {@code UIFilmController} 构造函数中以
 * {@code this} 创建,{@code controller} 不会为 null,故移除 NPE 守卫,
 * 改用 {@code @Accessor} 安全读取目标类字段。</p>
 */
@Mixin(value = OrbitViewGizmo.class, remap = false)
public abstract class OrbitViewGizmoFollowMixin
{
    @Accessor("controller")
    protected abstract UIFilmController bbspp$getController();

    private static final int BBS_FOLLOW_ORBIT_MODE = 6;

    @Inject(method = "isActive", at = @At("RETURN"), cancellable = true, remap = false)
    private void bbspp_cml$activateForFollowOrbit(CallbackInfoReturnable<Boolean> cir)
    {
        if (cir.getReturnValueZ())
        {
            return;
        }

        UIFilmController controller = this.bbspp$getController();

        if (controller == null)
        {
            return;
        }

        if (!BBSSettings.editorOrbitGizmo.get())
        {
            return;
        }

        if (controller.getPovMode() != BBS_FOLLOW_ORBIT_MODE)
        {
            return;
        }

        if (controller.panel.isFlying())
        {
            return;
        }

        cir.setReturnValue(true);
    }
}
