package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.premiere.IPremiereExportButton;
import gbeic.bbsplusplus.premiere.PremiereExportActions;
import gbeic.bbsplusplus.premiere.PremiereUIKeys;
import gbeic.bbsplusplus.settings.CMLSettings;
import gbeic.bbsplusplus.ui.forms.SnowUIKeys;
import gbeic.bbsplusplus.utils.BonePriority;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UIFilmPreview;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 影片预览界面扩展：添加骨骼优先级按钮与 Premiere 导出按钮。
 */
@Mixin(value = UIFilmPreview.class, remap = false)
public abstract class UIFilmPreviewMixin implements IPremiereExportButton
{
    @Shadow
    private UIFilmPanel panel;

    @Unique
    private UIIcon bbspp_cml$bonePriority;

    @Unique
    private UIIcon bbspp_cml$premiereExport;

    @Unique
    private boolean bbspp_cml$lastPremiereVisible = false;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$addMonitorButtons(UIFilmPanel panel, CallbackInfo ci)
    {
        UIFilmPreview self = (UIFilmPreview) (Object) this;

        this.bbspp_cml$bonePriority = new UIIcon(Icons.LIMB, (b) -> BonePriority.openMenu(self.getContext(), panel));
        this.bbspp_cml$bonePriority.tooltip(SnowUIKeys.BONE_PRIORITY);
        self.icons.addBefore(self.onionSkin, this.bbspp_cml$bonePriority);

        this.bbspp_cml$premiereExport = new UIIcon(Icons.SOUND, (b) -> PremiereExportActions.onClick(panel));
        this.bbspp_cml$premiereExport.tooltip(PremiereUIKeys.EXPORT, Direction.LEFT);
        this.bbspp_cml$premiereExport.context((menu) ->
        {
            menu.action(Icons.GEAR, PremiereUIKeys.EXPORT_SETTINGS, () -> PremiereExportActions.openSettings(panel));
        });
        self.icons.add(this.bbspp_cml$premiereExport);

        this.bbspp_cml$lastPremiereVisible = !CMLSettings.premiereExportEnabled.get();
        this.bbspp_cml$updatePremiereButtonVisibility();
    }

    /**
     * 根据设置更新 Premiere 导出按钮的可见性。
     */
    @Override
    public void bbspp_cml$updatePremiereButtonVisibility()
    {
        boolean visible = CMLSettings.premiereExportEnabled != null && CMLSettings.premiereExportEnabled.get();
        if (this.bbspp_cml$lastPremiereVisible != visible)
        {
            this.bbspp_cml$lastPremiereVisible = visible;
            this.bbspp_cml$premiereExport.setVisible(visible);
        }
    }

    /**
     * 每次渲染时检查设置，同步按钮可见性（从主设置界面改完后回来也能生效）。
     */
    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void bbspp_cml$onRender(UIContext context, CallbackInfo ci)
    {
        this.bbspp_cml$updatePremiereButtonVisibility();
    }
}
