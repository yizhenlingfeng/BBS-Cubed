package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.client.screen.VanillaParticleGradeContext;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies a vanilla-particle form's evaluated appearance to particles created in its tick. */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerVanillaFormMixin
{
    @Inject(
        method = "addParticle(Lnet/minecraft/client/particle/Particle;)V",
        at = @At("HEAD"),
        require = 0
    )
    private void bbspp_cml$applyVanillaFormGrade(Particle particle, CallbackInfo ci)
    {
        VanillaParticleGradeContext.capture(particle);
    }

    @Redirect(
        method = "renderParticles",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/particle/Particle;buildGeometry(Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/client/render/Camera;F)V"
        ),
        require = 0
    )
    private void bbspp_cml$renderVanillaFormGrade(
        Particle particle,
        VertexConsumer vertices,
        Camera camera,
        float tickDelta
    )
    {
        VanillaParticleGradeContext.render(particle, vertices, camera, tickDelta);
    }
}
