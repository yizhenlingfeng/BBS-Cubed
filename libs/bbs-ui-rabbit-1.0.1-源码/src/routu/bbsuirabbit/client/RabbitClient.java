package routu.bbsuirabbit.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.categories.FormCategory;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import routu.bbsuirabbit.RabbitMod;
import routu.bbsuirabbit.RabbitSettings;
import routu.bbsuirabbit.ui.morphing.FavoriteFormCategory;
import routu.bbsuirabbit.ui.morphing.FavoriteFormSection;

/**
 * Fabric client entry point: the favourites section of the morphing panel. Everything that
 * hooks into BBS's own registration lives in {@link RabbitClientAddon}.
 */
public class RabbitClient implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        /* The favourites section joins the palette through the extra-sections list, which
         * FormCategories.setup() consults on every rebuild. If BBS already ran its first
         * setup before this initializer ran, rebuild once so the section shows up without
         * waiting for an asset reload. */
        FormCategories.registerSection(FavoriteFormSection::new);
        ClientLifecycleEvents.CLIENT_STARTED.register((client) ->
        {
            try
            {
                FormCategories categories = BBSModClient.getFormCategories();

                if (categories == null || !RabbitSettings.isOn(RabbitSettings.newMorphingPanel))
                {
                    return;
                }

                for (FormCategory category : categories.getAllCategories())
                {
                    if (category instanceof FavoriteFormCategory)
                    {
                        return;
                    }
                }

                categories.setup();
            }
            catch (Exception e)
            {
                RabbitMod.LOGGER.warn("Couldn't add the favourites category: {}", e.getMessage());
            }
        });
    }
}
