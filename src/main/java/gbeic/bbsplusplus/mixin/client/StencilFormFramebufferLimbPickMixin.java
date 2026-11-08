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
 * <p>按住「肢体选择」按键时，模板缓冲区一律记录点击到的实际肢体（原始骨骼），
 * 这样左键拾取天然就是「忽略覆盖」。用右键拾取时，才在这里把原始骨骼映射成
 * 模型的骨骼覆盖，得到与平常用左键一致的选中结果。</p>
 *
 * <p>把映射放在读取拾取结果这一刻（而不是渲染模板缓冲区那一刻），就不会因为
 * 模板缓冲区还停留在上一帧、或者渲染时还没按下鼠标键而选错骨骼。</p>
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
