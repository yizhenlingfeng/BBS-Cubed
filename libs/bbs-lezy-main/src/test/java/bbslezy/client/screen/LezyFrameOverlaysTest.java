package bbslezy.client.screen;

import bbslezy.camera.clips.screen.ColorEffect;
import bbslezy.camera.clips.screen.GrainEffect;
import mchorse.bbs_mod.camera.clips.misc.ImageOverlay;
import mchorse.bbs_mod.camera.clips.misc.Subtitle;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which tracks a frame has to be drawn in. The case that matters here is the one where a track
 * holds something of the addon's and nothing else - a subtitle with no effect on its own track
 * still has to be drawn after the effects below it, or the text comes out under the grade.
 */
class LezyFrameOverlaysTest
{
    @Test
    void aSubtitleOnItsOwnTrackStillGetsDrawnAfterTheEffectsBelowIt()
    {
        ColorEffect grade = new ColorEffect();
        grade.layer = 0;

        Map<Subtitle, Integer> subtitles = new IdentityHashMap<>();
        subtitles.put(new Subtitle(), 2);

        assertEquals(List.of(0, 2), List.copyOf(LezyFrameOverlays.collectTracks(
            List.of(grade), List.of(), List.of(), Map.of(), subtitles)));
    }

    @Test
    void anImageOnAHigherTrackIsDrawnOverTheEffectBelowIt()
    {
        GrainEffect grain = new GrainEffect();
        grain.layer = 1;

        Map<ImageOverlay, Integer> images = new IdentityHashMap<>();
        images.put(new ImageOverlay(), 3);

        assertEquals(List.of(1, 3), List.copyOf(LezyFrameOverlays.collectTracks(
            List.of(), List.of(), List.of(grain), images, Map.of())));
    }

    @Test
    void tracksComeOutInOrderEvenWhenAddedBackToFront()
    {
        Map<Subtitle, Integer> subtitles = new IdentityHashMap<>();
        subtitles.put(new Subtitle(), 5);
        subtitles.put(new Subtitle(), 0);
        subtitles.put(new Subtitle(), 2);

        assertEquals(List.of(0, 2, 5), List.copyOf(LezyFrameOverlays.collectTracks(
            List.of(), List.of(), List.of(), Map.of(), subtitles)));
    }

    @Test
    void aFrameWithOnlyOverlaysAndNoEffectsStillHasTracks()
    {
        Map<ImageOverlay, Integer> images = new IdentityHashMap<>();
        images.put(new ImageOverlay(), 1);

        TreeSet<Integer> tracks = LezyFrameOverlays.collectTracks(List.of(), List.of(), List.of(), images, Map.of());

        assertEquals(1, tracks.size());
        assertEquals(1, tracks.first());
    }

    @Test
    void aFrameWithNothingOnItHasNoTracks()
    {
        assertTrue(LezyFrameOverlays.collectTracks(List.of(), List.of(), List.of(), Map.of(), Map.of()).isEmpty());
    }

    @Test
    void twoOverlaysOnOneTrackAreOneTrack()
    {
        Map<Subtitle, Integer> subtitles = new IdentityHashMap<>();
        subtitles.put(new Subtitle(), 4);
        subtitles.put(new Subtitle(), 4);

        assertEquals(List.of(4), List.copyOf(LezyFrameOverlays.collectTracks(
            List.of(), List.of(), List.of(), Map.of(), subtitles)));
    }

    @Test
    void installTakesOverOverlayRegistry()
    {
        mchorse.bbs_mod.ui.film.FrameOverlays.setup();
        assertTrue(LezyFrameOverlays.install());
        assertTrue(LezyFrameOverlays.isInstalled());
    }
}
