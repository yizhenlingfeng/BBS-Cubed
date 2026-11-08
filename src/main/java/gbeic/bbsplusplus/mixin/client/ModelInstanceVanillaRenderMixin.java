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
 * <p>普通模型的着色器由 {@code ModelFormRenderer.render3D} 决定（见
 * {@code ModelFormRendererLimbPickMixin}）；带焊接的模型还会在 {@code renderHybrid} 里按
 * {@code explicitWeld} 再决定一次，而 UI 预览里 {@code renderingWorld} 为 false，于是恒定换回
 * {@code BBSShaders.getModel()}。这里把这两处判断「骗」成世界渲染 + 开了光影即可沿用调用方的着色器。</p>
 *
 * <p>刻意不动 {@code isVAORendered()}：它会同时把渲染从 GPU 的 VAO 快路径踢到逐顶点 CPU 变换的
 * 立即模式，演员一多就严重掉帧，而切着色器只需要换 program。</p>
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
