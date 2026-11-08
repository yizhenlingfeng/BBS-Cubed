package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.client.ui.picking.LimbPickMode;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.cubic.ModelInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 肢体选择模式下「焊接（weld）」模型的原版着色器切换。
 *
 * <p>普通模型走 {@code ModelInstance.render}，着色器由 {@code ModelFormRenderer.render3D}
 * 决定（见 {@code ModelFormRendererLimbPickMixin}）；带焊接的模型走
 * {@code ModelInstance.renderHybrid}，那边会用 {@code explicitWeld} 再决定一次着色器：
 * UI 预览里 {@code renderingWorld} 为 false，于是它恒定把着色器换回 {@code BBSShaders.getModel()}。</p>
 *
 * <p>按住按键时把这两处判断「骗」成世界渲染 + 开了光影，让 {@code explicitWeld} 变成 false，
 * 于是它用调用方传进来的着色器（也就是切换到原版实体着色器后的那个）。</p>
 *
 * <p>注意：这里刻意不去动 {@code isVAORendered()}。把它改成 false 能把模型切到原版着色器，
 * 但那会同时把渲染从 GPU 的 VAO 快路径踢到每帧逐顶点 CPU 变换的立即模式，
 * 场景里演员一多就会严重掉帧。切着色器只需要换 program，不必放弃 VAO。</p>
 */
@Mixin(value = ModelInstance.class, remap = false)
public abstract class ModelInstanceVanillaRenderMixin
{
    @Redirect(method = "renderHybrid", at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/client/BBSRendering;isIrisShadersEnabled()Z"))
    private boolean bbspp$limbPickHybridShaders()
    {
        return LimbPickMode.held() || BBSRendering.isIrisShadersEnabled();
    }

    @Redirect(method = "renderHybrid", at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/client/BBSRendering;isRenderingWorld()Z"))
    private boolean bbspp$limbPickHybridWorld()
    {
        return LimbPickMode.held() || BBSRendering.isRenderingWorld();
    }
}
