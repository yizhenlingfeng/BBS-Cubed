package bbslezy.forms.renderers;

import bbslezy.forms.utils.Illusion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Spacing rules, the one thing no UI test can reach. Lives beside the method it pins because
 * that visibility is package-private on purpose — the renderer is not an API surface.
 */
class FormIllusionRendererTest
{
    @Test
    void uniformUsesSpacingWhenSet()
    {
        Illusion illusion = new Illusion();
        illusion.uniform = true;
        illusion.spread = 2F;
        illusion.spacing = 1.5F;

        assertEquals(1.5F, FormIllusionRenderer.getIllusionDistance(illusion, 1, 4), 1e-4F);
        assertEquals(4.5F, FormIllusionRenderer.getIllusionDistance(illusion, 3, 4), 1e-4F);
    }

    @Test
    void uniformDividesTheSpreadWhenSpacingUnset()
    {
        Illusion illusion = new Illusion();
        illusion.uniform = true;
        illusion.spread = 2F;
        illusion.spacing = 0F;

        /* Even gaps, and the last copy lands exactly on the spread the decay mode reaches
         * with its first copy — flipping the toggle redistributes, it does not rescale. */
        assertEquals(0.5F, FormIllusionRenderer.getIllusionDistance(illusion, 1, 4), 1e-4F);
        assertEquals(1F, FormIllusionRenderer.getIllusionDistance(illusion, 2, 4), 1e-4F);
        assertEquals(1.5F, FormIllusionRenderer.getIllusionDistance(illusion, 3, 4), 1e-4F);
        assertEquals(2F, FormIllusionRenderer.getIllusionDistance(illusion, 4, 4), 1e-4F);
    }

    @Test
    void decayModePutsTheFirstCopyAtTheSpreadAndDecaysFromThere()
    {
        Illusion illusion = new Illusion();
        illusion.uniform = false;
        illusion.spread = 2F;

        assertEquals(2F, FormIllusionRenderer.getIllusionDistance(illusion, 1, 4), 1e-4F);
        assertEquals(3.5F, FormIllusionRenderer.getIllusionDistance(illusion, 2, 4), 1e-4F);
        assertEquals(4.5F, FormIllusionRenderer.getIllusionDistance(illusion, 3, 4), 1e-4F);
        assertEquals(5F, FormIllusionRenderer.getIllusionDistance(illusion, 4, 4), 1e-4F);
    }

    @Test
    void offsetShiftsTheWholeGroupInBothModes()
    {
        Illusion uniform = new Illusion();
        uniform.uniform = true;
        uniform.spread = 2F;
        uniform.offset = 0.5F;

        assertEquals(1F, FormIllusionRenderer.getIllusionDistance(uniform, 1, 4), 1e-4F);

        Illusion decay = new Illusion();
        decay.uniform = false;
        decay.spread = 2F;
        decay.offset = 0.5F;

        assertEquals(2.5F, FormIllusionRenderer.getIllusionDistance(decay, 1, 4), 1e-4F);
    }
}
