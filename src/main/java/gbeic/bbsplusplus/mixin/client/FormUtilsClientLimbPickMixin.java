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
 * 肢体选择：让当前选中的演员在场景 / 影片预览里压在其他模型之前。
 *
 * <p>做法是把这只模型的深度压到视锥最前面的一小段（{@code glDepthRange(0, 0.001)}）：先画的模型
 * 盖不住它、后画的模型仍会被它挡住，它自己的各个部位照常按远近排序，而整帧的深度缓冲不受影响，
 * 比清空深度缓冲安全得多。</p>
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
