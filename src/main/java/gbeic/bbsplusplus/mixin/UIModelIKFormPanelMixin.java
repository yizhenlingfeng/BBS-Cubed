package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.presets.AutoSavePresetState;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
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
 * 只注入 commitChanges()：所有控件回调（滑块、开关、骨骼选择）最终都汇入该方法；
 * updateLabels/updateMarkers 是纯 UI 刷新（且 commitChanges 内部已调用 updateMarkers），
 * 注入它们只会造成重复快照构建，故移除。
 * 数据快照通过 supplier 延迟到防抖触发后才构建，拖动期间不产生序列化开销。
 * </p>
 */
@Mixin(value = UIModelIKFormPanel.class, remap = true)
public abstract class UIModelIKFormPanelMixin
{
    @Shadow protected String presetGroup;

    @Shadow public abstract MapType toPresetData();

    @Inject(method = "updateFields", at = @At("TAIL"))
    private void bbspp$autoSaveOnCommit(CallbackInfo ci)
    {
        bbspp$tryAutoSave();
    }

    @Inject(method = "startEdit(Lmchorse/bbs_mod/forms/forms/ModelForm;)V", at = @At("HEAD"))
    private void bbspp$resetAutoSaveOnStartEdit(ModelForm form, CallbackInfo ci)
    {
        AutoSavePresetState.clear("ik");
    }

    private void bbspp$tryAutoSave()
    {
        if (!AutoSavePresetState.isEnabled("ik"))
        {
            return;
        }
        String preset = AutoSavePresetState.getSelectedPreset("ik");
        if (preset == null || preset.isEmpty() || this.presetGroup == null || this.presetGroup.isEmpty())
        {
            return;
        }

        AutoSavePresetState.scheduleSave("ik", ModelIKManager.INSTANCE,
                this.presetGroup, preset, this::toPresetData);
    }
}
