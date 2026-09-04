package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.settings.CMLSettings;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = UIReplaysEditor.class, remap = false)
public abstract class UIReplaysEditorActionsOverlayMixin
{
    @Inject(method = "getColor", at = @At("RETURN"), cancellable = true, remap = false)
    private static void bbspp_cml$colorActionsOverlaysLikeActions(
        String key,
        CallbackInfoReturnable<Integer> cir
    )
    {
        if (CMLSettings.isSnowActionsEnabled() && StringUtils.fileName(key).startsWith("actions_overlay"))
        {
            cir.setReturnValue(UIReplaysEditor.getColor("actions"));
        }
    }

    @Inject(method = "getIcon", at = @At("RETURN"), cancellable = true, remap = false)
    private static void bbspp_cml$iconActionsOverlaysLikeActions(
        String key,
        CallbackInfoReturnable<Icon> cir
    )
    {
        if (CMLSettings.isSnowActionsEnabled() && StringUtils.fileName(key).startsWith("actions_overlay"))
        {
            cir.setReturnValue(UIReplaysEditor.getIcon("actions"));
        }
    }
}
