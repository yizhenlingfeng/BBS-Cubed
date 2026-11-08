package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.client.ui.picking.LimbPickMode;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.utils.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 肢体选择模式下拾取结果的骨骼覆盖映射。
 *
 * <p>按住按键时模板缓冲区一律记录实际肢体，所以左键拾取天然就是「忽略覆盖」；右键拾取时在这里把
 * 实际肢体映射成骨骼覆盖。映射放在读取结果这一刻，模板缓冲区停留在上一帧也不会选错骨骼。</p>
 */
@Mixin(value = StencilFormFramebuffer.class, remap = false)
public class StencilFormFramebufferLimbPickMixin
{
    @Inject(method = "getPicked", at = @At("RETURN"), cancellable = true)
    private void bbspp$limbPickPickedBone(CallbackInfoReturnable<Pair<Form, String>> cir)
    {
        Pair<Form, String> picked = cir.getReturnValue();

        if (picked == null || !LimbPickMode.respectOverrides())
        {
            return;
        }

        Pair<Form, String> mapped = LimbPickMode.withPickingOverrides(picked);

        if (mapped != picked)
        {
            cir.setReturnValue(mapped);
        }
    }
}
