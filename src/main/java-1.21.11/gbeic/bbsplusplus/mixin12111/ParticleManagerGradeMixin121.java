package gbeic.bbsplusplus.mixin12111;

import gbeic.bbsplusplus.client.particle.VanillaParticleGrade121;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在粒子进入世界的那一刻记下它属于哪个 {@code VanillaParticleForm} 的 tick。
 *
 * <p>1.21.11 的 {@code ParticleManager} 没有 {@code renderParticles}，只剩
 * {@code addToBatch}/{@code addParticle}；因此捕获时机选在
 * {@code addParticle(Particle)} 的 HEAD —— BBS 的原版粒子经
 * {@code ClientWorld.addParticleClient(...)} 最终都汇到这里。</p>
 */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerGradeMixin121
{
    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), require = 0)
    private void bbspp$captureParticleGrade(Particle particle, CallbackInfo ci)
    {
        VanillaParticleGrade121.capture(particle);
    }
}
