package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.MolangSharedProvider;
import gbeic.bbsplusplus.ui.forms.SnowUIKeys;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIActionsFormPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在伪装编辑器的"动作"面板底部追加"variable 动作模型互通"开关:写入
 * ModelForm 上由 {@code ModelFormMixin} 注册的 molangShared 值(默认关闭
 * = 修复后的按模型隔离;开启 = 回到原版全局共享行为)。
 */
@Mixin(value = UIActionsFormPanel.class, remap = false)
public abstract class UIActionsFormPanelMixin
{
    @Unique
    private UIToggle bbspp_cml$molangShared;

    @Unique
    private ModelForm bbspp_cml$form;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void bbspp_cml$addMolangSharedToggle(UIForm editor, CallbackInfo ci)
    {
        this.bbspp_cml$molangShared = new UIToggle(SnowUIKeys.MOLANG_SHARED, (b) ->
        {
            if (this.bbspp_cml$form instanceof MolangSharedProvider provider && provider.bbspp_cml$getMolangShared() != null)
            {
                provider.bbspp_cml$getMolangShared().set(b.getValue());
            }
        });
        this.bbspp_cml$molangShared.tooltip(SnowUIKeys.MOLANG_SHARED_TOOLTIP);

        ((UIActionsFormPanel) (Object) this).options.add(this.bbspp_cml$molangShared);
    }

    @Inject(method = "startEdit(Lmchorse/bbs_mod/forms/forms/ModelForm;)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$syncMolangSharedToggle(ModelForm form, CallbackInfo ci)
    {
        this.bbspp_cml$form = form;

        if (form instanceof MolangSharedProvider provider && provider.bbspp_cml$getMolangShared() != null)
        {
            this.bbspp_cml$molangShared.setValue(provider.bbspp_cml$getMolangShared().get());
        }
    }
}
