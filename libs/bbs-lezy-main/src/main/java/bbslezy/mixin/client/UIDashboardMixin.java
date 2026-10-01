package bbslezy.mixin.client;

import bbslezy.ui.LezyIrisHelper;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.ui.utils.keys.Keybind;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UIDashboard.class, remap = false)
public abstract class UIDashboardMixin
{
    @Inject(method = "<init>", at = @At("TAIL"))
    private void bbslezy$init(CallbackInfo ci)
    {
        UIElement overlay = ((UIDashboard) (Object) this).overlay;

        overlay.keys().keybinds.removeIf(k -> "K".equalsIgnoreCase(k.getKeyCombo()));

        KeyCombo toggleShaders = new KeyCombo(
            "toggle_shaders",
            L10n.lang("bbslezy.ui.dashboard.keys.toggle_shaders"),
            GLFW.GLFW_KEY_K
        ).categoryKey("dashboard");

        overlay.keys().register(toggleShaders, LezyIrisHelper::toggleShaders).category(UIKeys.DASHBOARD_CATEGORY);
    }
}
