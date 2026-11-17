package gbeic.bbsplusplus.mixin12111;

import com.mojang.blaze3d.vertex.VertexFormat;
import gbeic.bbsplusplus.api.ParticlePlusParticle;
import gbeic.bbsplusplus.particles.components.ParticleComponentCollisionAppearance;
import gbeic.bbsplusplus.particles.components.ParticleComponentParticleMorph;
import mchorse.bbs_mod.particles.components.appearance.ParticleComponentAppearanceBillboard;
import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 基础 billboard 让位给 Particle Plus 的两条覆盖路径（1.21.11 版）。
 *
 * <p>与 1.20 版唯一的差别是 {@code VertexFormat} 换了包：1.21.11 只剩
 * {@code com.mojang.blaze3d.vertex.VertexFormat}，{@code net.minecraft.client.render} 下那个
 * 已经删掉了。逻辑本身没变，因为上游
 * {@code ParticleComponentAppearanceBillboard#render} 的形参在两版之间完全一致。</p>
 *
 * <p>两种取消条件（只在组件是原版 billboard 本体时生效，子类自己会走到这里再走
 * {@code super.render}）：</p>
 * <ol>
 *   <li>碰撞外观组件存在且该粒子这一 tick 撞到过东西 —— 该由
 *       {@code ParticleComponentCollisionAppearance} 分支来画；</li>
 *   <li>morph 已启用且没勾 {@code render_texture} —— 该粒子是个「只显示形态、不显示
 *       贴图」的 morph。</li>
 * </ol>
 */
@Environment(EnvType.CLIENT)
@Mixin(value = ParticleComponentAppearanceBillboard.class, priority = 1001, remap = false)
public class ParticleComponentAppearanceBillboardMixin121
{
    @Inject(
        method = "render(Lmchorse/bbs_mod/particles/emitter/ParticleEmitter;Lcom/mojang/blaze3d/vertex/VertexFormat;Lmchorse/bbs_mod/particles/emitter/Particle;Lnet/minecraft/client/render/BufferBuilder;Lorg/joml/Matrix4f;IF)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void bbspp$beforeRender(
        ParticleEmitter emitter,
        VertexFormat format,
        Particle particle,
        BufferBuilder builder,
        Matrix4f matrix,
        int overlay,
        float transition,
        CallbackInfo ci
    )
    {
        if (!this.getClass().equals(ParticleComponentAppearanceBillboard.class) || emitter.scheme == null)
        {
            return;
        }

        ParticleComponentCollisionAppearance collision = emitter.scheme.get(ParticleComponentCollisionAppearance.class);

        if (collision != null && ((ParticlePlusParticle) particle).bbspp_cml$isIntersected())
        {
            ci.cancel();

            return;
        }

        ParticleComponentParticleMorph morph = emitter.scheme.get(ParticleComponentParticleMorph.class);

        if (morph != null && morph.enabled && !morph.renderTexture)
        {
            ci.cancel();
        }
    }
}
