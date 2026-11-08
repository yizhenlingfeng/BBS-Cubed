package routu.bbsuirabbit.mixin;

import routu.bbsuirabbit.RabbitSettings;
import routu.bbsuirabbit.ui.morphing.UIBBSPPFormList;
import java.util.ArrayList;
import java.util.function.Consumer;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.forms.IUIFormList;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={UIFormPalette.class})
public class UIFormPaletteMixin {
    @Inject(method={"<init>"}, at={@At(value="TAIL")}, remap=true)
    private void onInitTail(Consumer<Form> callback, CallbackInfo ci) {
        this.rabbit$checkLayout();
    }

    @Inject(method={"render"}, at={@At(value="HEAD")}, remap=true)
    private void onRender(UIContext context, CallbackInfo ci) {
        this.rabbit$checkLayout();
    }

    @Unique
    private void rabbit$checkLayout() {
        boolean currentlyNewLayout;
        UIFormPalette self = (UIFormPalette)(Object)this;
        boolean isNewLayout = RabbitSettings.newMorphingPanel != null && (Boolean)RabbitSettings.newMorphingPanel.get() != false;
        if (isNewLayout != (currentlyNewLayout = self.list instanceof UIBBSPPFormList)) {
            FormCategories formCategories;
            Form selected;
            ArrayList<IUIElement> extraElements = new ArrayList<IUIElement>();
            boolean hadClose = true;
            if (self.list != null && self.list.bar != null) {
                for (Object child : self.list.bar.getChildren()) {
                    if (child == self.list.search || child == self.list.edit || child == self.list.close || child == self.list.categoryFilter || self.list instanceof UIBBSPPFormList && ((UIBBSPPFormList)self.list).isOwnBarElement(child)) continue;
                    extraElements.add((IUIElement) child);
                }
            }
            if (self.list != null && self.list.close != null) {
                hadClose = self.list.close.getParent() != null;
            }
            Form form = selected = self.list == null ? null : self.list.getSelected();
            if (self.list != null) {
                self.list.removeFromParent();
            }
            self.list = isNewLayout ? new UIBBSPPFormList((IUIFormList)self) : new UIFormList((IUIFormList)self);
            self.list.full((UIElement)self);
            if (!hadClose && self.list.close != null) {
                self.list.close.removeFromParent();
            }
            if (self.list.bar != null) {
                for (IUIElement iUIElement : extraElements) {
                    self.list.bar.add(iUIElement);
                }
            }
            if ((formCategories = BBSModClient.getFormCategories()) != null && !formCategories.getAllCategories().isEmpty()) {
                self.list.setupForms(formCategories);
            }
            if (selected != null) {
                self.list.setSelected(selected);
            }
            self.removeAll();
            self.add(new IUIElement[]{self.list, self.editor});
            if (self.editor != null) {
                self.list.setVisible(!self.editor.isEditing());
            }
            self.resize();
        }
    }
}
