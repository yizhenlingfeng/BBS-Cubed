package gbeic.bbsplusplus.client.outline;

import com.mojang.blaze3d.systems.RenderSystem;
import gbeic.bbsplusplus.client.outline.geometry.OutlineAlphaMask;
import gbeic.bbsplusplus.client.outline.geometry.OutlineDecalFilter;
import gbeic.bbsplusplus.client.outline.geometry.OutlineGeometry;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.resources.Pixels;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 1.20.x immediate-mode backend for the outline shell.
 *
 * <p>Two passes are drawn around the model's own render: a colour pass at HEAD with depth writes
 * disabled (so the body covers the near half of the shell), and a depth-only pass at RETURN with
 * colour writes disabled (so the surviving rim pixels write real geometric depth and Iris's
 * deferred/composite stages do not classify them as sky).</p>
 */
public final class OutlineRenderer120
{
    private OutlineRenderer120()
    {
    }

    /**
     * Draws the complete rim at HEAD. Specs with a resolved LabPBR texture use the entity
     * shader; every other spec uses the solid position-colour path. Each spec is submitted once.
     */
    public static void render(MatrixStack stack, Model model, ModelConfig config, OutlineConfigHolder outline,
                              Color color, Link textureLink, Predicate<String> materialVisible)
    {
        if (!canRender(model, config, outline, color))
        {
            return;
        }

        Set<ModelCube> decals = OutlineDecalFilter.find(model, outline.bbspp$getOutlineDecalSize());
        List<OutlineGeometry.Spec> specs = OutlineGeometry.collect(model, config, outline, decals);
        Map<ModelCube, float[][]> faceRects = buildAlphaMask(model, textureLink);
        Matrix4f base = new Matrix4f(stack.peek().getPositionMatrix());
        Map<Long, ResourceTexture> glowTextures = resolveGlowTextures(specs);
        List<OutlineGeometry.Spec> solid = new ArrayList<>(specs.size());
        List<OutlineGeometry.Spec> glow = new ArrayList<>(specs.size());

        for (OutlineGeometry.Spec spec : specs)
        {
            (glowTextures.containsKey(specKey(spec)) ? glow : solid).add(spec);
        }

        drawSolid(model, config, solid, base, color.a, faceRects, decals, materialVisible);

        if (!glow.isEmpty()
            && (!FabricLoader.getInstance().isModLoaded("iris")
                || !renderLabPBR(model, config, glow, base, color.a, faceRects, decals, materialVisible)))
        {
            drawSolid(model, config, glow, base, color.a, faceRects, decals, materialVisible);
        }
    }

    /** Solid position-colour rim, with depth writes disabled so the body can cover it. */
    private static void drawSolid(Model model, ModelConfig config, List<OutlineGeometry.Spec> specs, Matrix4f base,
                                  float formAlpha, Map<ModelCube, float[][]> faceRects, Set<ModelCube> decals,
                                  Predicate<String> materialVisible)
    {
        if (specs.isEmpty())
        {
            return;
        }

        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        draw(model, config, specs, base, formAlpha, faceRects, decals, materialVisible);

        RenderSystem.depthMask(depthMask);

        if (!depthTest)
        {
            RenderSystem.disableDepthTest();
        }

        if (!blend)
        {
            RenderSystem.disableBlend();
        }

        if (!cull)
        {
            RenderSystem.disableCull();
        }
    }

    /** One LabPBR texture/draw per distinct (argb, glow) pair. */
    private record GlowBatch(int argb, int glow, List<OutlineGeometry.Spec> specs)
    {
    }

    private static long specKey(OutlineGeometry.Spec spec)
    {
        int alpha = (spec.argb() >>> 24) & 0xFF;
        int glow = alpha < 26 ? 0 : Math.round(Math.max(0F, Math.min(254F, spec.glow())));

        return ((long) spec.argb() << 32) | (glow & 0xFFFFFFFFL);
    }

    /**
     * Resolves one generated LabPBR texture per distinct glow group. Cool_pack only takes the
     * emissive path for specs with a usable diffuse; every other spec stays on the solid
     * {@code position_color} path instead of receiving a second glow pass.
     */
    private static Map<Long, ResourceTexture> resolveGlowTextures(List<OutlineGeometry.Spec> specs)
    {
        if (!FabricLoader.getInstance().isModLoaded("iris"))
        {
            return Map.of();
        }

        Map<Long, ResourceTexture> textures = new LinkedHashMap<>();

        for (OutlineGeometry.Spec spec : specs)
        {
            if (spec.a() <= 0F)
            {
                continue;
            }

            int alpha = (spec.argb() >>> 24) & 0xFF;
            int glow = Math.round(Math.max(0F, Math.min(254F, spec.glow())));

            if (alpha < 26 || glow <= 0)
            {
                continue;
            }

            long key = specKey(spec);

            if (textures.containsKey(key))
            {
                continue;
            }

            ResourceTexture texture = OutlineEmissiveTexture.getDiffuseTexture(spec.argb(), glow);

            if (texture != null)
            {
                textures.put(key, texture);
            }
        }

        return textures;
    }

    /**
     * Draws the rim with a real entity-translucent shader and a generated 1x1
     * diffuse. Iris sees the ResourceTexture, resolves its {@code _s}/{@code _n}
     * companions through the resource manager and routes the draw through its
     * entity pipeline, where LabPBR emission is applied.
     *
     * @return true when the LabPBR path handled every glow spec; false means the caller should
     *         draw the same specs through the solid position-colour path.
     */
    private static boolean renderLabPBR(Model model, ModelConfig config, List<OutlineGeometry.Spec> specs,
                                        Matrix4f base, float formAlpha, Map<ModelCube, float[][]> faceRects,
                                        Set<ModelCube> decals, Predicate<String> materialVisible)
    {
        if (formAlpha <= 0F)
        {
            return true;
        }

        Map<Long, GlowBatch> batches = new LinkedHashMap<>();

        for (OutlineGeometry.Spec spec : specs)
        {
            int alpha = (spec.argb() >>> 24) & 0xFF;
            int glow = Math.round(Math.max(0F, Math.min(254F, spec.glow())));

            if (alpha < 26 || glow <= 0 || spec.a() * formAlpha <= 0F)
            {
                continue;
            }

            long key = ((long) spec.argb() << 32) | (glow & 0xFFFFFFFFL);
            GlowBatch batch = batches.get(key);

            if (batch == null)
            {
                batch = new GlowBatch(spec.argb(), glow, new ArrayList<>());
                batches.put(key, batch);
            }

            batch.specs().add(spec);
        }

        if (batches.isEmpty())
        {
            return true;
        }

        List<GlowBatch> ordered = new ArrayList<>(batches.values());
        List<ResourceTexture> textures = new ArrayList<>(ordered.size());

        for (GlowBatch batch : ordered)
        {
            ResourceTexture texture = OutlineEmissiveTexture.getDiffuseTexture(batch.argb(), batch.glow());

            if (texture == null)
            {
                return false;
            }

            textures.add(texture);
        }

        int sampler0 = RenderSystem.getShaderTexture(0);
        int sampler1 = RenderSystem.getShaderTexture(1);
        int sampler2 = RenderSystem.getShaderTexture(2);
        float[] shaderColor = RenderSystem.getShaderColor().clone();
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;
        boolean ok = true;

        try
        {
            RenderSystem.setShader(GameRenderer::getRenderTypeEntityTranslucentProgram);
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.enableCull();
            RenderSystem.setShaderFogStart(1_000_000F);
            RenderSystem.setShaderFogEnd(1_000_001F);
            gameRenderer.getLightmapTextureManager().enable();
            gameRenderer.getOverlayTexture().setupOverlayColor();

            for (int i = 0; i < ordered.size(); i++)
            {
                RenderSystem.setShaderTexture(0, textures.get(i).getGlId());
                BufferBuilder builder = Tessellator.getInstance().getBuffer();
                int[] count = { 0 };

                builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);

                OutlineGeometry.emit(model, config, ordered.get(i).specs(), base, formAlpha,
                    (x, y, z, nx, ny, nz, r, g, b, a) ->
                    {
                        builder.vertex(x, y, z)
                            .color(1F, 1F, 1F, formAlpha)
                            .texture(0F, 0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                            .normal(nx, ny, nz)
                            .next();
                        count[0]++;
                    }, faceRects, decals, materialVisible);

                BufferBuilder.BuiltBuffer built = builder.end();

                if (count[0] > 0)
                {
                    BufferRenderer.drawWithGlobalProgram(built);
                }
            }
        }
        catch (Throwable ignored)
        {
            ok = false;
        }
        finally
        {
            gameRenderer.getOverlayTexture().teardownOverlayColor();
            gameRenderer.getLightmapTextureManager().disable();
            RenderSystem.setShaderTexture(0, sampler0);
            RenderSystem.setShaderTexture(1, sampler1);
            RenderSystem.setShaderTexture(2, sampler2);
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(depthMask);
            RenderSystem.depthFunc(depthFunc);

            if (!depthTest)
            {
                RenderSystem.disableDepthTest();
            }

            if (!blend)
            {
                RenderSystem.disableBlend();
            }

            if (!cull)
            {
                RenderSystem.disableCull();
            }
        }

        return ok;
    }

    /** Writes depth for rim pixels that survived the body. Called at {@code ModelInstance.render} RETURN. */
    public static void renderDepth(MatrixStack stack, Model model, ModelConfig config, OutlineConfigHolder outline,
                                   Color color, Link textureLink, Predicate<String> materialVisible)
    {
        if (!canRender(model, config, outline, color))
        {
            return;
        }

        Set<ModelCube> decals = OutlineDecalFilter.find(model, outline.bbspp$getOutlineDecalSize());
        List<OutlineGeometry.Spec> specs = OutlineGeometry.collect(model, config, outline, decals);
        Map<ModelCube, float[][]> faceRects = buildAlphaMask(model, textureLink);
        Matrix4f base = new Matrix4f(stack.peek().getPositionMatrix());

        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);

        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.enableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        draw(model, config, specs, base, color.a, faceRects, decals, materialVisible);

        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(depthMask);
        RenderSystem.depthFunc(depthFunc);

        if (blend)
        {
            RenderSystem.enableBlend();
        }

        if (!cull)
        {
            RenderSystem.disableCull();
        }
    }

    private static boolean canRender(Model model, ModelConfig config, OutlineConfigHolder outline, Color color)
    {
        return model != null && config != null && outline != null && color != null
            && outline.bbspp$isOutlineEnabled()
            && !BBSRendering.isIrisShadowPass();
    }

    private static Map<ModelCube, float[][]> buildAlphaMask(Model model, Link textureLink)
    {
        if (textureLink == null)
        {
            return null;
        }

        Texture texture = BBSModClient.getTextures().getTexture(textureLink);

        if (texture == null || texture == BBSModClient.getTextures().getError())
        {
            return null;
        }

        Pixels pixels = OutlineAlphaMask.pixels(texture);

        return pixels == null ? null : OutlineAlphaMask.build(pixels, texture, model.getAllGroups());
    }

    private static void draw(Model model, ModelConfig config, List<OutlineGeometry.Spec> specs, Matrix4f base,
                             float formAlpha, Map<ModelCube, float[][]> faceRects, Set<ModelCube> decals,
                             Predicate<String> materialVisible)
    {
        if (specs.isEmpty())
        {
            return;
        }

        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        int[] count = { 0 };

        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        OutlineGeometry.emit(model, config, specs, base, formAlpha, (x, y, z, nx, ny, nz, r, g, b, a) ->
        {
            builder.vertex(x, y, z).color(r, g, b, a).next();
            count[0]++;
        }, faceRects, decals, materialVisible);

        BufferBuilder.BuiltBuffer built = builder.end();

        if (count[0] > 0)
        {
            BufferRenderer.drawWithGlobalProgram(built);
        }
    }
}
