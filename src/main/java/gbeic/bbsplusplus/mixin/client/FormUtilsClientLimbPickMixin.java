package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.client.ui.picking.LimbPickMode;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 肢体选择：让「当前选中的演员」在场景/影片预览里压在其他模型之前。
 *
 * <p>做法是把这只模型的深度值压到视锥最前面的一小段（{@code glDepthRange(0, 0.001)}），
 * 而不是清空深度缓冲：</p>
 * <ul>
 *     <li>早于它绘制的模型，深度值都大于这段范围，于是它不再被遮挡；</li>
 *     <li>晚于它绘制的模型，会拿自己的深度和这段「最靠前」的深度比较，同样被它挡住；</li>
 *     <li>模型自己的各个部位仍在这 0.001 的深度区间内按远近比较，前后关系保持正常；</li>
 *     <li>整帧的深度缓冲不会被破坏，随后的粒子、天气、半透明方块不受影响。</li>
 * </ul>
 */
@Mixin(value = FormUtilsClient.class, remap = false)
public class FormUtilsClientLimbPickMixin
{
    @Inject(method = "render", at = @At("HEAD"))
    private static void bbspp$limbPickRenderFront(Form form, FormRenderingContext context, CallbackInfo ci)
    {
        if (bbspp$limbPickIsSelected(context))
        {
            GL11.glDepthRange(0.0D, 0.001D);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private static void bbspp$limbPickRenderFrontReset(Form form, FormRenderingContext context, CallbackInfo ci)
    {
        if (bbspp$limbPickIsSelected(context))
        {
            GL11.glDepthRange(0.0D, 1.0D);
        }
    }

    private static boolean bbspp$limbPickIsSelected(FormRenderingContext context)
    {
        if (!LimbPickMode.held() || context == null || context.isPicking())
        {
            return false;
        }

        /* 洋葱皮、颜色覆盖那几遍是半透明的残影，不该跟着一起压到最前面 */
        if (context.color != -1)
        {
            return false;
        }

        IEntity selected = LimbPickMode.selectedEntity();

        return selected != null && context.entity == selected;
    }
}
