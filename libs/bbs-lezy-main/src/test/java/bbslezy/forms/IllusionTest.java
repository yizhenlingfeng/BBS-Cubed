package bbslezy.forms;

import bbslezy.forms.renderers.FormIllusionRenderer;
import bbslezy.forms.utils.Illusion;
import bbslezy.forms.utils.LezyIllusionHelper;
import bbslezy.forms.values.ValueIllusion;
import bbslezy.utils.keyframes.factories.IllusionKeyframeFactory;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.interps.Interpolations;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IllusionTest
{
    @Test
    void testSerializationRoundTrip()
    {
        Illusion original = new Illusion();
        original.count = 4;
        original.spread = 1.5F;
        original.directions = Illusion.FRONT | Illusion.BACK | Illusion.UP;
        original.offset = 0.25F;
        original.opacity = 0.6F;
        original.opacityUniform = true;
        original.invert = true;
        original.uniform = true;
        original.spacing = 0.75F;
        original.real = true;
        original.delay = 2.5F;
        original.distort = 0.3F;
        original.distortUniform = true;
        original.glow = 1.2F;
        original.gradual = true;
        original.transform.scale.set(0.8F, 0.8F, 0.8F);
        original.transform.rotate.set(0F, 45F, 0F);

        MapType data = original.toData();

        Illusion restored = new Illusion();
        restored.fromData(data);

        assertTrue(restored.enabled);
        assertEquals(original.count, restored.count);
        assertEquals(original.directions, restored.directions);
        assertEquals(original.offset, restored.offset, 1e-4F);
        assertEquals(original.opacity, restored.opacity, 1e-4F);
        assertTrue(restored.opacityUniform);
        assertTrue(restored.invert);
        assertTrue(restored.uniform);
        assertEquals(original.spacing, restored.spacing, 1e-4F);
        assertTrue(restored.real);
        assertEquals(original.delay, restored.delay, 1e-4F);
        assertEquals(original.distort, restored.distort, 1e-4F);
        assertEquals(original.glow, restored.glow, 1e-4F);
        assertTrue(restored.gradual);
        assertEquals(0.8F, restored.transform.scale.x, 1e-4F);
        assertEquals(45F, restored.transform.rotate.y, 1e-4F);
    }

    @Test
    void testKeyframeInterpolation()
    {
        Illusion a = new Illusion();
        a.count = 2;
        a.spread = 1.0F;
        a.opacity = 0.2F;
        a.directions = Illusion.LEFT | Illusion.RIGHT;

        Illusion b = new Illusion();
        b.count = 6;
        b.spread = 3.0F;
        b.opacity = 0.8F;
        b.directions = Illusion.LEFT | Illusion.RIGHT;

        Illusion mid = IllusionKeyframeFactory.INSTANCE.interpolate(a, a, b, b, Interpolations.LINEAR, 0.5F);

        assertEquals(4, mid.count);
        assertEquals(2.0F, mid.spread, 1e-4F);
        assertEquals(0.5F, mid.opacity, 1e-4F);
        assertEquals(Illusion.LEFT | Illusion.RIGHT, mid.directions);
    }

    @Test
    void testDirectionsBitmask()
    {
        List<Vector3f> all = FormIllusionRenderer.getIllusionDirections(Illusion.FRONT | Illusion.BACK | Illusion.LEFT | Illusion.RIGHT | Illusion.UP | Illusion.DOWN);
        assertEquals(6, all.size());

        List<Vector3f> frontOnly = FormIllusionRenderer.getIllusionDirections(Illusion.FRONT);
        assertEquals(1, frontOnly.size());
        assertEquals(0F, frontOnly.get(0).x, 1e-4F);
        assertEquals(0F, frontOnly.get(0).y, 1e-4F);
        assertEquals(1F, frontOnly.get(0).z, 1e-4F);

        List<Vector3f> fallback = FormIllusionRenderer.getIllusionDirections(0);
        assertEquals(4, fallback.size()); // Front, Left, Right, Back
    }

    @Test
    void testDualPropertySync()
    {
        mchorse.bbs_mod.forms.forms.ModelForm source = new mchorse.bbs_mod.forms.forms.ModelForm();
        mchorse.bbs_mod.forms.forms.ModelForm target = new mchorse.bbs_mod.forms.forms.ModelForm();

        Illusion illusion = new Illusion();
        illusion.count = 5;
        illusion.spread = 2.0F;
        illusion.directions = Illusion.FRONT | Illusion.BACK;

        LezyIllusionHelper.setIllusion(source, illusion);

        assertNotNull(LezyIllusionHelper.getIllusion(source));
        assertEquals(5, LezyIllusionHelper.getIllusion(source).count);
        assertNull(LezyIllusionHelper.getIllusion(target));

        LezyIllusionHelper.syncIllusion(source, target);

        assertNotNull(LezyIllusionHelper.getIllusion(target));
        assertEquals(5, LezyIllusionHelper.getIllusion(target).count);
        assertEquals(2.0F, LezyIllusionHelper.getIllusion(target).spread, 1e-4F);
        assertEquals(Illusion.FRONT | Illusion.BACK, LezyIllusionHelper.getIllusion(target).directions);
    }

}
