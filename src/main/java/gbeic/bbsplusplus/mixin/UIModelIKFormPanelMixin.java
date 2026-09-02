package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.presets.AutoSavePresetState;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.forms.editors.panels.UIModelIKFormPanel;
import mchorse.bbs_mod.utils.pose.ModelIKManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * IK 链面板自动保存注入。
 * <p>
 * 在 commitChanges()、updateLabels()、updateMarkers() 等修改后触发的方法上注入，
 * 确保无论字段回调走哪条路径，都能触发自动保存。
 * </p>
 */
@Mixin(value = UIModelIKFormPanel.class, remap = true)
public abstract class UIModelIKFormPanelMixin
{
    @Shadow private String presetGroup;
    @Shadow private boolean syncingUI;

    @Shadow public abstract MapType toPresetData();

    @Inject(method = "commitChanges", at = @At("TAIL"))
    private void bbspp$autoSaveOnCommit(CallbackInfo ci)
    {
        bbspp$tryAutoSave();
    }

    @Inject(method = "updateLabels", at = @At("TAIL"))
    private void bbspp$autoSaveOnUpdateLabels(CallbackInfo ci)
    {
        bbspp$tryAutoSave();
    }

    @Inject(method = "updateMarkers", at = @At("TAIL"))
    private void bbspp$autoSaveOnUpdateMarkers(CallbackInfo ci)
    {
        bbspp$tryAutoSave();
    }

    private void bbspp$tryAutoSave()
    {
        if (this.syncingUI || !AutoSavePresetState.isEnabled("ik"))
        {
            return;
        }
        String preset = AutoSavePresetState.getSelectedPreset("ik");
        if (preset == null || preset.isEmpty() || this.presetGroup == null || this.presetGroup.isEmpty())
        {
            return;
        }

        MapType data = this.toPresetData();
        if (data != null)
        {
            AutoSavePresetState.scheduleSave("ik", ModelIKManager.INSTANCE,
                    this.presetGroup, preset, data);
        }
    }
}
