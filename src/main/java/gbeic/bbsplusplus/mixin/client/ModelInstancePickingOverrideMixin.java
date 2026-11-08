package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.client.ui.picking.LimbPickMode;
import mchorse.bbs_mod.cubic.ModelInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 按住「肢体选择」按键时，模板缓冲区记录实际点击到的肢体，忽略骨骼选取覆盖。
 *
 * <p>按键仅做被动检测，不消费输入事件，且只在 BBS 界面内生效。想按覆盖选取时的映射见
 * {@code StencilFormFramebufferLimbPickMixin}。</p>
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
        if (LimbPickMode.held())
        {
            return bone;
        }

        return this.getPickingBone(bone);
    }
}
