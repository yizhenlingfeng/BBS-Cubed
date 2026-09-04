package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.premiere.PremiereExportActions;
import gbeic.bbsplusplus.premiere.PremiereUIKeys;
import gbeic.bbsplusplus.settings.CMLSettings;
import gbeic.bbsplusplus.ui.forms.SnowUIKeys;
import gbeic.bbsplusplus.utils.BonePriority;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UIFilmPreview;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the bone-priority and Premiere export controls below the film monitor.
 */
@Mixin(value = UIFilmPreview.class, remap = false)
public abstract class UIFilmPreviewMixin
{
    @Unique
    private static final int BBS_SNOW_FOLLOW_ORBIT_MODE = 6;

    @Shadow
    private UIFilmPanel panel;

    @Unique
    private UIIcon bbspp_cml$bonePriority;

    @Unique
    private UIIcon bbspp_cml$premiereExport;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$addMonitorButtons(UIFilmPanel panel, CallbackInfo ci)
    {
        UIFilmPreview self = (UIFilmPreview) (Object) this;

        this.bbspp_cml$bonePriority = new UIIcon(Icons.LIMB, (b) -> BonePriority.openMenu(self.getContext(), panel));
        this.bbspp_cml$bonePriority.tooltip(SnowUIKeys.BONE_PRIORITY);
        self.icons.addBefore(self.onionSkin, this.bbspp_cml$bonePriority);

        if (CMLSettings.premiereExportEnabled != null && CMLSettings.premiereExportEnabled.get())
        {
            this.bbspp_cml$premiereExport = new UIIcon(Icons.SOUND, (b) -> PremiereExportActions.onClick(panel));
            this.bbspp_cml$premiereExport.tooltip(PremiereUIKeys.EXPORT, Direction.LEFT);
            this.bbspp_cml$premiereExport.context((menu) ->
            {
                menu.action(Icons.GEAR, PremiereUIKeys.EXPORT_SETTINGS, () -> PremiereExportActions.openSettings(panel));
            });
            self.icons.add(this.bbspp_cml$premiereExport);
        }
    }

    @Inject(method = "subMouseScrolled", at = @At("HEAD"), cancellable = true, remap = false)
    private void bbspp_snow$zoomFollowOrbit(UIContext context, CallbackInfoReturnable<Boolean> cir)
    {
        UIFilmPreview self = (UIFilmPreview) (Object) this;
        Area area = self.getViewport();

        if (area.isInside(context) && !this.panel.isFlying() && this.panel.getController().getPovMode() == BBS_SNOW_FOLLOW_ORBIT_MODE)
        {
            cir.setReturnValue(this.panel.getController().zoomOrbit(context.mouseWheel));
        }
    }

}
