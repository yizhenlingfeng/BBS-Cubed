package bbslezy.client.screen;

import bbslezy.camera.clips.screen.CinematicClip;
import bbslezy.camera.clips.screen.ColorClip;
import bbslezy.camera.clips.screen.ColorEffect;
import bbslezy.camera.clips.screen.GrainEffect;
import bbslezy.camera.clips.screen.LetterboxClip;
import bbslezy.camera.clips.screen.LayeredEffect;
import bbslezy.camera.clips.screen.LetterboxEffect;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class ScreenEffectRenderer
{
    /**
     * Draws the frame's overlays the way the timeline stacks them: one pass per layer, bottom
     * layer first, each pass drawing that layer's tint, then its shader effects, then its bars.
     *
     * <p>Before this every overlay on screen was folded into one shader pass in the order the
     * clips happened to be applied, so a track's grade and a track below it were summed into a
     * single look and the timeline read as flat. A layer now gets its own pass, which is what
     * makes the clip on the upper track an effect <em>on</em> the one below rather than more of
     * the same one. Layers with nothing to draw are skipped, and the usual film - everything on
     * one track - still costs exactly one pass.</p>
     */
    public static void render(Batcher2D batcher, ClipContext context, int screenW, int screenH)
    {
        List<ColorEffect> effects = ColorClip.getEffects(context);
        List<LetterboxEffect> letterboxEffects = LetterboxClip.getEffects(context);
        List<GrainEffect> grainEffects = CinematicClip.getGrainEffects(context);

        if (effects.isEmpty() && letterboxEffects.isEmpty() && grainEffects.isEmpty())
        {
            return;
        }

        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
        RenderSystem.disableDepthTest();

        for (int layer : collectLayers(effects, letterboxEffects, grainEffects))
        {
            renderLayer(batcher, layer, screenW, screenH, effects, letterboxEffects, grainEffects);
        }

        effects.clear();
        letterboxEffects.clear();
        grainEffects.clear();

        GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
        RenderSystem.enableDepthTest();
    }

    /** One track's worth of the frame: the tint it lays down, the pass over it, the bars on top. */
    public static void renderLayer(Batcher2D batcher, int layer, int screenW, int screenH,
        List<ColorEffect> effects, List<LetterboxEffect> letterboxEffects, List<GrainEffect> grainEffects)
    {
        List<ColorEffect> shaderEffects = new ArrayList<>();
        List<GrainEffect> layerGrain = new ArrayList<>();

        for (ColorEffect effect : effects)
        {
            if (effect.layer() != layer)
            {
                continue;
            }

            if (effect.hasOverlay)
            {
                batcher.box(0, 0, screenW, screenH, effect.overlayColor);
            }

            if (effect.hasGrade || effect.hasVignette || effect.hasDistort || effect.hasCinematic)
            {
                shaderEffects.add(effect);
            }
        }

        for (GrainEffect effect : grainEffects)
        {
            if (effect.layer() == layer)
            {
                layerGrain.add(effect);
            }
        }

        /* Shader pass: vintage flicker, scratches, color grade, fisheye, grain, and the rest. */
        if (!shaderEffects.isEmpty() || !layerGrain.isEmpty())
        {
            batcher.flush();
            ColorGradeRenderer.apply(shaderEffects, layerGrain);
            ColorGradeRenderer.resyncMinecraftState(batcher);
        }

        /* Letterbox bars, over the frame, on an ortho projection matrix of our own. */
        for (LetterboxEffect le : letterboxEffects)
        {
            if (le.layer() != layer)
            {
                continue;
            }

            Matrix4f cache = new Matrix4f(RenderSystem.getProjectionMatrix());
            Matrix4f ortho = new Matrix4f().ortho(0, screenW, screenH, 0, -1000, 3000);
            RenderSystem.setProjectionMatrix(ortho, VertexSorter.BY_Z);

            renderLetterbox(batcher, le, screenW, screenH);

            batcher.flush();
            RenderSystem.setProjectionMatrix(cache, VertexSorter.BY_Z);
        }
    }

    /**
     * Every layer that has something to draw, ascending, each one once.
     *
     * <p>Ascending because that is the order a timeline stacks in: a track composites onto what
     * is already there. Ties inside a layer keep the order the clips were applied in, so two
     * Color Grade clips on one track still sum the way they always did.</p>
     */
    static List<Integer> collectLayers(List<ColorEffect> effects, List<LetterboxEffect> letterboxEffects, List<GrainEffect> grainEffects)
    {
        List<Integer> layers = new ArrayList<>();

        collectLayers(layers, effects);
        collectLayers(layers, letterboxEffects);
        collectLayers(layers, grainEffects);

        layers.sort(null);

        List<Integer> unique = new ArrayList<>(layers.size());

        for (int layer : layers)
        {
            if (unique.isEmpty() || unique.get(unique.size() - 1) != layer)
            {
                unique.add(layer);
            }
        }

        return unique;
    }

    private static void collectLayers(List<Integer> layers, List<? extends LayeredEffect> effects)
    {
        for (LayeredEffect effect : effects)
        {
            layers.add(effect.layer());
        }
    }

    private static void renderLetterbox(Batcher2D batcher, LetterboxEffect effect, int screenW, int screenH)
    {
        if (effect.width <= 0F)
        {
            return;
        }

        int barH = (int) (screenH * effect.size);

        if (barH <= 0)
        {
            return;
        }

        float zoom = effect.zoom <= 0F ? 1F : effect.zoom;
        boolean transformed = effect.rotation != 0F || zoom != 1F || effect.offsetX != 0F || effect.offsetY != 0F;

        if (transformed)
        {
            MatrixStack stack = batcher.getContext().getMatrices();

            stack.push();
            stack.translate(effect.offsetX * screenW, effect.offsetY * screenH, 0F);
            stack.translate(screenW / 2F, screenH / 2F, 0F);
            stack.multiply(RotationAxis.POSITIVE_Z.rotation(MathUtils.toRad(effect.rotation)));
            stack.scale(zoom, zoom, 1F);
            stack.translate(-screenW / 2F, -screenH / 2F, 0F);
            renderLetterboxBars(batcher, effect, screenW, screenH, barH);
            stack.pop();
        }
        else
        {
            renderLetterboxBars(batcher, effect, screenW, screenH, barH);
        }
    }

    private static void renderLetterboxBars(Batcher2D batcher, LetterboxEffect effect, int screenW, int screenH, int barH)
    {
        int color = effect.color;
        int smoothH = (int) (barH * MathUtils.clamp(effect.smoothness, 0F, 1F));
        float barWidthFactor = effect.width;
        int barW = Math.max(1, Math.round(screenW * barWidthFactor));
        int barX = (screenW - barW) / 2;

        if (smoothH > 0)
        {
            int solidH = barH - smoothH;
            int transparent = Colors.setA(color, 0F);

            batcher.box(barX, 0, barX + barW, solidH, color);
            batcher.gradientVBox(barX, solidH, barX + barW, barH, color, transparent);

            batcher.gradientVBox(barX, screenH - barH, barX + barW, screenH - solidH, transparent, color);
            batcher.box(barX, screenH - solidH, barX + barW, screenH, color);
        }
        else
        {
            batcher.box(barX, 0, barX + barW, barH, color);
            batcher.box(barX, screenH - barH, barX + barW, screenH, color);
        }
    }
}
