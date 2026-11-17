package gbeic.bbsplusplus.client.particle;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import gbeic.bbsplusplus.BBSPlusPlusMod;
import gbeic.bbsplusplus.particles.ParticlePlusClient;
import mchorse.bbs_mod.particles.ParticleMaterial;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/**
 * Additive（叠加）粒子材质用的专属渲染层。
 *
 * <p>1.20.x 里混合方式是画前用 {@code RenderSystem.blendFunc(SRC_ALPHA, ONE)} 临时改的，
 * 1.21.11 已经没有这套立即模式状态 —— blend / cull / depth 全部固化在提交所用的
 * {@code RenderLayer} 背后的 {@code RenderPipeline} 上。上游只造了 opaque 与 blend 两种粒子
 * 管线，所以 Additive 只能自己造一条：着色器、顶点格式、UBO、采样器与
 * {@code BBSShaders.registerParticles} 完全一致，只有 blend 函数与深度写不同。</p>
 *
 * <p>Sampler0 不在这里绑定 —— 和上游的粒子层一样，靠
 * {@code ParticleEmitter.bindTexture()} 的那次全局绑定从外层传进来。</p>
 *
 * <p>任何一步失败都只置 {@code broken} 并返回 {@code null}，调用方回落到上游给的层，
 * 效果是「Additive 看起来像 Opaque」而不是崩游戏。</p>
 */
public final class ParticlePlusLayers121
{
    private static RenderLayer additiveLayer;
    private static boolean broken;

    private ParticlePlusLayers121()
    {}

    /**
     * @return Additive 粒子层；管线建不起来时返回 {@code null}
     */
    public static RenderLayer additive()
    {
        if (broken)
        {
            return null;
        }

        if (additiveLayer == null)
        {
            try
            {
                additiveLayer = create();
            }
            catch (Throwable throwable)
            {
                broken = true;
                BBSPlusPlusMod.LOGGER.warn("[Particle+] Additive 粒子管线创建失败，将回落到普通粒子层", throwable);
            }
        }

        return additiveLayer;
    }

    /** 该材质是否该走 Additive 层。 */
    public static boolean shouldUseAdditive(ParticleMaterial material)
    {
        return ParticlePlusClient.isAdditive(material);
    }

    /** 调用方在已经拿到 {@code BuiltBuffer} 之后换层重投。 */
    public static void draw(BuiltBuffer built, RenderLayer fallback)
    {
        RenderLayer additive = additive();

        (additive == null ? fallback : additive).draw(built);
    }

    private static RenderLayer create()
    {
        Identifier shader = Identifier.of("bbs", "core/particles");
        RenderPipeline pipeline = RenderPipelines.register(
            RenderPipeline.builder()
                .withLocation(Identifier.of(BBSPlusPlusMod.MOD_ID, "pipeline/particles_additive"))
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_LIGHT, VertexFormat.DrawMode.QUADS)
                .withBlend(BlendFunction.ADDITIVE)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("Fog", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withSampler("Sampler2")
                .build()
        );

        RenderLayer layer = RenderLayer.of(
            BBSPlusPlusMod.MOD_ID + "_particles_additive",
            RenderSetup.builder(pipeline)
                .expectedBufferSize(RenderLayer.field_64008)
                .translucent()
                .useLightmap()
                .build()
        );

        BBSPlusPlusMod.LOGGER.info("[Particle+] Additive 粒子管线已就绪");

        return layer;
    }
}
