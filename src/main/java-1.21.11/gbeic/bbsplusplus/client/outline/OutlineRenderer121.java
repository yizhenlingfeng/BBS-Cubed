package gbeic.bbsplusplus.client.outline;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import gbeic.bbsplusplus.api.GroupOutlineHolder;
import gbeic.bbsplusplus.client.outline.geometry.OutlineAlphaMask;
import gbeic.bbsplusplus.client.outline.geometry.OutlineDecalFilter;
import gbeic.bbsplusplus.client.outline.geometry.OutlineGeometry;
import gbeic.bbsplusplus.client.outline.geometry.OutlineShellKey;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.resources.Pixels;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Caches baked outline vertices and submits separate colour and depth passes.
 * Wider shells draw first because the colour pass leaves depth untouched.
 */
public final class OutlineRenderer121
{
    private static final Comparator<OutlineGeometry.Spec> WIDE_FIRST =
        Comparator.comparingDouble(OutlineGeometry.Spec::width).reversed();

    private static final RenderPipeline COLOR_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("bbsplusplus", "pipeline/outline_color"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(true)
            .build()
    );

    private static final RenderPipeline DEPTH_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
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

    /* The emissive shell draws before the model. Culling and disabled depth writes
     * keep the inner faces and shell depth from obscuring the model. */
    private static final RenderPipeline GLOW_PIPELINE = createGlowPipeline();

    private static RenderPipeline createGlowPipeline()
    {
        RenderPipeline pipeline = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
                .withLocation(Identifier.of("bbsplusplus", "pipeline/outline_glow"))
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(true)
                .withSampler("Sampler1")
                .build());

        // Iris reads the LabPBR emission texture through its emissive entity program.
        BBSRendering.mirrorIrisPipeline(pipeline, RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE);

        return pipeline;
    }

    private static final Map<Identifier, RenderLayer> GLOW_LAYERS = new ConcurrentHashMap<>();

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
            renderEmission(stack, model, config, color, textureLink, materialVisible);

            return;
        }

        drawCached(entry, color.a, COLOR_LAYER);

        renderEmission(stack, model, config, color, textureLink, materialVisible);
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

        drawImmediate(model, config, specs, base, color.a, faceRects, decals, materialVisible, COLOR_LAYER);
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

    /* ------------------------------------------------------------------------------------------
     * LabPBR 自发光通道（施工表 B2-07）
     * --------------------------------------------------------------------------------------- */

    /**
     * 是否有任何一组把轮廓 glow 打开 —— 绝大多数模型不发光，这里先做一次 O(组数) 的廉价判断，
     * 避免为了「没有自发光」的模型白跑一遍 {@code OutlineGeometry.collect}。
     *
     * <p><b>这里只认轮廓自己的 glow，不认 PBR emission。</b>逐骨骼 PBR 的 emission 走的是
     * LabPBR 的正规通道（{@code BoneGroupResolve121} 把五值经 {@code trackPbrVariant} 交给 Iris），
     * 不能拿来点亮自发光外壳：外壳画的是<b>轮廓色</b>的 1x1 贴图，某个骨骼只设了 emission、
     * 没设轮廓色时，那层壳会用默认白把自己整根骨骼盖成一片白（实机回归，2026-11-12）。</p>
     */
    private static boolean hasAnyGlow(Model model)
    {
        for (ModelGroup group : model.getAllGroups())
        {
            if (group instanceof GroupOutlineHolder holder)
            {
                for (int layer = 0; layer < 2; layer++)
                {
                    if (holder.bbspp_cml$hasOutlineLayer(layer) && holder.bbspp_cml$getOutlineGlow(layer) > 0F)
                    {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /** 把 0..1 的滑条值映到 LabPBR 的 alpha 字节；255 留给「该像素不发光」。 */
    private static int glowAlpha(float glow)
    {
        if (glow <= 0F)
        {
            return 0;
        }

        return (int) Math.max(1L, Math.min(254L, Math.round(glow)));
    }

    /** 一批自发光：一张伴生贴图 + 共用它的那些 spec。 */
    private record GlowBatch(OutlineGeometry.FacePaint paint, int glow, List<OutlineGeometry.Spec> specs)
    {
    }

    private static long paintId(OutlineGeometry.FacePaint paint, int glow)
    {
        return (paint.id() << 32) | (glow & 0xFFFFFFFFL);
    }

    private static RenderLayer glowLayer(Identifier texture)
    {
        return GLOW_LAYERS.computeIfAbsent(texture, id -> RenderLayer.of(
            "bbsplusplus_outline_glow",
            RenderSetup.builder(GLOW_PIPELINE)
                .texture("Sampler0", id)
                .translucent()
                .useLightmap()
                .useOverlay()
                .build()));
    }

    /**
     * 叠一层 LabPBR 自发光。只在 Iris 光影启用时才有意义 —— 没有光影包时没人采样
     * {@code _s} 伴生贴图，这一层只会把轮廓按原色再画一遍。
     *
     * <p>发光强度<b>只取轮廓自己的 glow 滑条</b>。逐骨骼 PBR 的 emission 不参与这里：
     * 它由 {@code BoneGroupResolve121} 经 {@code trackPbrVariant} 直接交给 Iris，
     * 走的是 LabPBR 的正规通道。曾经把两者取 {@code Math.max} 合在一起，结果某个骨骼只设了
     * emission、没设轮廓色时，这层壳会用默认白色 1x1 贴图把整根骨骼盖成一片白
     * （实机回归，2026-11-12，症状「Emission 会导致模型部分发白」）。</p>
     */
    private static void renderEmission(MatrixStack stack, Model model, ModelConfig config, Color color,
                                       Link textureLink, Predicate<String> materialVisible)
    {
        if (model == null || config == null || color == null || color.a <= 0F || !BBSRendering.isIrisShadersEnabled())
        {
            return;
        }

        if (!hasAnyGlow(model))
        {
            return;
        }

        List<OutlineGeometry.Spec> specs = OutlineGeometry.collect(model);

        if (specs.isEmpty())
        {
            return;
        }

        Map<Long, GlowBatch> batches = new java.util.LinkedHashMap<>();

        for (OutlineGeometry.Spec spec : specs)
        {
            int glow = glowAlpha(spec.glow());

            if (glow <= 0)
            {
                continue;
            }

            /* 每个 paint 各一批：纯色要自己的 1x1 diffuse、渐变要自己的 64x1，
             * 只用层色做 key 会把所有面压成同一种颜色。 */
            for (OutlineGeometry.FacePaint paint : OutlineGeometry.effectiveFacePaints(spec))
            {
                long key = paintId(paint, glow);
                GlowBatch batch = batches.get(key);

                if (batch == null)
                {
                    batch = new GlowBatch(paint, glow, new java.util.ArrayList<>());
                    batches.put(key, batch);
                }

                batch.specs().add(spec);
            }
        }

        if (batches.isEmpty())
        {
            return;
        }

        Map<String, Float> decalSizes = OutlineGeometry.decalSizes(model);
        Set<ModelCube> decals = OutlineDecalFilter.find(model, decalSizes);
        Map<ModelCube, float[][]> faceRects = buildAlphaMask(model, textureLink);
        Matrix4f base = new Matrix4f(stack.peek().getPositionMatrix());

        for (GlowBatch batch : batches.values())
        {
            OutlineGeometry.FacePaint paint = batch.paint();
            Identifier texture = paint.isRamp()
                ? OutlineEmissiveTexture.gradientIdentifier(paint.gradient(), batch.glow())
                : OutlineEmissiveTexture.diffuseIdentifier(paint.argb(), batch.glow());

            if (texture == null)
            {
                /* 生成失败只损失这一批的自发光，轮廓已经画过，不会变成没轮廓。 */
                continue;
            }

            BufferBuilder[] builder = { null };

            try
            {
                /* 必须是匿名类而不是 lambda：{@code VertexSink} 的抽象方法是 10 参版本，
                 * 带 {@code u} 的 12 参版本是 default 方法，lambda 覆盖不了它。 */
                OutlineGeometry.emit(model, config, batch.specs(), base, color.a,
                    new OutlineGeometry.VertexSink()
                    {
                        @Override
                        public void vertex(float x, float y, float z, float nx, float ny, float nz,
                                           float r, float g, float b, float a)
                        {
                            this.vertex(x, y, z, nx, ny, nz, 0F, 0F, r, g, b, a);
                        }

                        @Override
                        public void vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v,
                                           float r, float g, float b, float a)
                        {
                            if (builder[0] == null)
                            {
                                builder[0] = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES,
                                    VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
                            }

                            /* 纯色 diffuse 是 1x1，u 落在它唯一的纹素上；渐变是 64x1，沿 u 取色。 */
                            builder[0].vertex(x, y, z)
                                .color(1F, 1F, 1F, a)
                                .texture(u, 0.5F)
                                .overlay(OverlayTexture.DEFAULT_UV)
                                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                                .normal(nx, ny, nz);
                        }
                    }, faceRects, decals, materialVisible, true, paint);
            }
            catch (Throwable ignored)
            {
                /* 伴生贴图坏了只损失自发光。 */
                continue;
            }

            if (builder[0] == null)
            {
                continue;
            }

            BuiltBuffer built = builder[0].endNullable();

            if (built != null)
            {
                glowLayer(texture).draw(built);
            }
        }
    }
}
