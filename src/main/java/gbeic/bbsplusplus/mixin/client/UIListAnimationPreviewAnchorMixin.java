package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.client.ui.animations.UIAnimationPreviewTooltip;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.items.UIItems;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.tooltips.ITooltip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 在 renderContent 期间（外层滚动 shift 仍生效）算出悬停行动视觉锚点写入 tooltip；
 * postRender 阶段坐标已还原，无法再调 getHoveredIndex。
 * area/scroll/tooltip 继承自父类，用强转访问（与 UIListPoseParameterBrushMixin 一致）。
 */
@Mixin(UIList.class)
public abstract class UIListAnimationPreviewAnchorMixin
{
    @Shadow
    public abstract int getHoveredIndex(UIContext context);

    @Shadow
    protected abstract List<?> visible();

    @Inject(method = "renderContent", at = @At("HEAD"), remap = false)
    private void bbspp$feedAnimationPreview(UIContext context, CallbackInfo ci)
    {
        UIElement self = (UIElement) (Object) this;

        if (!(self.tooltip instanceof UIAnimationPreviewTooltip tip))
        {
            return;
        }

        tip.clear();

        int row = this.getHoveredIndex(context);

        if (row <= 0)
        {
            return;
        }

        List<?> items = this.visible();

        if (row >= items.size() || !(items.get(row) instanceof String animation))
        {
            return;
        }

        UIItems itemsSelf = (UIItems) (Object) this;
        int s = itemsSelf.scroll.scrollItemSize;
        int rowBottom = self.area.y + (row + 1) * s - (int) itemsSelf.scroll.getScroll();

        tip.prepare(animation, context.globalX(self.area.ex()), context.globalY(rowBottom));
    }
}
