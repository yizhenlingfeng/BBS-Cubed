package routu.bbsuirabbit.client;

import mchorse.bbs_mod.api.BBSAddonMod;
import mchorse.bbs_mod.api.Subscribe;
import mchorse.bbs_mod.api.client.events.RegisterClientSettingsEvent;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import routu.bbsuirabbit.RabbitSettings;
import routu.bbsuirabbit.client.ui.home.FilmThumbnails;
import routu.bbsuirabbit.library.LibraryNetwork;

/**
 * BBS 2.7 client addon entry point ("bbs-client-addon"). BBS creates it before it posts any of
 * its registration events, which a plain client initializer cannot rely on.
 */
public class RabbitClientAddon implements BBSAddonMod
{
    public RabbitClientAddon()
    {
        /* Film library pictures: the integrated server keeps them, every client mirrors them */
        LibraryNetwork.register();
        FilmThumbnails.INSTANCE.register();
    }

    @Subscribe
    public void onRegisterSettings(RegisterClientSettingsEvent event)
    {
        event.register(Icons.HEART, RabbitSettings.ID, RabbitSettings::register);
    }
}
