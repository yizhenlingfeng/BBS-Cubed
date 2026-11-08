package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.client.ui.picking.LimbPickMode;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 肢体选择模式下的原版实体着色器。
 *
 * <p>{@code ModelFormRenderer.render3D} 用
 * {@code !isIrisWorldShadersEnabled() && model.isVAORendered() ? BBSShaders.getModel() : 原版实体着色器}
 * 挑主着色器。按住按键时把第一个判断抬成 true，就会落到原版实体着色器那一支，而
 * {@code isVAORendered()} 与 VAO 快路径原封不动 —— 只换 program，不换渲染管线。</p>
 *
 * <p>render3D 里有两处同样的判断（主通道与第二遍），方法级 redirect 会一起命中。</p>
 */
@Mixin(value = ModelFormRenderer.class, remap = false)
public abstract class ModelFormRendererLimbPickMixin
{
    @Redirect(method = "render3D", at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/client/BBSRendering;isIrisWorldShadersEnabled()Z"))
    private boolean bbspp$limbPickVanillaShader()
    {
        return LimbPickMode.held() || BBSRendering.isIrisWorldShadersEnabled();
    }
}
