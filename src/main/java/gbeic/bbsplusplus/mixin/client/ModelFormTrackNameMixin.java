package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.settings.CMLSettings;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.L10n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Form.class, remap = false)
public abstract class ModelFormTrackNameMixin
{
    @Inject(
        method = "getTrackName(Ljava/lang/String;)Ljava/lang/String;",
        at = @At("RETURN"),
        cancellable = true,
        remap = false
    )
    private void bbspp_cml$nameActionsOverlayTracks(String property, CallbackInfoReturnable<String> cir)
    {
        if (!CMLSettings.isSnowActionsEnabled())
        {
            return;
        }

        int propertySlash = property.lastIndexOf('/');
        String name = propertySlash < 0 ? property : property.substring(propertySlash + 1);
        String current = cir.getReturnValue();
        int currentSlash = current.lastIndexOf('/');
        String prefix = currentSlash < 0 ? "" : current.substring(0, currentSlash + 1);

        if (name.equals("actions_overlay"))
        {
            cir.setReturnValue(prefix + L10n.lang("bbspp.ui.film.replays.actions_overlay").get());

            return;
        }

        if (!name.startsWith("actions_overlay"))
        {
            return;
        }

        try
        {
            int index = Integer.parseInt(name.substring("actions_overlay".length()));
            String label = L10n.lang("bbspp.ui.film.replays.actions_overlay_numbered").get();

            cir.setReturnValue(prefix + String.format(label, index + 2));
        }
        catch (NumberFormatException ignored)
        {
            /* 预期内：actions_overlay 后缀不是数字时说明不是本模组的编号轨道，保持原名即可 */
        }
    }
}
