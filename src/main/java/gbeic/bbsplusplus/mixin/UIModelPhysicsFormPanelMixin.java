package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.presets.AutoSavePresetState;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIModelPhysicsFormPanel;
import mchorse.bbs_mod.utils.pose.ModelPhysicsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 物理骨骼面板自动保存注入。
 * <p>
 * 只注入 save(boolean)：commitChanges() 内部首行即调用 save(false)，
 * 而 trackpad/字段回调最终都汇入 commitChanges，因此 save 一处即可覆盖所有修改路径，
 * 也避免了原先 commitChanges 与 save 双重注入导致的重复快照构建。
 * 数据快照通过 supplier 延迟到防抖触发后才构建，拖动期间不产生序列化开销。
 * </p>
 */
@Mixin(value = UIModelPhysicsFormPanel.class, remap = true)
public abstract class UIModelPhysicsFormPanelMixin
{
    @Shadow(remap = false)
    public abstract MapType toPresetData();

    @Inject(method = "updateFields", at = @At("TAIL"))
    private void bbspp$autoSaveOnSave(CallbackInfo ci)
    {
        bbspp$tryAutoSave();
    }

    @Inject(method = "startEdit(Lmchorse/bbs_mod/forms/forms/ModelForm;)V", at = @At("HEAD"))
    private void bbspp$resetAutoSaveOnStartEdit(ModelForm form, CallbackInfo ci)
    {
        AutoSavePresetState.clear("physics");
    }

    private void bbspp$tryAutoSave()
    {
        if (!AutoSavePresetState.isEnabled("physics"))
        {
            return;
        }
        String preset = AutoSavePresetState.getSelectedPreset("physics");
        String presetGroup = ((UIBoneListFormPanelAccessor) (Object) this).bbspp$getPresetGroup();
        if (preset == null || preset.isEmpty() || presetGroup == null || presetGroup.isEmpty())
        {
            return;
        }

        AutoSavePresetState.scheduleSave("physics", ModelPhysicsManager.INSTANCE,
                presetGroup, preset, this::toPresetData);
    }
}
