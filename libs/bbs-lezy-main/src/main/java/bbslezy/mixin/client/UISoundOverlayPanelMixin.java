package bbslezy.mixin.client;

import bbslezy.audio.LezyAudioCodecs;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.overlay.UISoundOverlayPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

@Mixin(value = UISoundOverlayPanel.class, remap = false)
public abstract class UISoundOverlayPanelMixin
{
    /**
     * ponytail: append extra audio/video containers to picker return;
     * leaves core's HashSet intact and avoids bytecode redirects.
     */
    @Inject(method = "getSoundEvents", at = @At("RETURN"), cancellable = true)
    private static void bbslezy$addSupportedAudioContainers(CallbackInfoReturnable<Set<String>> cir)
    {
        Set<String> locations = cir.getReturnValue();

        if (locations == null)
        {
            locations = new HashSet<>();
        }
        else
        {
            locations = new HashSet<>(locations);
        }

        for (Link link : BBSMod.getProvider().getLinksFromPath(Link.assets("audio")))
        {
            String pathLower = link.path.toLowerCase();

            if (LezyAudioCodecs.shouldHandle(pathLower) || LezyAudioCodecs.isVideo(pathLower))
            {
                locations.add(link.toString());
            }
        }

        cir.setReturnValue(locations);
    }
}
