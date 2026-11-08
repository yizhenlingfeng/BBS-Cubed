package routu.bbsuirabbit.client.ui.assets;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.ui.dashboard.panels.landing.ILandingHost;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.HashMap;
import java.util.Map;

/**
 * Particle editor's home source: a static sprite per card (the same renderPreview the
 * morphing palette's particle cells use), and ONE shared live emitter that only plays
 * under the hovered card.
 */
public class ParticleAssetSource implements AssetHomeSource
{
    private final ILandingHost host;
    private final Map<String, ParticleForm> cache = new HashMap<>();

    /** Not in the element tree: rendered by hand over the hovered card, never hit-tested. */
    private final ParticleLivePreview live = new ParticleLivePreview();

    public ParticleAssetSource(ILandingHost host)
    {
        this.host = host;
    }

    @Override
    public ILandingHost host()
    {
        return this.host;
    }

    @Override
    public Icon icon()
    {
        return Icons.PARTICLE;
    }

    @Override
    public boolean listStyle()
    {
        return false;
    }

    @Override
    public void renderThumb(UIContext ctx, String id, int x, int y, int w, int h, boolean hovered)
    {
        ParticleForm form = this.cache.computeIfAbsent(id, (key) ->
        {
            ParticleForm f = new ParticleForm();

            f.effect.set(key);

            return f;
        });

        FormUtilsClient.renderPreview(form, ctx, x, y, x + w, y + h);
    }

    /** The live emitter follows the hovered card; leaving clears it. */
    @Override
    public void tick(UIContext context, String hoveredId, int x, int y, int w, int h)
    {
        if (hoveredId == null)
        {
            this.live.clear();

            return;
        }

        this.live.render(context, hoveredId, null, x, y, w, h);
    }
}
