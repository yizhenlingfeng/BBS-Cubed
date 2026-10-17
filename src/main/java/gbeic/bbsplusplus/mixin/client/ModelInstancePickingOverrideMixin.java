package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.ui.forms.SnowUIKeys;
import mchorse.bbs_mod.cubic.ModelInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 允许在模型编辑器中按住指定按键（默认 E）跳过骨骼选取覆盖，
 * 直接选中实际点击的肢体。该按键仅做被动检测，不消费输入事件。
 */
@Mixin(value = ModelInstance.class, remap = false)
public abstract class ModelInstancePickingOverrideMixin
{
    @Shadow
    private native String getPickingBone(String bone);

    @Redirect(
        method = "fillStencilMap",
        at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/cubic/ModelInstance;getPickingBone(Ljava/lang/String;)Ljava/lang/String;")
    )
    private String bbspp$ignorePickingOverride(ModelInstance instance, String bone)
    {
        if (SnowUIKeys.IGNORE_PICKING_OVERRIDE_KEY.isHeld())
        {
            return bone;
        }

        return this.getPickingBone(bone);
    }
}
