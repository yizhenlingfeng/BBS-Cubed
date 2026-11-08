package routu.bbsuirabbit.mixin;

import routu.bbsuirabbit.ui.morphing.UIBBSPPFormList;
import mchorse.bbs_mod.ui.forms.UIFormList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={UIFormList.class})
public class UIFormListMixin {
    @Inject(method={"applySearchFilter"}, at={@At(value="TAIL")}, remap=true)
    private void afterSearchFilter(String raw, CallbackInfo ci) {
        if ((Object)this instanceof UIBBSPPFormList) {
            ((UIBBSPPFormList)(Object)this).afterSearch(raw);
        }
    }
}
