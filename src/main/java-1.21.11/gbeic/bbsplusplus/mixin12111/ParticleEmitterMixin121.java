package gbeic.bbsplusplus.mixin12111;

import com.mojang.blaze3d.vertex.VertexFormat;
import gbeic.bbsplusplus.api.ParticlePlusParticle;
import gbeic.bbsplusplus.client.particle.ParticlePlusLayers121;
import gbeic.bbsplusplus.particles.components.ParticleComponentParticleMorph;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.particles.components.ParticleComponentBase;
import mchorse.bbs_mod.particles.components.appearance.BillboardDirection;
import mchorse.bbs_mod.particles.components.appearance.CameraFacing;
import mchorse.bbs_mod.particles.components.appearance.ParticleComponentAppearanceBillboard;
import mchorse.bbs_mod.particles.emitter.Particle;
import mchorse.bbs_mod.particles.emitter.ParticleEmitter;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Particle Plus 的发射器侧实现：morph 形态渲染 + Additive 材质换层。
 *
 * <p>1.21.11 的 {@code ParticleEmitter#render} 已经不带
 * {@code Supplier<ShaderProgram>}、{@code boolean} 参数，也没有任何
 * {@code RenderSystem.disableBlend()} 调用 —— 混合、深度写、剔除全部固化在传入的
 * {@code RenderLayer} 背后。所以 1.20 版那两个 {@code @Redirect}/{@code @Inject}
 * 的 GL 状态补丁在这里整体不存在了，取而代之的是：</p>
 * <ul>
 *   <li>HEAD：在粒子几何之前把 morph 表单逐粒子画一遍（与 1.20 的插入点同序）；</li>
 *   <li>{@code layer.draw(built)} 的 {@code @Redirect}：Additive 材质时换成自建的
 *       Additive 管线层，其余材质原样放行。</li>
 * </ul>
 *
 * <p>顺序很重要：Additive 层 {@code depthWrite(false)}，必须在原版粒子几何之外单独提交，
 * 不能靠改原层的状态 —— {@code RenderLayer} 是不可变的。</p>
 */
@Environment(EnvType.CLIENT)
@Mixin(value = ParticleEmitter.class, priority = 1002, remap = false)
public class ParticleEmitterMixin121
{
    @Shadow
    public mchorse.bbs_mod.particles.ParticleScheme scheme;

    @Shadow
    public List<Particle> particles;

    @Shadow
    public float cYaw;

    @Shadow
    public float cPitch;

    @Shadow
    public double cX;

    @Shadow
    public double cY;

    @Shadow
    public double cZ;

    @Shadow
    public Matrix3f rotation;

    @Unique
    private final FormRenderingContext bbspp$formContext = new FormRenderingContext();

    @Unique
    private final Vector3f bbspp$particlePosition = new Vector3f();

    @Unique
    private final Vector3f bbspp$facingDirection = new Vector3f();

    @Unique
    private final Matrix4f bbspp$emitterTransform = new Matrix4f();

    @Inject(method = "updateParticle(Lmchorse/bbs_mod/particles/emitter/Particle;)V", at = @At("HEAD"), require = 0)
    private void bbspp$resetCollision(Particle particle, CallbackInfo ci)
    {
        ((ParticlePlusParticle) particle).bbspp_cml$setIntersected(false);
    }

    @Inject(
        method = "render(Lcom/mojang/blaze3d/vertex/VertexFormat;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/util/math/MatrixStack;IF)V",
        at = @At("HEAD"),
        require = 0
    )
    private void bbspp$beforeRender(VertexFormat format, RenderLayer layer, MatrixStack stack, int overlay, float transition, CallbackInfo ci)
    {
        if (this.scheme == null)
        {
            return;
        }

        ParticleComponentParticleMorph morph = this.scheme.get(ParticleComponentParticleMorph.class);

        if (morph == null || !morph.enabled || morph.form == null)
        {
            return;
        }

        ParticleEmitter emitter = (ParticleEmitter) (Object) this;
        ParticleComponentAppearanceBillboard appearance = this.bbspp$getAppearance();
        boolean useCameraFacing = appearance != null || morph.billboard;

        emitter.setEmitterVariables(transition);

        for (Particle particle : this.particles)
        {
            emitter.setParticleVariables(particle, transition);

            double worldX = Lerps.lerp(particle.prevPosition.x, particle.position.x, transition);
            double worldY = Lerps.lerp(particle.prevPosition.y, particle.position.y, transition);
            double worldZ = Lerps.lerp(particle.prevPosition.z, particle.position.z, transition);
            boolean staticSpace = particle.relativePosition && particle.relativeRotation;

            if (staticSpace)
            {
                this.bbspp$particlePosition.set((float) worldX, (float) worldY, (float) worldZ);
                this.rotation.transform(this.bbspp$particlePosition);

                worldX = this.bbspp$particlePosition.x + emitter.lastGlobal.x;
                worldY = this.bbspp$particlePosition.y + emitter.lastGlobal.y;
                worldZ = this.bbspp$particlePosition.z + emitter.lastGlobal.z;
            }

            double x = worldX - this.cX;
            double y = worldY - this.cY;
            double z = worldZ - this.cZ;
            int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;

            if (!emitter.lit && emitter.world != null)
            {
                light = WorldRenderer.getLightmapCoordinates(emitter.world, BlockPos.ofFloored(worldX, worldY, worldZ));
            }

            StubEntity dummy = ((ParticlePlusParticle) particle).bbspp_cml$getDummy(emitter);

            dummy.setAge(particle.age);
            this.bbspp$formContext.set(FormRenderType.ENTITY, dummy, stack, light, overlay, transition);
            this.bbspp$formContext.camera(MinecraftClient.getInstance().gameRenderer.getCamera());

            stack.push();
            stack.translate(x, y, z);

            if (useCameraFacing)
            {
                this.bbspp$applyCameraFacing(stack, emitter, particle, appearance, staticSpace, worldX, worldY, worldZ);
            }
            else if (particle.relativeRotation)
            {
                this.bbspp$emitterTransform.set(this.rotation);
                MatrixStackUtils.multiply(stack, this.bbspp$emitterTransform);
            }

            float rotation = Lerps.lerp(particle.prevRotation, particle.rotation, transition);

            if (rotation != 0F)
            {
                stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
            }

            float scale = morph.getScale();

            if (useCameraFacing && particle.textureScale)
            {
                Matrix3f scaleMatrix = staticSpace ? this.rotation : particle.matrix;

                scale *= scaleMatrix.getRow(0, this.bbspp$facingDirection).length();
            }

            if (scale != 1F)
            {
                stack.scale(scale, scale, scale);
            }

            FormUtilsClient.render(morph.form, this.bbspp$formContext);
            stack.pop();
        }
    }

    /**
     * Additive 材质的换层点。
     *
     * <p>原实现是「画前 {@code blendFunc(SRC_ALPHA, ONE)}＋关深度写」，在 1.21.11 里
     * 这两项都属于 {@code RenderPipeline}，改不了。所以这里把这一步挪到提交时：
     * Additive 时用自建层画同一份 {@code BuiltBuffer}，否则照原样调回去。</p>
     */
    @Redirect(
        method = "render(Lcom/mojang/blaze3d/vertex/VertexFormat;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/util/math/MatrixStack;IF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/RenderLayer;draw(Lnet/minecraft/client/render/BuiltBuffer;)V"),
        require = 0
    )
    private void bbspp$drawAdditive(RenderLayer instance, BuiltBuffer built)
    {
        if (this.scheme != null && ParticlePlusLayers121.shouldUseAdditive(this.scheme.material))
        {
            ParticlePlusLayers121.draw(built, instance);

            return;
        }

        instance.draw(built);
    }

    @Unique
    private ParticleComponentAppearanceBillboard bbspp$getAppearance()
    {
        ParticleComponentAppearanceBillboard fallback = null;

        for (ParticleComponentBase component : this.scheme.components)
        {
            if (component.getClass() == ParticleComponentAppearanceBillboard.class)
            {
                return (ParticleComponentAppearanceBillboard) component;
            }

            if (fallback == null && component instanceof ParticleComponentAppearanceBillboard billboard)
            {
                fallback = billboard;
            }
        }

        return fallback;
    }

    @Unique
    private void bbspp$applyCameraFacing(
        MatrixStack stack,
        ParticleEmitter emitter,
        Particle particle,
        ParticleComponentAppearanceBillboard appearance,
        boolean staticSpace,
        double worldX,
        double worldY,
        double worldZ
    )
    {
        CameraFacing facing = appearance == null ? CameraFacing.ROTATE_XYZ : appearance.facing;
        float yaw = this.cYaw;
        float pitch = this.cPitch;

        if (facing == CameraFacing.LOOKAT_XYZ || facing == CameraFacing.LOOKAT_Y)
        {
            double dX = this.cX - worldX;
            double dY = this.cY - worldY;
            double dZ = this.cZ - worldZ;
            double horizontalDistance = Math.sqrt(dX * dX + dZ * dZ);

            yaw = 180F - (float) Math.toDegrees(Math.atan2(dZ, dX)) - 90F;
            pitch = -(float) Math.toDegrees(Math.atan2(dY, horizontalDistance)) + 180F;
        }

        if (facing == CameraFacing.ROTATE_XYZ || facing == CameraFacing.LOOKAT_XYZ)
        {
            stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        }
        else if (facing == CameraFacing.ROTATE_Y || facing == CameraFacing.LOOKAT_Y)
        {
            stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        }
        else
        {
            this.bbspp$applyDirectionFacing(stack, emitter, particle, appearance, staticSpace, facing);
        }
    }

    @Unique
    private void bbspp$applyDirectionFacing(
        MatrixStack stack,
        ParticleEmitter emitter,
        Particle particle,
        ParticleComponentAppearanceBillboard appearance,
        boolean staticSpace,
        CameraFacing facing
    )
    {
        this.bbspp$updateFacingDirection(emitter, particle, appearance, staticSpace);

        float nx = particle.facingDirection.x;
        float ny = particle.facingDirection.y;
        float nz = particle.facingDirection.z;

        if (ny == 1F)
        {
            ny = -1F;
        }
        else if (ny == -1F)
        {
            ny = 1F;
            nz = -1.0E-5F;
        }

        float yaw = (float) Math.atan2(nx, nz);
        float pitch = (float) Math.atan2(ny, Math.sqrt(nx * nx + nz * nz));
        float halfPi = (float) (Math.PI / 2D);

        if (facing == CameraFacing.DIRECTION_X)
        {
            stack.multiply(RotationAxis.POSITIVE_Y.rotation(yaw - halfPi));
            stack.multiply(RotationAxis.POSITIVE_Z.rotation(pitch));
        }
        else if (facing == CameraFacing.DIRECTION_Y)
        {
            stack.multiply(RotationAxis.POSITIVE_Y.rotation(yaw - (float) Math.PI));
            stack.multiply(RotationAxis.POSITIVE_X.rotation(pitch - halfPi));
        }
        else
        {
            stack.multiply(RotationAxis.POSITIVE_Y.rotation(yaw));
            stack.multiply(RotationAxis.POSITIVE_X.rotation(-pitch));
        }
    }

    @Unique
    private void bbspp$updateFacingDirection(
        ParticleEmitter emitter,
        Particle particle,
        ParticleComponentAppearanceBillboard appearance,
        boolean staticSpace
    )
    {
        if (appearance != null && appearance.directionMode == BillboardDirection.CUSTOM_DIRECTION)
        {
            particle.facingDirection.set(
                (float) appearance.customDirection[0].get(),
                (float) appearance.customDirection[1].get(),
                (float) appearance.customDirection[2].get()
            );

            if (particle.facingDirection.lengthSquared() > 1.0E-8F)
            {
                particle.facingDirection.normalize();
            }

            return;
        }

        this.bbspp$facingDirection.set(
            (float) (particle.position.x - particle.prevPosition.x),
            (float) (particle.position.y - particle.prevPosition.y),
            (float) (particle.position.z - particle.prevPosition.z)
        );

        if (staticSpace)
        {
            emitter.rotation.transform(this.bbspp$facingDirection);
        }

        float length = this.bbspp$facingDirection.length();
        float threshold = appearance == null ? 0.01F : appearance.minSpeedThreshold;

        if (length * length > 1.0E-8F && length * 20F >= threshold)
        {
            particle.facingDirection.set(this.bbspp$facingDirection).normalize();
        }
    }
}
