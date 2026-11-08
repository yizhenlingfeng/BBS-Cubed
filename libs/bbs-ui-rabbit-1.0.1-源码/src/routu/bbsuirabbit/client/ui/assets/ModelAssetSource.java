package routu.bbsuirabbit.client.ui.assets;

import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.ui.dashboard.panels.landing.ILandingHost;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.l10n.L10n;

import java.util.HashMap;
import java.util.Map;

/** Model editor's home source: grid cards, a cached ModelForm preview per model key. */
public class ModelAssetSource implements AssetHomeSource
{
    private final ILandingHost host;
    private final Map<String, ModelForm> cache = new HashMap<>();

    public ModelAssetSource(ILandingHost host)
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
        return Icons.POSE;
    }

    @Override
    public boolean listStyle()
    {
        return false;
    }

    @Override
    public void renderThumb(UIContext ctx, String id, int x, int y, int w, int h, boolean hovered)
    {
        ModelForm form = this.cache.computeIfAbsent(id, (key) ->
        {
            ModelForm f = new ModelForm();

            f.model.set(key);

            return f;
        });

        FormUtilsClient.renderPreview(form, ctx, x, y, x + w, y + h);
    }

    @Override
    public void extraMenu(ContextMenuManager menu, String id)
    {
        ModelForm form = this.cache.get(id);

        if (form == null)
        {
            return;
        }

        menu.action(Icons.POSE, L10n.lang("rabbit.ui.assets.morph_with_it"), () -> ClientNetwork.sendPlayerForm(FormUtils.copy(form)));
    }
}
