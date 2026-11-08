package routu.bbsuirabbit.mixin;

import routu.bbsuirabbit.client.ui.film.IFilmLibraryLayoutToggle;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={UIElement.class})
public class UIElementMixin {
    /* WrapOperation, not Redirect: BBS++ redirects the same call, and two redirects there crash the game */
    @WrapOperation(method={"render"}, at=@At(value="INVOKE", target="Ljava/util/List;iterator()Ljava/util/Iterator;"), remap=false)
    private Iterator<IUIElement> rabbit$renderChildrenSnapshot(List<IUIElement> children, Operation<Iterator<IUIElement>> original) {
        return original.call(new ArrayList<IUIElement>(children));
    }

    @Inject(method={"render"}, at={@At(value="HEAD")}, remap=true)
    private void rabbit$syncFilmLibraryLayoutOnRender(UIContext context, CallbackInfo ci) {
        UIElementMixin uIElementMixin = this;
        if (uIElementMixin instanceof IFilmLibraryLayoutToggle) {
            IFilmLibraryLayoutToggle toggle = (IFilmLibraryLayoutToggle)((Object)uIElementMixin);
            toggle.rabbit$syncFilmLibraryLayout();
        }
    }
}
