package gbeic.bbsplusplus.mixin.client;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Particle.class)
public interface ParticleColorAccessor
{
    @Accessor("red")
    float bbspp_cml$getRed();

    @Accessor("green")
    float bbspp_cml$getGreen();

    @Accessor("blue")
    float bbspp_cml$getBlue();

    @Accessor("alpha")
    float bbspp_cml$getAlpha();

    @Invoker("setAlpha")
    void bbspp_cml$setAlpha(float alpha);
}
