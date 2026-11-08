package routu.bbsuirabbit.mixin;

import routu.bbsuirabbit.RabbitSettings;
import routu.bbsuirabbit.ui.morphing.UIBBSPPFormEditorList;
import java.util.function.Consumer;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.forms.IUIFormList;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={UIFormEditor.class})
public abstract class UIFormEditorMixin {
    @Shadow
    private Consumer<Form> callback;

    @Inject(method={"openFormList"}, at={@At(value="HEAD")}, cancellable=true, remap=true)
    private void rabbit$openFormList(Form current, Consumer<Form> callback, CallbackInfo ci) {
        if (RabbitSettings.newMorphingPanel == null || !((Boolean)RabbitSettings.newMorphingPanel.get()).booleanValue()) {
            return;
        }
        UIBBSPPFormEditorList list = new UIBBSPPFormEditorList((IUIFormList)((UIFormEditor)(Object)this));
        list.setSelected(current);
        this.callback = callback;
        list.full((UIElement)((UIFormEditor)(Object)this));
        list.resize();
        ((UIFormEditor)(Object)this).add((IUIElement)list);
        FormCategories formCategories = BBSModClient.getFormCategories();
        if (!formCategories.getAllCategories().isEmpty()) {
            list.setupForms(formCategories);
        }
        ci.cancel();
    }
}
