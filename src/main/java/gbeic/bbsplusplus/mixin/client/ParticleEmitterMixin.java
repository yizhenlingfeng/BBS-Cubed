package gbeic.bbsplusplus.mixin.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import gbeic.bbsplusplus.api.ParticlePlusParticle;
import gbeic.bbsplusplus.particles.ParticlePlusClient;
import gbeic.bbsplusplus.particles.components.ParticleComponentParticleMorph;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.particles.ParticleMaterial;
import mchorse.bbs_mod.particles.ParticleScheme;
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
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexFormat;
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
import java.util.function.Supplier;

@Environment(EnvType.CLIENT)
@Mixin(value = ParticleEmitter.class, priority = 1002, remap = false)
public class ParticleEmitterMixin
{
    @Shadow
    public ParticleScheme scheme;

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
    private final FormRenderingContext bbspp_cml$formContext = new FormRenderingContext();

    @Unique
    private final Vector3f bbspp_cml$particlePosition = new Vector3f();

    @Unique
    private final Vector3f bbspp_cml$facingDirection = new Vector3f();

    @Unique
    private final Matrix4f bbspp_cml$emitterTransform = new Matrix4f();

    @Inject(method = "updateParticle", at = @At("HEAD"))
    private void bbspp_cml$resetCollision(Particle particle, CallbackInfo ci)
    {
        ((ParticlePlusParticle) particle).bbspp_cml$setIntersected(false);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void bbspp_cml$beforeRender(VertexFormat format, Supplier<ShaderProgram> program, MatrixStack stack, int overlay, float transition, CallbackInfo ci)
    {
        if (this.scheme == null)
        {
            return;
        }

        this.bbspp_cml$setupBlendState(this.scheme.material);

        ParticleComponentParticleMorph morph = this.scheme.get(ParticleComponentParticleMorph.class);

        if (morph == null || !morph.enabled || morph.form == null)
        {
            return;
        }

        ParticleEmitter emitter = (ParticleEmitter) (Object) this;
        ParticleComponentAppearanceBillboard appearance = this.bbspp_cml$getAppearance();
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
                this.bbspp_cml$particlePosition.set((float) worldX, (float) worldY, (float) worldZ);
                this.rotation.transform(this.bbspp_cml$particlePosition);

                worldX = this.bbspp_cml$particlePosition.x + emitter.lastGlobal.x;
                worldY = this.bbspp_cml$particlePosition.y + emitter.lastGlobal.y;
                worldZ = this.bbspp_cml$particlePosition.z + emitter.lastGlobal.z;
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
            this.bbspp_cml$formContext.set(FormRenderType.ENTITY, dummy, stack, light, overlay, transition);
            this.bbspp_cml$formContext.camera(MinecraftClient.getInstance().gameRenderer.getCamera());

            stack.push();
            stack.translate(x, y, z);

            if (useCameraFacing)
            {
                this.bbspp_cml$applyCameraFacing(stack, emitter, particle, appearance, staticSpace, worldX, worldY, worldZ);
            }
            else if (particle.relativeRotation)
            {
                this.bbspp_cml$emitterTransform.set(this.rotation);
                MatrixStackUtils.multiply(stack, this.bbspp_cml$emitterTransform);
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

                scale *= scaleMatrix.getRow(0, this.bbspp_cml$facingDirection).length();
            }

            if (scale != 1F)
            {
                stack.scale(scale, scale, scale);
            }

            FormUtilsClient.render(morph.form, this.bbspp_cml$formContext);
            stack.pop();
        }
    }

    @Unique
    private ParticleComponentAppearanceBillboard bbspp_cml$getAppearance()
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
    private void bbspp_cml$applyCameraFacing(
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
            this.bbspp_cml$applyDirectionFacing(stack, emitter, particle, appearance, staticSpace, facing);
        }
    }

    @Unique
    private void bbspp_cml$applyDirectionFacing(
        MatrixStack stack,
        ParticleEmitter emitter,
        Particle particle,
        ParticleComponentAppearanceBillboard appearance,
        boolean staticSpace,
        CameraFacing facing
    )
    {
        this.bbspp_cml$updateFacingDirection(emitter, particle, appearance, staticSpace);

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
    private void bbspp_cml$updateFacingDirection(
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

        this.bbspp_cml$facingDirection.set(
            (float) (particle.position.x - particle.prevPosition.x),
            (float) (particle.position.y - particle.prevPosition.y),
            (float) (particle.position.z - particle.prevPosition.z)
        );

        if (staticSpace)
        {
            emitter.rotation.transform(this.bbspp_cml$facingDirection);
        }

        float length = this.bbspp_cml$facingDirection.length();
        float threshold = appearance == null ? 0.01F : appearance.minSpeedThreshold;

        if (length * length > 1.0E-8F && length * 20F >= threshold)
        {
            particle.facingDirection.set(this.bbspp_cml$facingDirection).normalize();
        }
    }

    @Redirect(
        method = "render",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;disableBlend()V")
    )
    private void bbspp_cml$keepConfiguredBlend()
    {
        if (this.scheme == null || this.scheme.material != ParticleMaterial.BLEND && !ParticlePlusClient.isAdditive(this.scheme.material))
        {
            RenderSystem.disableBlend();
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void bbspp_cml$afterRender(VertexFormat format, Supplier<ShaderProgram> program, MatrixStack stack, int overlay, float transition, CallbackInfo ci)
    {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
    }

    @Unique
    private void bbspp_cml$setupBlendState(ParticleMaterial material)
    {
        if (ParticlePlusClient.isAdditive(material))
        {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
        }
        else if (material == ParticleMaterial.BLEND)
        {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
        }
        else
        {
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
        }
    }
}
