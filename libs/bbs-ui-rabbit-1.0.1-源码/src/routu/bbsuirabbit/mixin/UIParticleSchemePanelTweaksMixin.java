package routu.bbsuirabbit.mixin;

import mchorse.bbs_mod.l10n.L10n;
import routu.bbsuirabbit.mixin.UIDataDashboardPanelAccessor;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.particles.UIParticleSchemeRenderer;
import mchorse.bbs_mod.ui.framework.elements.layout.UIDockLayout;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.dashboard.panels.bar.UIPanelActionBar;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import org.lwjgl.glfw.GLFW;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Particle editor tweaks: the easy-to-misclick "reset layout" button leaves the top-right
 * corner and moves into the lock button's right-click menu; the restart button stops
 * wearing a trash icon; R restarts the emitter.
 */
@Mixin(value = UIParticleSchemePanel.class, remap = false)
public abstract class UIParticleSchemePanelTweaksMixin
{
    @Shadow(remap = false)
    public UIParticleSchemeRenderer renderer;

    @Shadow(remap = false)
    public UIDockLayout dock;

    /** The resetLayout button (third action() in the chain) is not added at all. */
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/ui/dashboard/panels/bar/UIPanelActionBar;action(Lmchorse/bbs_mod/ui/framework/elements/buttons/UIIcon;)Lmchorse/bbs_mod/ui/dashboard/panels/bar/UIPanelActionBar;", ordinal = 2), remap = false)
    private UIPanelActionBar rabbit$skipResetLayoutButton(UIPanelActionBar bar, UIIcon icon, Operation<UIPanelActionBar> original)
    {
        return bar;
    }

    /** The restart emitter button gets a refresh icon instead of a trash can. */
    @ModifyExpressionValue(method = "<init>", at = @At(value = "FIELD", target = "Lmchorse/bbs_mod/ui/utils/icons/Icons;TRASH:Lmchorse/bbs_mod/ui/utils/icons/Icon;", ordinal = 0), remap = false)
    private mchorse.bbs_mod.ui.utils.icons.Icon rabbit$restartIcon(mchorse.bbs_mod.ui.utils.icons.Icon icon)
    {
        return Icons.REFRESH;
    }

    /** Reset layout survives on the layout lock button's right-click menu. */
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/ui/dashboard/panels/bar/UIPanelActionBar;layout(Lmchorse/bbs_mod/ui/framework/elements/buttons/UIIcon;Ljava/util/function/BooleanSupplier;)Lmchorse/bbs_mod/ui/dashboard/panels/bar/UIPanelActionBar;"), index = 0, remap = false)
    private UIIcon rabbit$addResetToLockMenu(UIIcon lock)
    {
        lock.context((menu) -> menu.action(Icons.REFRESH, UIKeys.FILM_LAYOUT_RESET, () -> this.dock.resetLayout()));

        return lock;
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void rabbit$registerRestartKey(CallbackInfo ci)
    {
        UIParticleSchemePanel self = (UIParticleSchemePanel) (Object) this;

        self.keys().register(new KeyCombo("rabbit_restart_emitter", L10n.lang("rabbit.keys.restart_emitter"), GLFW.GLFW_KEY_R).category(UIKeys.SNOWSTORM_TITLE), () ->
        {
            if (((UIDataDashboardPanelAccessor) this).rabbit$getData() instanceof mchorse.bbs_mod.particles.ParticleScheme scheme)
            {
                this.renderer.setScheme(scheme);
            }
        }).active(() -> self.getContext() == null || !self.getContext().isFocused());
    }
}
