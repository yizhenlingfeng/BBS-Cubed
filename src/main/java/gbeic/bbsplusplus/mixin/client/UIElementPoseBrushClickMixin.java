package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.client.ui.pose.IPoseParameterBrush;
import gbeic.bbsplusplus.mixin.client.accessor.UIListAccessor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.pose.UIPoseBoneStringList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在通用 {@link UIElement#subMouseClicked(UIContext)} 上处理姿势骨骼列表右侧的状态菱形。
 *
 * <p>{@code UIPoseBoneStringList} 本身没有覆写 {@code subMouseClicked}，BBS 2.6/2.7 的列表
 * 实现也不覆写它，因此把点击处理放到真正声明该方法的 {@code UIElement} 上，并用
 * {@code instanceof UIPoseBoneStringList} 限定作用域。</p>
 */
@Mixin(value = UIElement.class, remap = false)
public abstract class UIElementPoseBrushClickMixin
{
    @Inject(method = "subMouseClicked", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void bbspp$togglePoseBoneFromStateDiamond(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        if (!((Object) this instanceof UIPoseBoneStringList self) || context.mouseButton != 0)
        {
            return;
        }

        int markerX = self.area.ex() - 12;

        if (!self.area.isInside(context) || context.mouseX < markerX - 7 || context.mouseX > markerX + 7)
        {
            return;
        }

        int index = ((UIListAccessor) (Object) this).bbspp$getIndexAtCursor(context);

        if (index < 0 || index >= self.getList().size())
        {
            return;
        }

        String bone = self.getList().get(index);

        IPoseParameterBrush brush = bbspp$findParameterBrush((UIElement) (Object) this);

        if (brush != null && brush.bbspp$togglePoseBoneSkipped(bone))
        {
            cir.setReturnValue(true);
        }
    }

    private static IPoseParameterBrush bbspp$findParameterBrush(UIElement element)
    {
        while (element != null)
        {
            if (element instanceof IPoseParameterBrush brush)
            {
                return brush;
            }

            element = element.getParent();
        }

        return null;
    }
}
