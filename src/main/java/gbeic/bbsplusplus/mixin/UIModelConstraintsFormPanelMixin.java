package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.presets.AutoSavePresetState;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIModelConstraintsFormPanel;
import mchorse.bbs_mod.utils.pose.ModelConstraintsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 骨骼限制面板自动保存注入。
 * <p>
 * 只注入 commitChanges()：trackpad 主回调 onFieldChanged() 内部即调用 commitChanges()，
 * 注入两处会造成重复快照；updateFields 是"数据→控件"的刷新路径（syncingUI 复位在 TAIL 之前，
 * 注入挡不住程序化赋值），移除后切换骨骼/加载预设不再触发多余调度。
 * 数据快照通过 supplier 延迟到防抖触发后才构建，拖动期间不产生序列化开销。
 * </p>
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

    @Inject(method = "startEdit(Lmchorse/bbs_mod/forms/forms/ModelForm;)V", at = @At("HEAD"))
    private void bbspp$resetAutoSaveOnStartEdit(ModelForm form, CallbackInfo ci)
    {
        AutoSavePresetState.clear("constraints");
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

        AutoSavePresetState.scheduleSave("constraints", ModelConstraintsManager.INSTANCE,
                this.presetGroup, preset, this::toPresetData);
    }
}
