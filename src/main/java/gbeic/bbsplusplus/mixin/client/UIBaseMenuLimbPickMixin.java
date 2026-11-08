package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.client.ui.picking.LimbPickMode;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 肢体选择：按住按键时隐藏操控器（gizmo）与坐标轴。
 *
 * <p>表单编辑器预览、影片/场景预览、模型方块预览等所有界面都是通过
 * {@link UIBaseMenu#isHideGizmoHeld()} / {@link UIBaseMenu#shouldRenderAxes()} 分支来决定
 * 要不要画操控器与坐标轴，所以在这里统一收口即可，不必逐个渲染器注入。</p>
 */
@Mixin(value = UIBaseMenu.class, remap = false)
public class UIBaseMenuLimbPickMixin
{
    @Inject(method = "isHideGizmoHeld", at = @At("RETURN"), cancellable = true)
    private static void bbspp$limbPickHideGizmo(CallbackInfoReturnable<Boolean> cir)
    {
        if (LimbPickMode.held())
        {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "shouldRenderAxes", at = @At("RETURN"), cancellable = true)
    private static void bbspp$limbPickHideAxes(CallbackInfoReturnable<Boolean> cir)
    {
        if (LimbPickMode.held())
        {
            cir.setReturnValue(false);
        }
    }
}
