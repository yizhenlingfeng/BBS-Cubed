package gbeic.bbsplusplus.client.outline;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.VertexFormat;
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
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 1.21.11 RenderPipeline backend for the outline shell.
 *
 * <p>1.21.5 removed the immediate {@code RenderSystem.setShader} / {@code BufferRenderer} path.
 * The same position-colour geometry is now built into a BufferBuilder and submitted through three
 * immutable render layers: colour (depth-tested, no depth write), the additive vanilla glow
 * overlay and depth-only. LabPBR emission still falls back to the solid colour path until the
 * 1.21.11 LabPBR entity layer is ported.</p>
 *
 * <p>This backend has no reusable vertex buffer, so it caches the baked {@code x,y,z,r,g,b,a}
 * floats instead. Replaying a hit walks the array once and skips the matrix tree, weld sets,
 * alpha-mask lookups and bilinear sub-quads; the caller's stack is part of the cache key, so a
 * static model reuses its bake across frames and a moving one still builds only once per frame.</p>
 *
 * <p>The vanilla position-colour snippet is private in this mapping, so the project access
 * widener exposes it as a compile-time constant. This is production-safe: the field name is
 * remapped with the rest of the class instead of being looked up by a literal name at runtime.</p>
 */
public final class OutlineRenderer121
{
    private static final RenderPipeline.Snippet POSITION_COLOR_SNIPPET = RenderPipelines.POSITION_COLOR_SNIPPET;

    /**
     * Widest shell first: the shell writes no depth, so a narrow shell drawn after a wide one
     * would be hidden wherever both cover the same pixel.
     */
    private static final java.util.Comparator<OutlineGeometry.Spec> WIDE_FIRST =
        java.util.Comparator.comparingDouble(OutlineGeometry.Spec::width).reversed();

    private static final RenderPipeline COLOR_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("bbsplusplus", "pipeline/outline_color"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(true)
            .build()
    );

    private static final RenderPipeline DEPTH_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("bbsplusplus", "pipeline/outline_depth"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withoutBlend()
            .withColorWrite(false, false)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(true)
            .withCull(true)
            .build()
    );

    /**
     * Additive {@code SRC_ALPHA, ONE}, so the overlay brightens the shell that was already drawn
     * instead of blending the same colour twice. This matches the 1.20.x backend.
     *
     * <p>{@link BlendFunction#ADDITIVE} is {@code ONE, ONE} and would ignore the alpha the
     * strength is baked into, collapsing the overlay into a flat full-bright pass. Hence the
     * explicit {@code SRC_ALPHA} source factor here.</p>
     */
    private static final RenderPipeline VANILLA_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("bbsplusplus", "pipeline/outline_vanilla_glow"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE))
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(true)
            .build()
    );

    private static final RenderLayer COLOR_LAYER = RenderLayer.of("bbsplusplus_outline_color",
        RenderSetup.builder(COLOR_PIPELINE).translucent().build());
    private static final RenderLayer DEPTH_LAYER = RenderLayer.of("bbsplusplus_outline_depth",
        RenderSetup.builder(DEPTH_PIPELINE).build());
    private static final RenderLayer VANILLA_LAYER = RenderLayer.of("bbsplusplus_outline_vanilla_glow",
        RenderSetup.builder(VANILLA_PIPELINE).translucent().build());

    /** HEAD/RETURN pairing for the cached path. Nested renders pair like a stack. */
    private record Prepared(Model model, ModelConfig config, OutlineShellCache121.Entry entry, int generation, boolean skipDepth)
    {
    }

    private static final ArrayDeque<Prepared> PREPARED = new ArrayDeque<>();

    private OutlineRenderer121()
    {
    }

    /** Draws the coloured rim. Called at {@code ModelInstance.render} HEAD, before the body. */
    public static void render(MatrixStack stack, Model model, ModelConfig config,
                              Color color, Link textureLink, Predicate<String> materialVisible)
    {
        if (!canRender(model, config, color))
        {
            return;
        }

        if (!OutlineGeometry.hasAnyOutline(model))
        {
            pushPrepared(model, config, null, true);

            return;
        }

        if (color.a <= 0F)
        {
            /* Still pair HEAD/RETURN, but do not bake or rebuild anything for a fully faded form. */
            pushPrepared(model, config, null, true);

            return;
        }

        Matrix4f base = new Matrix4f(stack.peek().getPositionMatrix());

        /* 1.21.11 has no readable model-view matrix (the pipeline owns it), so this port keeps the
         * model-space ramp; the screen-space sampling only runs on the 1.20.x renderer. */
        OutlineShellKey.Key shared = OutlineShellKey.of(model, config, textureLink, materialVisible);
        long baseHash = OutlineShellKey.baseHash(base);
        OutlineShellKey.Key key = new OutlineShellKey.Key(shared.value() * 31 + baseHash, shared.geometryHash());
        OutlineShellCache121.Entry entry = OutlineShellCache121.getOrBuild(model, key,
            cached -> buildEntry(cached, key, model, config, textureLink, materialVisible, base, baseHash));

        pushPrepared(model, config, entry);

        if (entry == null)
        {
            renderImmediate(stack, model, config, color, textureLink, materialVisible);

            return;
        }

        drawCached(entry, color.a, COLOR_LAYER);

        if (entry.mainVertices.length > 0)
        {
            /* The overlay shell is the wider one, so its full-bright pass would paint over the
             * parent rim. Run the wide layer first, repaint the narrow layer, then run the narrow
             * layer, so the parent outline always ends up on top. */
            drawVanilla(entry, color.a);
            drawArray(entry.mainVertices, entry.mainVertices.length / 7, color.a, COLOR_LAYER);
            drawArray(entry.mainVanillaVertices, entry.mainVanillaVertices.length / 7, color.a, VANILLA_LAYER);
        }
        else if (entry.vanillaVertexCount > 0)
        {
            drawVanilla(entry, color.a);
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

    private static void pushPrepared(Model model, ModelConfig config, OutlineShellCache121.Entry entry)
    {
        pushPrepared(model, config, entry, false);
    }

    private static void pushPrepared(Model model, ModelConfig config, OutlineShellCache121.Entry entry, boolean skipDepth)
    {
        while (PREPARED.size() > 32)
        {
            PREPARED.pollLast();
        }

        PREPARED.push(new Prepared(model, config, entry, entry == null ? 0 : entry.generation, skipDepth));
    }

    private static void buildEntry(OutlineShellCache121.Entry entry, OutlineShellKey.Key key, Model model,
                                   ModelConfig config, Link textureLink,
                                   Predicate<String> materialVisible, Matrix4f base, long baseHash)
    {
        if (entry.geometryHash != 0L && entry.geometryHash != key.geometryHash())
        {
            OutlineAlphaMask.invalidateMasks();
        }

        entry.geometryHash = key.geometryHash();

        Map<String, Float> decalSizes = OutlineGeometry.decalSizes(model);
        Set<ModelCube> decals = OutlineDecalFilter.find(model, decalSizes);
        List<OutlineGeometry.Spec> specs = OutlineGeometry.collect(model);

        specs.sort(WIDE_FIRST);

        Map<ModelCube, float[][]> faceRects = buildAlphaMask(model, textureLink);
        List<OutlineGeometry.Spec> vanilla = new ArrayList<>(specs.size());
        List<OutlineGeometry.Spec> main = new ArrayList<>(specs.size());
        boolean overlay = false;

        for (OutlineGeometry.Spec spec : specs)
        {
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

        OutlineShellCache121.VertexData data = new OutlineShellCache121.VertexData();

        OutlineGeometry.emit(model, config, specs, base, 1F, data::vertex,
            faceRects, decals, materialVisible, false);

        entry.vertices = data.trim();
        entry.vertexCount = data.count;

        if (overlay && !main.isEmpty())
        {
            /* Both layers are on: bake the narrow layer separately so it can be replayed after the
             * wide layer's full-bright pass (see render). */
            OutlineShellCache121.VertexData mainData = new OutlineShellCache121.VertexData();

            OutlineGeometry.emit(model, config, main, base, 1F, mainData::vertex,
                faceRects, decals, materialVisible, false);

            entry.mainVertices = mainData.trim();
            entry.mainVanillaVertices = bakeVanilla(model, config, main, base, faceRects, decals, materialVisible);
            entry.vanillaVertices = bakeVanilla(model, config, filterLayer(vanilla, 1), base, faceRects, decals, materialVisible);
        }
        else
        {
            entry.mainVertices = new float[0];
            entry.mainVanillaVertices = new float[0];
            entry.vanillaVertices = bakeVanilla(model, config, vanilla, base, faceRects, decals, materialVisible);
        }

        entry.vanillaVertexCount = entry.vanillaVertices.length / 7;
        entry.baseHash = baseHash;
        entry.bytes = OutlineShellCache121.bytesOf(data.count + entry.vanillaVertexCount);
        entry.valid = true;
    }

    /**
     * Bakes the vanilla glow overlay into {@code x, y, z, r, g, b, a} floats with the per-group
     * strength already folded into the alpha channel.
     */
    private static float[] bakeVanilla(Model model, ModelConfig config, List<OutlineGeometry.Spec> specs,
                                       Matrix4f base, Map<ModelCube, float[][]> faceRects, Set<ModelCube> decals,
                                       Predicate<String> materialVisible)
    {
        if (specs.isEmpty())
        {
            return new float[0];
        }

        Map<Integer, List<OutlineGeometry.Spec>> groups = new LinkedHashMap<>();

        for (OutlineGeometry.Spec spec : specs)
        {
            int strength = Math.round(Math.max(0F, Math.min(255F, spec.vanillaGlow())));

            if (strength <= 0 || spec.a() <= 0F)
            {
                continue;
            }

            groups.computeIfAbsent(strength, key -> new ArrayList<>()).add(spec);
        }

        if (groups.isEmpty())
        {
            return new float[0];
        }

        float[] vertices = new float[groups.size() * 64];
        int offset = 0;

        for (Map.Entry<Integer, List<OutlineGeometry.Spec>> group : groups.entrySet())
        {
            float strength = group.getKey() / 255F;
            OutlineShellCache121.VertexData data = new OutlineShellCache121.VertexData();

            OutlineGeometry.emit(model, config, group.getValue(), base, strength, data::vertex,
                faceRects, decals, materialVisible, false);

            float[] baked = data.trim();

            if (vertices.length < offset + baked.length)
            {
                vertices = Arrays.copyOf(vertices, Math.max(vertices.length * 2, offset + baked.length));
            }

            System.arraycopy(baked, 0, vertices, offset, baked.length);
            offset += baked.length;
        }

        return Arrays.copyOf(vertices, offset);
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

        if (prepared != null && prepared.skipDepth())
        {
            return;
        }

        Matrix4f base = new Matrix4f(stack.peek().getPositionMatrix());
        long baseHash = OutlineShellKey.baseHash(base);

        if (prepared == null || prepared.model != model || prepared.config != config
            || prepared.entry == null || !prepared.entry.valid
            || prepared.entry.generation != prepared.generation
            || prepared.entry.baseHash != baseHash)
        {
            if (prepared != null)
            {
                PREPARED.clear();
            }

            renderDepthImmediate(stack, model, config, color, textureLink, materialVisible);

            return;
        }

        drawCached(prepared.entry, color.a, DEPTH_LAYER);
    }

    private static void drawCached(OutlineShellCache121.Entry entry, float formAlpha, RenderLayer layer)
    {
        drawArray(entry.vertices, entry.vertexCount, formAlpha, layer);
    }

    private static void drawVanilla(OutlineShellCache121.Entry entry, float formAlpha)
    {
        drawArray(entry.vanillaVertices, entry.vanillaVertexCount, formAlpha, VANILLA_LAYER);
    }

    /** Replays a baked {@code x, y, z, r, g, b, a} array through one pipeline. */
    private static void drawArray(float[] vertices, int count, float formAlpha, RenderLayer layer)
    {
        if (count <= 0)
        {
            return;
        }

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < count; i++)
        {
            int offset = i * 7;

            builder.vertex(vertices[offset], vertices[offset + 1], vertices[offset + 2])
                .color(vertices[offset + 3], vertices[offset + 4], vertices[offset + 5], vertices[offset + 6] * formAlpha);
        }

        BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            layer.draw(built);
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
        List<OutlineGeometry.Spec> vanilla = new ArrayList<>(specs.size());

        for (OutlineGeometry.Spec spec : specs)
        {
            if (spec.vanillaGlow() > 0F)
            {
                vanilla.add(spec);
            }
        }

        drawImmediate(model, config, specs, base, color.a, faceRects, decals, materialVisible, COLOR_LAYER);
        drawVanillaImmediate(model, config, vanilla, base, color.a, faceRects, decals, materialVisible);
    }

    /** Fallback per-frame vanilla overlay used when the cached shell cannot be built. */
    private static void drawVanillaImmediate(Model model, ModelConfig config, List<OutlineGeometry.Spec> specs,
                                             Matrix4f base, float formAlpha, Map<ModelCube, float[][]> faceRects,
                                             Set<ModelCube> decals, Predicate<String> materialVisible)
    {
        if (specs.isEmpty())
        {
            return;
        }

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

        for (Map.Entry<Integer, List<OutlineGeometry.Spec>> group : groups.entrySet())
        {
            float strength = group.getKey() / 255F;

            drawImmediate(model, config, group.getValue(), base, formAlpha * strength,
                faceRects, decals, materialVisible, VANILLA_LAYER);
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

        drawImmediate(model, config, specs, new Matrix4f(stack.peek().getPositionMatrix()), color.a,
            faceRects, decals, materialVisible, DEPTH_LAYER);
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

    private static void drawImmediate(Model model, ModelConfig config, List<OutlineGeometry.Spec> specs, Matrix4f base,
                                      float formAlpha, Map<ModelCube, float[][]> faceRects, Set<ModelCube> decals,
                                      Predicate<String> materialVisible, RenderLayer layer)
    {
        if (specs.isEmpty())
        {
            return;
        }

        BufferBuilder[] builder = { null };

        OutlineGeometry.emit(model, config, specs, base, formAlpha, (x, y, z, nx, ny, nz, r, g, b, a) ->
        {
            if (builder[0] == null)
            {
                builder[0] = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            }

            builder[0].vertex(x, y, z).color(r, g, b, a);
        }, faceRects, decals, materialVisible, false);

        if (builder[0] == null)
        {
            return;
        }

        BuiltBuffer built = builder[0].endNullable();

        if (built != null)
        {
            layer.draw(built);
        }
    }
}
