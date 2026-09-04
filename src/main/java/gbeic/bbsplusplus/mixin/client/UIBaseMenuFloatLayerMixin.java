package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.FloatLayerAccess;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 main 与 modal overlay 之间插入 floatLayer,小窗挂在此层:
 * 无遮罩、不拦截空白点击,且永远在设置类 UIOverlay 之下。
 */
@Mixin(value = UIBaseMenu.class, remap = false)
public abstract class UIBaseMenuFloatLayerMixin implements FloatLayerAccess
{
    @Shadow
    public UIElement main;

    @Shadow
    public UIElement overlay;

    @Shadow
    public Area viewport;

    @Unique
    private UIElement bbspp_cml$floatLayer;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$createFloatLayer(CallbackInfo ci)
    {
        UIBaseMenu self = (UIBaseMenu) (Object) this;

        this.bbspp_cml$floatLayer = new UIElement();
        this.bbspp_cml$floatLayer.full(this.viewport);
        /* 不 markContainer、默认 PASS:空白处不挡 main 的点击 */

        if (this.overlay != null && this.overlay.hasParent())
        {
            self.getRoot().addBefore(this.overlay, this.bbspp_cml$floatLayer);
        }
        else
        {
            self.getRoot().add(this.bbspp_cml$floatLayer);
        }
    }

    @Override
    public UIElement bbspp_cml$getFloatLayer()
    {
        if (this.bbspp_cml$floatLayer != null && !this.bbspp_cml$floatLayer.hasParent())
        {
            UIBaseMenu self = (UIBaseMenu) (Object) this;

            if (this.overlay != null && this.overlay.hasParent())
            {
                self.getRoot().addBefore(this.overlay, this.bbspp_cml$floatLayer);
            }
            else if (self.getRoot() != null)
            {
                self.getRoot().add(this.bbspp_cml$floatLayer);
            }
        }

        return this.bbspp_cml$floatLayer;
    }
}
