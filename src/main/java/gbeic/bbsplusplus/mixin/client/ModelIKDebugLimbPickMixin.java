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
 * <p>BBS 的 IK 叠加层（{@code ModelIKDebug}）是在 {@code ModelFormRenderer.renderModel}
 * 里、模型绘制完之后直接追加的，关闭深度测试时（{@code ikDebug.xray}）会压在回放里的
 * 演员模型之上；它同时还会往模板缓冲区里登记可拾取的 target/pole 标记，按住键选肢体时
 * 有可能先命中这些标记。</p>
 *
 * <p>所以按住「肢体选择」键时把两处都跳过：画面上不再有线条/小球挡住回放图层，
 * 拾取也只剩真正的骨骼。松开按键立刻恢复，不改任何设置项。</p>
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
