package routu.bbsuirabbit.mixin;

import routu.bbsuirabbit.client.ui.film.IFilmLibrarySearchBox;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={UITextbox.class})
public abstract class UITextboxFilmLibrarySearchMixin
implements IFilmLibrarySearchBox {
    @Unique
    private boolean rabbit$filmLibrarySearchBox;

    @Override
    public void rabbit$setFilmLibrarySearchBox(boolean enabled) {
        this.rabbit$filmLibrarySearchBox = enabled;
    }

    @Inject(method={"subKeyPressed"}, at={@At(value="HEAD")}, cancellable=true, remap=true)
    private void rabbit$clearFilmLibrarySearchOnEscape(UIContext context, CallbackInfoReturnable<Boolean> cir) {
        UITextbox self = (UITextbox)(Object)this;
        if (this.rabbit$filmLibrarySearchBox && self.isFocused() && context.isPressed(256) && !self.getText().isEmpty()) {
            self.setText("");
            if (self.callback != null) {
                self.callback.accept("");
            }
            cir.setReturnValue(true);
        }
    }
}
