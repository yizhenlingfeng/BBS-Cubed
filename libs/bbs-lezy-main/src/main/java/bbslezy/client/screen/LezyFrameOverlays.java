package bbslezy.client.screen;

import bbslezy.camera.clips.screen.CinematicClip;
import bbslezy.camera.clips.screen.ColorClip;
import bbslezy.camera.clips.screen.ColorEffect;
import bbslezy.camera.clips.screen.GrainEffect;
import bbslezy.camera.clips.screen.LetterboxClip;
import bbslezy.camera.clips.screen.LetterboxEffect;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;

import mchorse.bbs_mod.camera.clips.misc.ImageClip;
import mchorse.bbs_mod.camera.clips.misc.ImageOverlay;
import mchorse.bbs_mod.camera.clips.misc.Subtitle;
import mchorse.bbs_mod.camera.clips.misc.SubtitleClip;
import mchorse.bbs_mod.ui.film.FrameOverlays;
import mchorse.bbs_mod.ui.film.UIImageRenderer;
import mchorse.bbs_mod.ui.film.UISubtitleRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Draws every overlay of the frame in timeline order: the lowest track first, the next one over
 * the top of it, up to the highest.
 *
 * <p>What this is for: BBS draws its images and subtitles through its own two renderers, and those
 * sit in the registry before any addon's, so a colour grade or a cinematic effect ran over the
 * subtitles no matter which track each of them was on. A track read as a stack only if the effect
 * on it was drawn at that point in the stack.</p>
 *
 * <p>So the two renderers are taken out of the registry here and their work is folded into this
 * pass, per track, along with the addon's own effects. The registry itself is only read and
 * written: if the accessor mixin did not apply, {@link #install()} declines, BBS keeps its two
 * renderers, and the frame draws the way it always has.</p>
 */
public class LezyFrameOverlays
{
    /** How many renderers {@code FrameOverlays.setup()} registers, and in what order. */
    private static final int BBS_OVERLAY_RENDERERS = 2;

    private static boolean ownsRegistry;

    /**
     * Takes BBS's image and subtitle renderers out of the overlay registry, leaving the addon to
     * draw them between the effects of the right track. Returns whether it happened - false means
     * the frame is being drawn the old way, which is the safe answer.
     */
    public static boolean install()
    {
        if (ownsRegistry)
        {
            return true;
        }

        try
        {
            Field f = FrameOverlays.class.getDeclaredField("RENDERERS");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<FrameOverlays.IFrameOverlayRenderer> renderers = (List<FrameOverlays.IFrameOverlayRenderer>) f.get(null);

            /* setup() registers the image renderer and the subtitle renderer before the event that
             * addons answer, so these two are them - but a registry that came back short or in an
             * order this does not know is left completely alone. */
            if (renderers == null || renderers.size() < BBS_OVERLAY_RENDERERS)
            {
                return false;
            }

            renderers.subList(0, BBS_OVERLAY_RENDERERS).clear();
            ownsRegistry = true;

            return true;
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }

    public static boolean isInstalled()
    {
        return ownsRegistry;
    }

    public static void render(MatrixStack stack, Batcher2D batcher, ClipContext context, int screenW, int screenH)
    {
        List<ColorEffect> effects = ColorClip.getEffects(context);
        List<LetterboxEffect> letterboxEffects = LetterboxClip.getEffects(context);
        List<GrainEffect> grainEffects = CinematicClip.getGrainEffects(context);
        List<ImageOverlay> images = ImageClip.getImages(context);
        List<Subtitle> subtitles = SubtitleClip.getSubtitles(context);
        if (effects.isEmpty() && letterboxEffects.isEmpty() && grainEffects.isEmpty() && images.isEmpty() && subtitles.isEmpty())
        {
            return;
        }

        if (!ownsRegistry)
        {
            install();
        }

        if (!ownsRegistry)
        {
            ScreenEffectRenderer.render(batcher, context, screenW, screenH);
            return;
        }

        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
        RenderSystem.disableDepthTest();

        Map<Object, Integer> overlayLayers = trackOfOverlays(context);
        Map<ImageOverlay, Integer> imageLayers = mapByLayer(images, overlayLayers);
        Map<Subtitle, Integer> subtitleLayers = mapByLayer(subtitles, overlayLayers);

        /* Every track on the frame, not just the ones holding an effect: a subtitle on track 3 is
         * still a track the effects below it have to be drawn under. */
        for (int track : collectTracks(effects, letterboxEffects, grainEffects, imageLayers, subtitleLayers))
        {
            ScreenEffectRenderer.renderLayer(batcher, track, screenW, screenH, effects, letterboxEffects, grainEffects);

            UIImageRenderer.renderImages(stack, batcher, atLayer(images, imageLayers, track));
            UISubtitleRenderer.renderSubtitles(stack, batcher, atLayer(subtitles, subtitleLayers, track));
            batcher.flush();
        }

        effects.clear();
        letterboxEffects.clear();
        grainEffects.clear();

        GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
        RenderSystem.enableDepthTest();
    }

    /**
     * Every track the frame has something on, ascending.
     *
     * <p>The overlays count, and not only the effects: a subtitle on a track of its own has to get
     * a turn, or the effects drawn after it would land on top of the text - which is the whole
     * point of the tracks being a stack.</p>
     */
    static TreeSet<Integer> collectTracks(List<ColorEffect> effects, List<LetterboxEffect> letterboxEffects,
        List<GrainEffect> grainEffects, Map<ImageOverlay, Integer> imageLayers, Map<Subtitle, Integer> subtitleLayers)
    {
        TreeSet<Integer> tracks = new TreeSet<>(ScreenEffectRenderer.collectLayers(effects, letterboxEffects, grainEffects));

        tracks.addAll(imageLayers.values());
        tracks.addAll(subtitleLayers.values());

        return tracks;
    }


    /**
     * The track each overlay was made on, found through the clip that made it.
     *
     * <p>The overlay objects carry no track of their own - the clip hands out the very instance it
     * fills in - so identity against the clip list is what connects the two.</p>
     */
    private static Map<Object, Integer> trackOfOverlays(ClipContext context)
    {
        Map<Object, Integer> layers = new IdentityHashMap<>();

        if (context.clips == null)
        {
            return layers;
        }

        for (Clip clip : context.clips.get())
        {
            if (clip instanceof ImageClip image)
            {
                layers.put(image.getOverlay(), image.layer.get());
            }
            else if (clip instanceof SubtitleClip subtitle)
            {
                layers.put(subtitle.getSubtitle(), subtitle.layer.get());
            }
        }

        return layers;
    }

    private static <T> Map<T, Integer> mapByLayer(List<T> overlays, Map<Object, Integer> layers)
    {
        Map<T, Integer> byLayer = new IdentityHashMap<>(overlays.size());

        for (T overlay : overlays)
        {
            /* An overlay with no clip behind it - one left over from a controller that was handed
             * clips this pass never saw - is drawn on the bottom track, where it was before. */
            byLayer.put(overlay, layers.getOrDefault(overlay, 0));
        }

        return byLayer;
    }

    private static <T> List<T> atLayer(List<T> overlays, Map<T, Integer> layers, int track)
    {
        List<T> here = new ArrayList<>();

        for (T overlay : overlays)
        {
            if (layers.get(overlay) == track)
            {
                here.add(overlay);
            }
        }

        return here;
    }
}
