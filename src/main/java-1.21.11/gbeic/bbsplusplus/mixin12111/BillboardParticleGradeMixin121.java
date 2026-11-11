package gbeic.bbsplusplus.mixin12111;

import gbeic.bbsplusplus.client.particle.VanillaParticleGrade121;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.BillboardParticleSubmittable;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把 {@code VanillaParticleForm} 的着色应用到原版粒子上。
 *
 * <p>1.21.11 里 {@code Particle} 已经没有颜色字段，也没有 {@code buildGeometry}；
 * 颜色搬到了 {@link BillboardParticle} 的 {@code red/green/blue/alpha}，唯一的读取点
 * 是 {@code renderVertex}（由 {@code render} 调用四次）。所以这里在公开
 * {@code render} 的 HEAD 临时改写这四个字段、RETURN 复原 —— 不改顶点、不碰渲染管线，
 * 因此对透明/发光/Iris 各条路径都成立。</p>
 */
@Mixin(BillboardParticle.class)
public abstract class BillboardParticleGradeMixin121
{
    @Shadow protected float red;
    @Shadow protected float green;
    @Shadow protected float blue;
    @Shadow protected float alpha;

    @Unique private float bbspp$savedRed;
    @Unique private float bbspp$savedGreen;
    @Unique private float bbspp$savedBlue;
    @Unique private float bbspp$savedAlpha;
    @Unique private boolean bbspp$graded;

    @Inject(
        method = "render(Lnet/minecraft/client/particle/BillboardParticleSubmittable;Lnet/minecraft/client/render/Camera;F)V",
        at = @At("HEAD"),
        require = 0
    )
    private void bbspp$applyGrade(BillboardParticleSubmittable submittable, Camera camera, float tickDelta, CallbackInfo ci)
    {
        this.bbspp$graded = false;

        VanillaParticleGrade121.State state = VanillaParticleGrade121.stateOf((Particle) (Object) this);

        if (state == null)
        {
            return;
        }

        float[] graded = VanillaParticleGrade121.apply(state, this.red, this.green, this.blue, this.alpha);

        this.bbspp$savedRed = this.red;
        this.bbspp$savedGreen = this.green;
        this.bbspp$savedBlue = this.blue;
        this.bbspp$savedAlpha = this.alpha;
        this.red = graded[0];
        this.green = graded[1];
        this.blue = graded[2];
        this.alpha = graded[3];
        this.bbspp$graded = true;
    }

    @Inject(
        method = "render(Lnet/minecraft/client/particle/BillboardParticleSubmittable;Lnet/minecraft/client/render/Camera;F)V",
        at = @At("RETURN"),
        require = 0
    )
    private void bbspp$restoreGrade(BillboardParticleSubmittable submittable, Camera camera, float tickDelta, CallbackInfo ci)
    {
        if (!this.bbspp$graded)
        {
            return;
        }

        this.red = this.bbspp$savedRed;
        this.green = this.bbspp$savedGreen;
        this.blue = this.bbspp$savedBlue;
        this.alpha = this.bbspp$savedAlpha;
        this.bbspp$graded = false;
    }
}
