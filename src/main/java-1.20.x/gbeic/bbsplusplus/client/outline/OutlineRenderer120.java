package gbeic.bbsplusplus.client.outline;

import com.mojang.blaze3d.systems.RenderSystem;
import gbeic.bbsplusplus.client.outline.geometry.OutlineAlphaMask;
import gbeic.bbsplusplus.client.outline.geometry.OutlineDecalFilter;
import gbeic.bbsplusplus.client.outline.geometry.OutlineGeometry;
import gbeic.bbsplusplus.client.outline.geometry.OutlineShellKey;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.resources.Pixels;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 1.20.x backend for the outline shell.
 *
 * <p>Two passes are drawn around the model's own render: a colour pass at HEAD with depth writes
 * disabled (so the body covers the near half of the shell), and a depth-only pass at RETURN with
 * colour writes disabled (so the surviving rim pixels write real geometric depth and Iris's
 * deferred/composite stages do not classify them as sky).</p>
 *
 * <p>The solid colour and depth halves of both passes are now baked once into
 * {@link VertexBuffer}s in model-root space (see {@link OutlineShellCache}); a cache hit costs
 * two draws and no per-cube work at all. The two additive layers on top of the shell - LabPBR
 * emission and the vanilla full-bright glow - are drawn separately and never change the baked
 * shell: the emission layer rebuilds its vertices once per frame because the vanilla entity
 * shader consumes world-space normals, which cannot be shared across poses, while the vanilla
 * layer reuses the same geometry walk at its own strength.</p>
 */
public final class OutlineRenderer120
{
    private static final Matrix4f IDENTITY = new Matrix4f();

    /**
     * Widest shell first: the shell writes no depth, so a narrow shell drawn after a wide one
     * would be hidden wherever both cover the same pixel.
     */
    private static final java.util.Comparator<OutlineGeometry.Spec> WIDE_FIRST =
        java.util.Comparator.comparingDouble(OutlineGeometry.Spec::width).reversed();

    /** HEAD/RETURN pairing for the cached path. Nested renders pair like a stack. */
    private record Prepared(Model model, ModelConfig config, OutlineShellCache.Entry entry, int generation, boolean skipDepth)
    {
    }

    private static final ArrayDeque<Prepared> PREPARED = new ArrayDeque<>();

    private OutlineRenderer120()
    {
    }

    /**
     * Draws the rim at HEAD: the plain shell first, then the additive LabPBR emission and
     * vanilla full-bright layers on top of it. Every outlined spec is part of the shell, so
     * turning either layer on never changes the silhouette.
     */
    public static void render(MatrixStack stack, Model model, ModelConfig config,
                              Color color, Link textureLink, Predicate<String> materialVisible)
    {
        if (!canRender(model, config, color))
        {
            return;
        }

        if (!OutlineGeometry.hasAnyOutline(model))
        {
            /* Keep HEAD/RETURN paired; the RETURN side falls back to an (empty) immediate pass. */
            pushPrepared(model, config, null, true);

            return;
        }

        if (color.a <= 0F)
        {
            /* Still pair HEAD/RETURN, but do not bake or rebuild anything for a fully faded form. */
            pushPrepared(model, config, null, true);

            return;
        }

        OutlineShellKey.Key key = OutlineShellKey.of(model, config, textureLink, materialVisible);
        OutlineShellCache.Entry entry = OutlineShellCache.getOrBuild(model, key,
            cached -> buildEntry(cached, key, model, config, textureLink, materialVisible));

        pushPrepared(model, config, entry);

        if (entry == null)
        {
            renderImmediate(stack, model, config, color, textureLink, materialVisible);

            return;
        }

        drawColor(entry, model, config, new Matrix4f(stack.peek().getPositionMatrix()), color.a, materialVisible);
    }

    private static void pushPrepared(Model model, ModelConfig config, OutlineShellCache.Entry entry)
    {
        pushPrepared(model, config, entry, false);
    }

    private static void pushPrepared(Model model, ModelConfig config, OutlineShellCache.Entry entry, boolean skipDepth)
    {
        while (PREPARED.size() > 32)
        {
            PREPARED.pollLast();
        }

        PREPARED.push(new Prepared(model, config, entry, entry == null ? 0 : entry.generation, skipDepth));
    }

    private static void buildEntry(OutlineShellCache.Entry entry, OutlineShellKey.Key key, Model model,
                                   ModelConfig config, Link textureLink, Predicate<String> materialVisible)
    {
        if (entry.geometryHash != 0L && entry.geometryHash != key.geometryHash())
        {
            /* Cube positions/UVs changed while the game is running: the per-cube mask cache is
             * keyed by cube identity, so it has to be dropped before it is queried again. */
            OutlineAlphaMask.invalidateMasks();
        }

        entry.geometryHash = key.geometryHash();

        Map<String, Float> decalSizes = OutlineGeometry.decalSizes(model);
        Set<ModelCube> decals = OutlineDecalFilter.find(model, decalSizes);
        List<OutlineGeometry.Spec> specs = OutlineGeometry.collect(model);

        specs.sort(WIDE_FIRST);

        Map<ModelCube, float[][]> faceRects = buildAlphaMask(model, textureLink);
        Map<Long, ResourceTexture> glowTextures = resolveGlowTextures(specs);
        List<OutlineGeometry.Spec> glow = new ArrayList<>(specs.size());
        List<OutlineGeometry.Spec> vanilla = new ArrayList<>(specs.size());
        List<OutlineGeometry.Spec> main = new ArrayList<>(specs.size());
        boolean overlay = false;

        for (OutlineGeometry.Spec spec : specs)
        {
            if (hasGlowTexture(spec, glowTextures))
            {
                glow.add(spec);
            }

            if (spec.vanillaGlow() > 0F)
            {
                vanilla.add(spec);
            }

            if (spec.layer() == 0)
            {
                main.add(spec);
            }
            else
            {
                overlay = true;
            }
        }

        entry.glowSpecs = glow;
        entry.vanillaSpecs = vanilla;
        entry.mainSpecs = main;
        entry.hasOverlayLayer = overlay;
        entry.faceRects = faceRects;
        entry.decals = decals;

        /* The plain shell always holds every spec: emission and the vanilla full-bright layer are
         * additive on top, so turning either on never changes the silhouette or the base colour. */
        Uploaded solidBuild = upload(entry.solidVbo, model, config, specs, faceRects, decals, materialVisible);
        entry.solidVbo = solidBuild.vbo;
        entry.solidVertices = solidBuild.vertices;

        entry.valid = true;
    }

    private record Uploaded(VertexBuffer vbo, int vertices)
    {
    }

    /**
     * Bakes one spec list into a {@code POSITION_COLOR} buffer. The bake uses the model-root
     * frame (identity base) and the spec's own alpha; the form opacity is applied at draw time
     * through {@code ColorModulator}.
     */
    private static Uploaded upload(VertexBuffer existing, Model model, ModelConfig config,
                                   List<OutlineGeometry.Spec> specs, Map<ModelCube, float[][]> faceRects,
                                   Set<ModelCube> decals, Predicate<String> materialVisible)
    {
        if (specs == null || specs.isEmpty())
        {
            return new Uploaded(existing, 0);
        }

        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        boolean[] began = { false };
        int[] count = { 0 };

        try
        {
            OutlineGeometry.emit(model, config, specs, IDENTITY, 1F, (x, y, z, nx, ny, nz, r, g, b, a) ->
            {
                if (!began[0])
                {
                    builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
                    began[0] = true;
                }

                count[0]++;
                builder.vertex(x, y, z).color(r, g, b, a).next();
            }, faceRects, decals, materialVisible, false);
        }
        catch (Throwable throwable)
        {
            if (began[0])
            {
                try
                {
                    builder.end();
                }
                catch (Throwable ignored)
                {
                }
            }

            throw throwable;
        }

        if (!began[0])
        {
            return new Uploaded(existing, 0);
        }

        BufferBuilder.BuiltBuffer built = builder.end();
        VertexBuffer vbo = existing == null ? new VertexBuffer(VertexBuffer.Usage.DYNAMIC) : existing;

        vbo.bind();
        vbo.upload(built);
        VertexBuffer.unbind();

        return new Uploaded(vbo, count[0]);
    }

    private static void drawColor(OutlineShellCache.Entry entry, Model model, ModelConfig config, Matrix4f base,
                                  float formAlpha, Predicate<String> materialVisible)
    {
        if (entry.solidVertices <= 0)
        {
            return;
        }

        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        float[] shaderColor = RenderSystem.getShaderColor().clone();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.setShaderColor(1F, 1F, 1F, formAlpha * shaderColor[3]);

        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(base);
        ShaderProgram shader = RenderSystem.getShader();

        if (entry.solidVertices > 0)
        {
            drawVbo(entry.solidVbo, modelView, shader);
        }

        if (entry.hasOverlayLayer && !entry.mainSpecs.isEmpty())
        {
            /* The overlay shell is the wider one, so its glow would paint straight over the parent
             * rim. Run the wide layer's glow first, repaint the narrow layer, then run the narrow
             * layer's glow, so the parent rim always ends up on top. */
            drawGlowPasses(entry.glowSpecs, entry.vanillaSpecs, 1, model, config, base, formAlpha,
                entry.faceRects, entry.decals, materialVisible, shaderColor);

            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            RenderSystem.setShaderColor(1F, 1F, 1F, formAlpha * shaderColor[3]);
            draw(model, config, entry.mainSpecs, base, formAlpha, entry.faceRects, entry.decals, materialVisible);

            drawGlowPasses(entry.glowSpecs, entry.vanillaSpecs, 0, model, config, base, formAlpha,
                entry.faceRects, entry.decals, materialVisible, shaderColor);
        }
        else
        {
            drawGlowPasses(entry.glowSpecs, entry.vanillaSpecs, -1, model, config, base, formAlpha,
                entry.faceRects, entry.decals, materialVisible, shaderColor);
        }

        RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
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

    /**
     * Runs the additive passes - LabPBR emission and the vanilla full-bright layer - for one
     * outline layer; {@code layer < 0} means every layer, which is all models using only the main
     * outline need. Splitting by layer is what lets {@link #drawColor} keep the narrow parent rim
     * above the wide overlay's glow.
     */
    private static void drawGlowPasses(List<OutlineGeometry.Spec> glowSpecs, List<OutlineGeometry.Spec> vanillaSpecs,
                                       int layer, Model model, ModelConfig config, Matrix4f base, float formAlpha,
                                       Map<ModelCube, float[][]> faceRects, Set<ModelCube> decals,
                                       Predicate<String> materialVisible, float[] shaderColor)
    {
        List<OutlineGeometry.Spec> glow = layer < 0 ? glowSpecs : filterLayer(glowSpecs, layer);
        List<OutlineGeometry.Spec> vanilla = layer < 0 ? vanillaSpecs : filterLayer(vanillaSpecs, layer);

        if (!glow.isEmpty() && BBSRendering.isIrisShadersEnabled())
        {
            /* LabPBR emission only exists inside a shader pack; without one this pass would just
             * repaint the shell at full brightness, which is what the vanilla glow switch is for. */
            renderLabPBR(model, config, glow, base, formAlpha, faceRects, decals, materialVisible);
        }

        if (!vanilla.isEmpty())
        {
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            renderVanilla(model, config, vanilla, base, formAlpha, faceRects, decals, materialVisible);
        }
    }

    /** Returns the specs of one layer, keeping the wide-first order they were sorted into. */
    private static List<OutlineGeometry.Spec> filterLayer(List<OutlineGeometry.Spec> specs, int layer)
    {
        List<OutlineGeometry.Spec> matches = new ArrayList<>(specs.size());

        for (OutlineGeometry.Spec spec : specs)
        {
            if (spec.layer() == layer)
            {
                matches.add(spec);
            }
        }

        return matches;
    }

    private static void drawVbo(VertexBuffer vbo, Matrix4f modelView, ShaderProgram shader)
    {
        vbo.bind();
        vbo.draw(modelView, RenderSystem.getProjectionMatrix(), shader);
        VertexBuffer.unbind();
    }

    /** Writes depth for rim pixels that survived the body. Called at {@code ModelInstance.render} RETURN. */
    public static void renderDepth(MatrixStack stack, Model model, ModelConfig config,
                                   Color color, Link textureLink, Predicate<String> materialVisible)
    {
        if (!canRender(model, config, color))
        {
            return;
        }

        Prepared prepared = PREPARED.poll();

        if (color.a <= 0F)
        {
            return;
        }

        if (prepared == null || prepared.model != model || prepared.config != config)
        {
            if (prepared != null)
            {
                PREPARED.clear();
            }

            renderDepthImmediate(stack, model, config, color, textureLink, materialVisible);

            return;
        }

        if (prepared.skipDepth())
        {
            return;
        }

        if (prepared.entry == null || !prepared.entry.valid || prepared.entry.generation != prepared.generation)
        {
            renderDepthImmediate(stack, model, config, color, textureLink, materialVisible);

            return;
        }

        drawDepth(prepared.entry, new Matrix4f(stack.peek().getPositionMatrix()));
    }

    private static void drawDepth(OutlineShellCache.Entry entry, Matrix4f base)
    {
        if (entry.solidVertices <= 0)
        {
            return;
        }

        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        float[] shaderColor = RenderSystem.getShaderColor().clone();

        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.enableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(base);
        ShaderProgram shader = RenderSystem.getShader();

        /* The solid buffer carries every outlined spec, so it already covers the emission and
         * full-bright layers; depth only has to be written once. */
        drawVbo(entry.solidVbo, modelView, shader);

        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(depthMask);
        RenderSystem.depthFunc(depthFunc);
        RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);

        if (blend)
        {
            RenderSystem.enableBlend();
        }

        if (!cull)
        {
            RenderSystem.disableCull();
        }
    }

    /** Fallback per-frame path used when the cached shell cannot be built. */
    private static void renderImmediate(MatrixStack stack, Model model, ModelConfig config,
                                        Color color, Link textureLink, Predicate<String> materialVisible)
    {
        Map<String, Float> decalSizes = OutlineGeometry.decalSizes(model);
        Set<ModelCube> decals = OutlineDecalFilter.find(model, decalSizes);
        List<OutlineGeometry.Spec> specs = OutlineGeometry.collect(model);

        specs.sort(WIDE_FIRST);

        Map<ModelCube, float[][]> faceRects = buildAlphaMask(model, textureLink);
        Matrix4f base = new Matrix4f(stack.peek().getPositionMatrix());
        Map<Long, ResourceTexture> glowTextures = resolveGlowTextures(specs);
        List<OutlineGeometry.Spec> glow = new ArrayList<>(specs.size());
        List<OutlineGeometry.Spec> vanilla = new ArrayList<>(specs.size());
        List<OutlineGeometry.Spec> main = new ArrayList<>(specs.size());
        boolean overlay = false;

        for (OutlineGeometry.Spec spec : specs)
        {
            if (hasGlowTexture(spec, glowTextures))
            {
                glow.add(spec);
            }

            if (spec.vanillaGlow() > 0F)
            {
                vanilla.add(spec);
            }

            if (spec.layer() == 0)
            {
                main.add(spec);
            }
            else
            {
                overlay = true;
            }
        }

        drawSolid(model, config, specs, base, color.a, faceRects, decals, materialVisible);

        float[] shaderColor = RenderSystem.getShaderColor().clone();

        if (overlay && !main.isEmpty())
        {
            /* Same layering rule as the cached path: the wide overlay's glow must not paint over
             * the parent rim, so the narrow layer is repainted between the two glow passes. */
            drawGlowPasses(glow, vanilla, 1, model, config, base, color.a, faceRects, decals, materialVisible, shaderColor);

            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            RenderSystem.setShaderColor(1F, 1F, 1F, color.a * shaderColor[3]);
            draw(model, config, main, base, color.a, faceRects, decals, materialVisible);

            drawGlowPasses(glow, vanilla, 0, model, config, base, color.a, faceRects, decals, materialVisible, shaderColor);
        }
        else
        {
            drawGlowPasses(glow, vanilla, -1, model, config, base, color.a, faceRects, decals, materialVisible, shaderColor);
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

    /**
     * Maps the emission slider to the LabPBR alpha byte: {@code 0} means "no emission", anything
     * above it maps linearly onto {@code 1..254}. 255 is skipped because LabPBR reserves it for
     * "this pixel does not emit", which is what a plain RGB texture without alpha reads as.
     */
    private static int glowAlpha(float glow)
    {
        if (glow <= 0F)
        {
            return 0;
        }

        return (int) Math.max(1L, Math.min(254L, Math.round(glow)));
    }

    /**
     * Whether at least one of the spec's face colours has a companion emissive texture.
     *
     * <p>Checking the layer colour alone would drop a bone whose six faces are all customised,
     * because the layer colour itself is then not painted anywhere.</p>
     */
    private static boolean hasGlowTexture(OutlineGeometry.Spec spec,
                                          Map<Long, ResourceTexture> textures)
    {
        if (textures.isEmpty() || spec.a() <= 0F)
        {
            return false;
        }

        int glow = glowAlpha(spec.glow());

        if (glow <= 0)
        {
            return false;
        }

        for (int color : OutlineGeometry.effectiveFaceColors(spec))
        {
            if (textures.containsKey(glowKey(color, glow)))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Resolves one generated LabPBR texture per (face colour, glow) pair. Walked per face colour,
     * not just per layer colour, because the emission pass draws one batch per colour and each of
     * those batches needs its own 1x1 diffuse. A spec whose companion texture cannot be created
     * only loses its emission, never its rim.
     */
    private static Map<Long, ResourceTexture> resolveGlowTextures(List<OutlineGeometry.Spec> specs)
    {
        if (!BBSRendering.isIrisShadersEnabled())
        {
            /* Without an enabled shader pack nobody samples the companion texture, so there is
             * no point in allocating one PNG-backed texture per (colour, glow) pair. */
            return Map.of();
        }

        Map<Long, ResourceTexture> textures = new LinkedHashMap<>();

        for (OutlineGeometry.Spec spec : specs)
        {
            if (spec.a() <= 0F)
            {
                continue;
            }

            int glow = glowAlpha(spec.glow());

            if (glow <= 0)
            {
                continue;
            }

            for (int color : OutlineGeometry.effectiveFaceColors(spec))
            {
                long key = glowKey(color, glow);

                if (textures.containsKey(key))
                {
                    continue;
                }

                ResourceTexture texture = OutlineEmissiveTexture.getDiffuseTexture(color, glow);

                if (texture != null)
                {
                    textures.put(key, texture);
                }
            }
        }

        return textures;
    }

    private static long glowKey(int argb, int glow)
    {
        return ((long) argb << 32) | (glow & 0xFFFFFFFFL);
    }

    /**
     * Draws the rim with a real entity-translucent shader and a generated 1x1 diffuse. Iris sees
     * the ResourceTexture, resolves its {@code _s}/{@code _n} companions through the resource
     * manager and routes the draw through its entity pipeline, where LabPBR emission is applied.
     *
     * <p>When a companion texture cannot be created the layer is simply skipped: the plain shell
     * already covers those specs, so the result is a rim without emission rather than no rim.</p>
     */
    private static void renderLabPBR(Model model, ModelConfig config, List<OutlineGeometry.Spec> specs,
                                     Matrix4f base, float formAlpha, Map<ModelCube, float[][]> faceRects,
                                     Set<ModelCube> decals, Predicate<String> materialVisible)
    {
        if (formAlpha <= 0F)
        {
            return;
        }

        Map<Long, GlowBatch> batches = new LinkedHashMap<>();

        for (OutlineGeometry.Spec spec : specs)
        {
            int glow = glowAlpha(spec.glow());

            /* The layer-wide alpha is not used here: every face carries its own after
             * {@code pickFace}, so a transparent layer with one opaque custom face must still
             * reach the vertex stage. */
            if (glow <= 0 || formAlpha <= 0F)
            {
                continue;
            }

            /* One batch per face colour: each colour needs its own 1x1 emissive diffuse, so a
             * single batch keyed on the layer colour would flatten every face to the layer tint. */
            for (int color : OutlineGeometry.effectiveFaceColors(spec))
            {
                long key = glowKey(color, glow);
                GlowBatch batch = batches.get(key);

                if (batch == null)
                {
                    batch = new GlowBatch(color, glow, new ArrayList<>());
                    batches.put(key, batch);
                }

                batch.specs().add(spec);
            }
        }

        if (batches.isEmpty())
        {
            return;
        }

        List<GlowBatch> ordered = new ArrayList<>(batches.size());
        List<ResourceTexture> textures = new ArrayList<>(batches.size());

        for (GlowBatch batch : batches.values())
        {
            ResourceTexture texture = OutlineEmissiveTexture.getDiffuseTexture(batch.argb(), batch.glow());

            /* Drop the batches that got no companion texture instead of giving up on the whole
             * layer: the other colours still have a valid diffuse and should keep glowing. */
            if (texture == null)
            {
                continue;
            }

            ordered.add(batch);
            textures.add(texture);
        }

        if (ordered.isEmpty())
        {
            return;
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
                boolean[] began = { false };

                OutlineGeometry.emit(model, config, ordered.get(i).specs(), base, formAlpha,
                    (x, y, z, nx, ny, nz, r, g, b, a) ->
                    {
                        if (!began[0])
                        {
                            builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
                            began[0] = true;
                        }

                        builder.vertex(x, y, z)
                            .color(1F, 1F, 1F, a)
                            .texture(0F, 0F)
                            .overlay(OverlayTexture.DEFAULT_UV)
                            .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                            .normal(nx, ny, nz)
                            .next();
                    }, faceRects, decals, materialVisible, true, ordered.get(i).argb());

                if (!began[0])
                {
                    continue;
                }

                BufferRenderer.drawWithGlobalProgram(builder.end());
            }
        }
        catch (Throwable ignored)
        {
            /* A broken companion texture or a shader mismatch only costs the emission layer; the
             * shell has already been drawn, so the rim stays intact. */
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
    }

    /**
     * The vanilla full-bright overlay. It is a plain unlit {@code position_color} pass with
     * additive blending, so it works without Iris and without a shader pack that declares PBR
     * samplers. The trade-off is that a shader pack which replaces the entity shader usually
     * washes the layer out, which is what the tooltip warns about.
     */
    private static void renderVanilla(Model model, ModelConfig config, List<OutlineGeometry.Spec> specs,
                                      Matrix4f base, float formAlpha, Map<ModelCube, float[][]> faceRects,
                                      Set<ModelCube> decals, Predicate<String> materialVisible)
    {
        if (formAlpha <= 0F)
        {
            return;
        }

        /* One draw per distinct strength: the strength rides on the shader colour, so two specs
         * that only differ in vanilla glow cannot share a batch. */
        Map<Integer, List<OutlineGeometry.Spec>> groups = new LinkedHashMap<>();

        for (OutlineGeometry.Spec spec : specs)
        {
            int strength = Math.round(Math.max(0F, Math.min(255F, spec.vanillaGlow())));

            if (strength <= 0 || spec.a() * formAlpha <= 0F)
            {
                continue;
            }

            groups.computeIfAbsent(strength, key -> new ArrayList<>()).add(spec);
        }

        if (groups.isEmpty())
        {
            return;
        }

        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        float[] shaderColor = RenderSystem.getShaderColor().clone();
        /* The caller restored the incoming shader colour before calling in, so the form fade has
         * to be applied here; the strength then only scales that one product. */
        float baseAlpha = formAlpha * shaderColor[3];

        try
        {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            RenderSystem.enableBlend();
            /* Additive, so the layer brightens what the plain shell already put down instead of
             * re-blending the same colour. The strength still scales the alpha, so a value between
             * the two switch positions would render as a partial overlay. */
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.enableCull();

            for (Map.Entry<Integer, List<OutlineGeometry.Spec>> group : groups.entrySet())
            {
                float strength = group.getKey() / 255F;

                RenderSystem.setShaderColor(1F, 1F, 1F, baseAlpha * strength);
                draw(model, config, group.getValue(), base, 1F, faceRects, decals, materialVisible);
            }
        }
        finally
        {
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(depthMask);
            RenderSystem.depthFunc(depthFunc);
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);

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
    }

    /** Fallback per-frame depth path used when the cached shell cannot be built. */
    private static void renderDepthImmediate(MatrixStack stack, Model model, ModelConfig config,
                                             Color color, Link textureLink,
                                             Predicate<String> materialVisible)
    {
        Map<String, Float> decalSizes = OutlineGeometry.decalSizes(model);
        Set<ModelCube> decals = OutlineDecalFilter.find(model, decalSizes);
        List<OutlineGeometry.Spec> specs = OutlineGeometry.collect(model);

        specs.sort(WIDE_FIRST);

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

    private static boolean canRender(Model model, ModelConfig config, Color color)
    {
        return model != null && config != null && color != null
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
        boolean[] began = { false };

        OutlineGeometry.emit(model, config, specs, base, formAlpha, (x, y, z, nx, ny, nz, r, g, b, a) ->
        {
            if (!began[0])
            {
                builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
                began[0] = true;
            }

            builder.vertex(x, y, z).color(r, g, b, a).next();
        }, faceRects, decals, materialVisible, false);

        if (!began[0])
        {
            return;
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());
    }
}
