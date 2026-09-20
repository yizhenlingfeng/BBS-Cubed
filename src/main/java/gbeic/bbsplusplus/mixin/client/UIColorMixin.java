package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 将 UIColor 的右键从直接弹出调色卡改为弹出上下文菜单，
 * 菜单中从上到下依次为外部注册的动作（如"应用至子集"）和"调色卡"。
 */
@Mixin(UIColor.class)
public abstract class UIColorMixin
{
    @Inject(method = "<init>", at = @At("TAIL"))
    private void bbspp$addPresetsMenuItem(CallbackInfo ci)
    {
        UIColor self = (UIColor) (Object) this;

        self.context((menu) ->
        {
            /* order=0：排在默认 order=-1 的外部动作（如姿势编辑器的"应用至子集"）之后 */
            menu.action(Icons.COLOR, UIKeys.GENERAL_PRESETS, () ->
            {
                UIContext context = self.getContext();
                if (context != null)
                {
                    ((UIColorAccessor) self).bbspp$openPresets(context);
                }
            }).order(0);
        });
    }

    /**
     * 右键不再直接打开调色卡，改为返回 false 让 UIElement.mouseClickedContextMenu
     * 走上下文菜单流程（外部动作 + 调色卡项）。
     */
    @Inject(
        method = "subMouseClicked",
        at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/ui/framework/elements/input/UIColor;openPresets(Lmchorse/bbs_mod/ui/framework/UIContext;)V"),
        cancellable = true
    )
    private void bbspp$rightClickToContextMenu(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        cir.setReturnValue(false);
    }
}
