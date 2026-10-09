package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.LimbCullingConfigHolder;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.ui.ValueStringKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.model_editor.UIModelBoneList;
import mchorse.bbs_mod.ui.model_editor.UIModelConfigEditor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(value = UIModelConfigEditor.class, remap = true)
public abstract class UIModelConfigEditorLimbCullingMixin
{
    @Shadow private UIElement bonePanel;
    @Shadow private UIModelBoneList bones;
    @Shadow private ModelConfig data;

    @Inject(method = "fillBone", at = @At("TAIL"))
    private void bbspp$addLimbCullingToggle(CallbackInfo ci)
    {
        if (!(this.data instanceof LimbCullingConfigHolder holder)) return;

        String bone = this.bones.getCurrentFirst();
        ValueStringKeys value = holder.bbspp$getLimbCullingBonesValue();
        boolean culled = bone != null && holder.bbspp$getLimbCullingBones().contains(bone);
        IKey label = L10n.lang("bbs.ui.model_editor.limb_culling.toggle");
        String finalBone = bone;

        this.bonePanel.add(new UIToggle(label, culled, t ->
        {
            if (finalBone == null) return;
            boolean enabled = t.getValue();
            BaseValue.edit(value, (ValueStringKeys v) ->
            {
                Set<String> set = v.get();
                if (set != null)
                {
                    if (enabled) set.add(finalBone);
                    else set.remove(finalBone);
                }
            });
        }));
    }
}
