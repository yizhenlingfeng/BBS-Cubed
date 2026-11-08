package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.client.ui.picking.LimbPickMode;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ik.ModelIKDebug;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 肢体选择模式下隐藏 IK 调试叠加层。
 *
 * <p>{@code ModelIKDebug} 在模型绘制完之后追加叠加层，关闭深度测试时会压在回放图层之上，还会往
 * 模板缓冲区登记可拾取的 target/pole 标记。按住按键时两处都跳过：画面干净，拾取只剩真正的骨骼。</p>
 */
@Mixin(value = ModelIKDebug.class, remap = true)
public abstract class ModelIKDebugLimbPickMixin
{
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private static void bbspp$limbPickHideIKDebug(MatrixStack stack, IModel model, ModelForm form, String selectedTip, CallbackInfo ci)
    {
        if (LimbPickMode.held())
        {
            ci.cancel();
        }
    }

    @Inject(method = "renderStencil", at = @At("HEAD"), cancellable = true)
    private static void bbspp$limbPickHideIKDebugStencil(MatrixStack stack, IModel model, ModelForm modelForm, StencilMap stencilMap, Form form, CallbackInfo ci)
    {
        if (LimbPickMode.held())
        {
            ci.cancel();
        }
    }
}
