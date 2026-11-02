package gbeic.bbsplusplus.client.outline;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 1.21.11 RenderPipeline backend for the outline shell.
 *
 * <p>1.21.5 removed the immediate {@code RenderSystem.setShader} / {@code BufferRenderer} path.
 * The same position-colour geometry is now built into a BufferBuilder and submitted through two
 * immutable render layers: colour (depth-tested, no depth write) and depth-only. Glow groups fall
 * back to the solid colour path until the 1.21.11 LabPBR entity layer is ported.</p>
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

    private static final RenderLayer COLOR_LAYER = RenderLayer.of("bbsplusplus_outline_color",
        RenderSetup.builder(COLOR_PIPELINE).translucent().build());
    private static final RenderLayer DEPTH_LAYER = RenderLayer.of("bbsplusplus_outline_depth",
        RenderSetup.builder(DEPTH_PIPELINE).build());

    /** HEAD/RETURN pairing for the cached path. Nested renders pair like a stack. */
    private record Prepared(Model model, ModelConfig config, OutlineShellCache121.Entry entry, int generation)
    {
    }

    private static final ArrayDeque<Prepared> PREPARED = new ArrayDeque<>();

    private OutlineRenderer121()
    {
    }

    /** Draws the coloured rim. Called at {@code ModelInstance.render} HEAD, before the body. */
    public static void render(MatrixStack stack, Model model, ModelConfig config, OutlineConfigHolder outline,
                              Color color, Link textureLink, Predicate<String> materialVisible)
    {
        if (!canRender(model, config, outline, color))
        {
            return;
        }

        if (color.a <= 0F)
        {
            /* Still pair HEAD/RETURN, but do not bake or rebuild anything for a fully faded form. */
            pushPrepared(model, config, null);

            return;
        }

        Matrix4f base = new Matrix4f(stack.peek().getPositionMatrix());
        OutlineShellKey.Key shared = OutlineShellKey.of(model, config, outline, textureLink, materialVisible);
        long baseHash = OutlineShellKey.baseHash(base);
        OutlineShellKey.Key key = new OutlineShellKey.Key(shared.value() * 31 + baseHash, shared.geometryHash());
        OutlineShellCache121.Entry entry = OutlineShellCache121.getOrBuild(model, key,
            cached -> buildEntry(cached, key, model, config, outline, textureLink, materialVisible, base, baseHash));

        pushPrepared(model, config, entry);

        if (entry == null)
        {
            renderImmediate(stack, model, config, outline, color, textureLink, materialVisible);

            return;
        }

        drawCached(entry, color.a, COLOR_LAYER);
    }

    private static void pushPrepared(Model model, ModelConfig config, OutlineShellCache121.Entry entry)
    {
        while (PREPARED.size() > 32)
        {
            PREPARED.pollLast();
        }

        PREPARED.push(new Prepared(model, config, entry, entry == null ? 0 : entry.generation));
    }

    private static void buildEntry(OutlineShellCache121.Entry entry, OutlineShellKey.Key key, Model model,
                                   ModelConfig config, OutlineConfigHolder outline, Link textureLink,
                                   Predicate<String> materialVisible, Matrix4f base, long baseHash)
    {
        if (entry.geometryHash != 0L && entry.geometryHash != key.geometryHash())
        {
            OutlineAlphaMask.invalidateMasks();
        }

        entry.geometryHash = key.geometryHash();

        Set<ModelCube> decals = OutlineDecalFilter.find(model, outline.bbspp$getOutlineDecalSize());
        List<OutlineGeometry.Spec> specs = OutlineGeometry.collect(model, config, outline, decals);
        Map<ModelCube, float[][]> faceRects = buildAlphaMask(model, textureLink);
        OutlineShellCache121.VertexData data = new OutlineShellCache121.VertexData();

        OutlineGeometry.emit(model, config, specs, base, 1F, data::vertex,
            faceRects, decals, materialVisible, false);

        entry.vertices = data.trim();
        entry.vertexCount = data.count;
        entry.baseHash = baseHash;
        entry.bytes = OutlineShellCache121.bytesOf(data.count);
        entry.valid = true;
    }

    /** Writes depth for rim pixels that survived the body. Called at {@code ModelInstance.render} RETURN. */
    public static void renderDepth(MatrixStack stack, Model model, ModelConfig config, OutlineConfigHolder outline,
                                   Color color, Link textureLink, Predicate<String> materialVisible)
    {
        if (!canRender(model, config, outline, color))
        {
            return;
        }

        Prepared prepared = PREPARED.poll();

        if (color.a <= 0F)
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

            renderDepthImmediate(stack, model, config, outline, color, textureLink, materialVisible);

            return;
        }

        drawCached(prepared.entry, color.a, DEPTH_LAYER);
    }

    private static void drawCached(OutlineShellCache121.Entry entry, float formAlpha, RenderLayer layer)
    {
        if (entry.vertexCount <= 0)
        {
            return;
        }

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        float[] vertices = entry.vertices;

        for (int i = 0; i < entry.vertexCount; i++)
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

    /** Old per-frame path, kept as a fallback when the cache cannot build. */
    private static void renderImmediate(MatrixStack stack, Model model, ModelConfig config, OutlineConfigHolder outline,
                                        Color color, Link textureLink, Predicate<String> materialVisible)
    {
        Set<ModelCube> decals = OutlineDecalFilter.find(model, outline.bbspp$getOutlineDecalSize());
        List<OutlineGeometry.Spec> specs = OutlineGeometry.collect(model, config, outline, decals);
        Map<ModelCube, float[][]> faceRects = buildAlphaMask(model, textureLink);

        drawImmediate(model, config, specs, new Matrix4f(stack.peek().getPositionMatrix()), color.a,
            faceRects, decals, materialVisible, COLOR_LAYER);
    }

    /** Old per-frame depth path, kept as a fallback when the cache cannot build. */
    private static void renderDepthImmediate(MatrixStack stack, Model model, ModelConfig config,
                                             OutlineConfigHolder outline, Color color, Link textureLink,
                                             Predicate<String> materialVisible)
    {
        Set<ModelCube> decals = OutlineDecalFilter.find(model, outline.bbspp$getOutlineDecalSize());
        List<OutlineGeometry.Spec> specs = OutlineGeometry.collect(model, config, outline, decals);
        Map<ModelCube, float[][]> faceRects = buildAlphaMask(model, textureLink);

        drawImmediate(model, config, specs, new Matrix4f(stack.peek().getPositionMatrix()), color.a,
            faceRects, decals, materialVisible, DEPTH_LAYER);
    }

    private static boolean canRender(Model model, ModelConfig config, OutlineConfigHolder outline, Color color)
    {
        return model != null && config != null && outline != null && color != null
            && outline.bbspp$hasOutline()
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
