package routu.bbsuirabbit.ui.morphing;

import routu.bbsuirabbit.RabbitMod;
import routu.bbsuirabbit.client.ui.assets.GLStateGuard;
import routu.bbsuirabbit.client.ui.assets.ParticleLivePreview;
import routu.bbsuirabbit.client.ui.assets.VanillaParticleLivePreview;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.cells.CellState;

/** The morph cell currently being rendered, plus its live particle preview state. */
public final class MorphCellState
{
    public static Form currentForm;
    public static CellState currentState;

    private static final ParticleLivePreview live = new ParticleLivePreview();
    private static final VanillaParticleLivePreview vanillaLive = new VanillaParticleLivePreview();
    private static boolean liveBroken;
    private static boolean vanillaBroken;

    private MorphCellState()
    {}

    public static void enter(Form form, CellState state)
    {
        currentForm = form;
        currentState = state;
    }

    public static void leave()
    {
        currentForm = null;
        currentState = null;
    }

    /** BBS-particle live count; vanilla previews report true (scene is private). */
    public static boolean hasParticles()
    {
        if (currentForm instanceof VanillaParticleForm)
        {
            return true;
        }

        return live.hasParticles();
    }

    /** Live preview for the current particle cell; false = draw the static sprite. */
    public static boolean livePreview(Form form, UIContext context, int x, int y, int w, int h)
    {
        if (currentState == null || !currentState.hover)
        {
            return false;
        }

        if (form instanceof VanillaParticleForm vanilla && !vanillaBroken)
        {
            GLStateGuard guard = GLStateGuard.capture(context);

            try
            {
                return vanillaLive.render(context, vanilla, x, y, w, h);
            }
            catch (Throwable t)
            {
                guard.restore(context);
                vanillaBroken = true;
                RabbitMod.LOGGER.warn("Morph vanilla particle preview failed; disabled for this session", t);

                return false;
            }
        }

        if (!(form instanceof ParticleForm particle) || liveBroken)
        {
            return false;
        }

        Link texture = particle.texture.get();
        GLStateGuard guard = GLStateGuard.capture(context);

        try
        {
            return live.render(context, particle.effect.get(), texture, x, y, w, h);
        }
        catch (Throwable t)
        {
            guard.restore(context);
            liveBroken = true;
            RabbitMod.LOGGER.warn("Morph particle live preview failed; disabled for this session", t);

            return false;
        }
    }
}
