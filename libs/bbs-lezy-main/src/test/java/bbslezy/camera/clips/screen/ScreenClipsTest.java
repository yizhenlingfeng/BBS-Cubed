package bbslezy.camera.clips.screen;

import bbslezy.actions.LezyDamageActionClip;
import bbslezy.utils.keyframes.factories.LensRadiusSettingsKeyframeFactory;
import mchorse.bbs_mod.utils.interps.Interpolations;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScreenClipsTest
{
    @Test
    void colorClip_properties()
    {
        ColorClip clip = new ColorClip();
        assertNotNull(clip.create());
        assertInstanceOf(ColorClip.class, clip.create());
        assertEquals(14, clip.channels.length);
    }

    @Test
    void letterboxClip_properties()
    {
        LetterboxClip clip = new LetterboxClip();
        assertNotNull(clip.create());
        assertInstanceOf(LetterboxClip.class, clip.create());
        assertEquals(0.48D, clip.height.get(), 1e-4D, "Letterbox should default to 0.48 cinema ratio");
        assertEquals(1.0D, clip.width.get(), 1e-4D);
        assertEquals(8, clip.channels.length);
    }

    @Test
    void vignetteClip_properties()
    {
        VignetteClip clip = new VignetteClip();
        assertNotNull(clip.create());
        assertInstanceOf(VignetteClip.class, clip.create());
        assertEquals(2, clip.channels.length);
    }

    @Test
    void cinematicClip_properties()
    {
        CinematicClip clip = new CinematicClip();
        assertNotNull(clip.create());
        assertInstanceOf(CinematicClip.class, clip.create());
        assertNotNull(clip.vintage, "CinematicClip must have vintage keyframe channel");
        assertNotNull(clip.grainStrength, "CinematicClip must have grain channel");
    }

    @Test
    void lezyDamageActionClip_properties()
    {
        LezyDamageActionClip clip = new LezyDamageActionClip();
        assertTrue(clip.isClient(), "Damage action clip must be client-enabled for hurt timer & sounds");
        assertEquals(1.0F, clip.damage.get(), 1e-4F, "Default damage should be 1.0");
        assertNotNull(clip.create());
        assertInstanceOf(LezyDamageActionClip.class, clip.create());
    }

    @Test
    void lensRadius_interpolation()
    {
        LensRadiusSettings a = new LensRadiusSettings(1F, 1F);
        LensRadiusSettings b = new LensRadiusSettings(3F, 5F);

        LensRadiusSettings result = LensRadiusSettingsKeyframeFactory.INSTANCE.interpolate(
            a, a, b, b, Interpolations.LINEAR, 0.5F
        );

        assertEquals(2F, result.x, 1e-4F);
        assertEquals(3F, result.y, 1e-4F);
    }
}
