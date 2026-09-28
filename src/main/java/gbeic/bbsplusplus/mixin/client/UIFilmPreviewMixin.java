package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.premiere.PremiereExportActions;
import gbeic.bbsplusplus.premiere.PremiereUIKeys;
import gbeic.bbsplusplus.settings.CMLSettings;
import gbeic.bbsplusplus.ui.forms.SnowUIKeys;
import gbeic.bbsplusplus.utils.BonePriority;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UIFilmPreview;
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
public abstract class UIFilmPreviewMixin
{
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

        /* 总是创建按钮，根据设置动态控制可见性，避免开关后需要重进游戏才刷新 */
        this.bbspp_cml$premiereExport = new UIIcon(Icons.SOUND, (b) -> PremiereExportActions.onClick(panel));
        this.bbspp_cml$premiereExport.tooltip(PremiereUIKeys.EXPORT, Direction.LEFT);
        this.bbspp_cml$premiereExport.context((menu) ->
        {
            menu.action(Icons.GEAR, PremiereUIKeys.EXPORT_SETTINGS, () -> PremiereExportActions.openSettings(panel));
        });
        self.icons.add(this.bbspp_cml$premiereExport);

        this.bbspp_cml$updatePremiereButtonVisibility();
    }

    /**
     * 根据设置更新 Premiere 导出按钮的可见性。
     */
    @Unique
    private void bbspp_cml$updatePremiereButtonVisibility()
    {
        boolean visible = CMLSettings.premiereExportEnabled != null && CMLSettings.premiereExportEnabled.get();
        this.bbspp_cml$premiereExport.setVisible(visible);
    }

    /**
     * 每次界面更新时同步按钮可见性，设置开关后立即生效。
     */
    @Inject(method = "update", at = @At("HEAD"), remap = false)
    private void bbspp_cml$onUpdate(CallbackInfo ci)
    {
        this.bbspp_cml$updatePremiereButtonVisibility();
    }
}
