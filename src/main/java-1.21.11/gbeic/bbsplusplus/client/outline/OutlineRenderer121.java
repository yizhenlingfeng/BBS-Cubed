package gbeic.bbsplusplus.client.outline;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
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

        Set<ModelCube> decals = OutlineDecalFilter.find(model, outline.bbspp$getOutlineDecalSize());
        List<OutlineGeometry.Spec> specs = OutlineGeometry.collect(model, config, outline, decals);

        draw(model, config, specs, new Matrix4f(stack.peek().getPositionMatrix()), color.a,
            buildAlphaMask(model, textureLink), decals, materialVisible, COLOR_LAYER);
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

        draw(model, config, specs, new Matrix4f(stack.peek().getPositionMatrix()), color.a,
            buildAlphaMask(model, textureLink), decals, materialVisible, DEPTH_LAYER);
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

    private static void draw(Model model, ModelConfig config, List<OutlineGeometry.Spec> specs, Matrix4f base,
                             float formAlpha, Map<ModelCube, float[][]> faceRects, Set<ModelCube> decals,
                             Predicate<String> materialVisible, RenderLayer layer)
    {
        if (specs.isEmpty())
        {
            return;
        }

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        int[] count = { 0 };

        OutlineGeometry.emit(model, config, specs, base, formAlpha, (x, y, z, nx, ny, nz, r, g, b, a) ->
        {
            builder.vertex(x, y, z).color(r, g, b, a);
            count[0]++;
        }, faceRects, decals, materialVisible);

        BuiltBuffer built = builder.endNullable();

        if (built != null && count[0] > 0)
        {
            layer.draw(built);
        }
    }
}
