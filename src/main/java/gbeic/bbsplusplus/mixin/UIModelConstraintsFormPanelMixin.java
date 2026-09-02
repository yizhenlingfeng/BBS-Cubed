package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.presets.AutoSavePresetState;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.forms.editors.panels.UIModelConstraintsFormPanel;
import mchorse.bbs_mod.utils.pose.ModelConstraintsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 骨骼限制面板自动保存注入。
 */
@Mixin(value = UIModelConstraintsFormPanel.class, remap = true)
public abstract class UIModelConstraintsFormPanelMixin
{
    @Shadow private String presetGroup;
    @Shadow private boolean syncingUI;

    @Shadow public abstract MapType toPresetData();

    @Inject(method = "commitChanges", at = @At("TAIL"))
    private void bbspp$autoSaveOnCommit(CallbackInfo ci)
    {
        bbspp$tryAutoSave();
    }

    @Inject(method = "onFieldChanged", at = @At("TAIL"))
    private void bbspp$autoSaveOnFieldChanged(CallbackInfo ci)
    {
        bbspp$tryAutoSave();
    }

    @Inject(method = "updateFields", at = @At("TAIL"))
    private void bbspp$autoSaveOnUpdateFields(CallbackInfo ci)
    {
        bbspp$tryAutoSave();
    }

    private void bbspp$tryAutoSave()
    {
        if (this.syncingUI || !AutoSavePresetState.isEnabled("constraints"))
        {
            return;
        }
        String preset = AutoSavePresetState.getSelectedPreset("constraints");
        if (preset == null || preset.isEmpty() || this.presetGroup == null || this.presetGroup.isEmpty())
        {
            return;
        }

        MapType data = this.toPresetData();
        if (data != null)
        {
            AutoSavePresetState.scheduleSave("constraints", ModelConstraintsManager.INSTANCE,
                    this.presetGroup, preset, data);
        }
    }
}
