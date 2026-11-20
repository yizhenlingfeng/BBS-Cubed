package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.ui.forms.AnimationBakeProgress;
import mchorse.bbs_mod.forms.states.AnimationState;
import mchorse.bbs_mod.ui.forms.editors.states.UIAnimationStateList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UIList.class, remap = false)
public abstract class UIAnimationStateListMixin
{
    @Inject(method = "renderElementPart", at = @At("TAIL"), remap = false)
    private void bbspp_cml$renderBakeProgress(
        UIContext context,
        Object element,
        int index,
        int x,
        int y,
        boolean hover,
        boolean selected,
        CallbackInfo ci
    )
    {
        Object self = this;

        if (!(self instanceof UIAnimationStateList) || !(element instanceof AnimationState state))
        {
            return;
        }

        float progress = AnimationBakeProgress.get(state);

        if (progress < 0F)
        {
            return;
        }

        UIList<?> list = (UIList<?>) self;
        int left = x + 4;
        int right = list.area.ex() - 4;
        int barY = y + list.rowHeight() - 3;
        int fill = left + Math.round((right - left) * progress);

        context.batcher.box(left, barY, right, barY + 2, 0x90000000);

        if (fill > left)
        {
            context.batcher.box(left, barY, fill, barY + 2, 0xE078BFFF);
        }
    }
}
