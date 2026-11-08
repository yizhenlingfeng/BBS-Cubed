package routu.bbsuirabbit.mixin;

import routu.bbsuirabbit.RabbitSettings;
import routu.bbsuirabbit.client.ui.assets.AssetHomeSource;
import routu.bbsuirabbit.client.ui.assets.AudioAssetSource;
import routu.bbsuirabbit.client.ui.assets.ModelAssetSource;
import routu.bbsuirabbit.client.ui.assets.ParticleAssetSource;
import routu.bbsuirabbit.client.ui.assets.UIAssetHome;
import routu.bbsuirabbit.client.ui.film.IFilmLibraryLayoutToggle;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.UIEditorDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.landing.UILandingScreen;
import mchorse.bbs_mod.ui.dashboard.panels.tabs.UITabList;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.model_editor.UIModelEditorPanel;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.utility.audio.UIAudioEditorPanel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/**
 * The model, particle and audio editors' empty tab shows BBS++'s asset workbench instead
 * of BBS's landing screen. Structured after the film-home mixin: the landing stays mounted
 * but hidden, and the existing render-time layout hook (IFilmLibraryLayoutToggle via
 * UIElementMixin) flips the home when the setting toggles while the panel is up.
 */
@Mixin(value = UIEditorDashboardPanel.class, remap = false)
public abstract class UIEditorDashboardPanelAssetHomeMixin extends UIDashboardPanel implements IFilmLibraryLayoutToggle
{
    @Shadow(remap = false)
    public UILandingScreen landing;

    @Shadow(remap = false)
    @Final
    public UITabList tabs;

    @Shadow(remap = false)
    protected boolean update;

    @Unique
    private UIAssetHome rabbit$assetHome;

    @Unique
    private boolean rabbit$assetHomeOn;

    private UIEditorDashboardPanelAssetHomeMixin(UIDashboard dashboard)
    {
        super(dashboard);
    }

    @Shadow(remap = false)
    protected abstract <T extends UIElement> T layoutUnderTopBar(T element);

    @Shadow(remap = false)
    public abstract void syncLanding();

    @Shadow(remap = false)
    public abstract void requestNames();

    @Unique
    private static boolean rabbit$isOn()
    {
        return RabbitSettings.assetHome != null && RabbitSettings.assetHome.get();
    }

    @Inject(method = "mountLanding", at = @At("RETURN"), remap = false)
    private void rabbit$mountAssetHome(CallbackInfo ci)
    {
        if (this.rabbit$assetHome != null)
        {
            return;
        }

        AssetHomeSource source = null;

        if ((Object) this instanceof UIModelEditorPanel)
        {
            source = new ModelAssetSource((mchorse.bbs_mod.ui.dashboard.panels.landing.ILandingHost)(Object)this);
        }
        else if ((Object) this instanceof UIParticleSchemePanel)
        {
            source = new ParticleAssetSource((mchorse.bbs_mod.ui.dashboard.panels.landing.ILandingHost)(Object)this);
        }
        else if ((Object) this instanceof UIAudioEditorPanel)
        {
            source = new AudioAssetSource((mchorse.bbs_mod.ui.dashboard.panels.landing.ILandingHost)(Object)this);
        }

        if (source != null)
        {
            this.rabbit$assetHome = new UIAssetHome(source);
            this.rabbit$assetHome.setVisible(false);
            this.add(this.layoutUnderTopBar(this.rabbit$assetHome));
            this.syncLanding();
        }
    }

    @Inject(method = "syncLanding", at = @At("HEAD"), cancellable = true, remap = false)
    private void rabbit$syncAssetHome(CallbackInfo ci)
    {
        if (this.rabbit$assetHome == null)
        {
            return;
        }

        this.rabbit$assetHomeOn = rabbit$isOn();

        if (!this.rabbit$assetHomeOn)
        {
            this.rabbit$assetHome.setVisible(false);

            return;
        }

        if (this.landing != null)
        {
            this.landing.setVisible(false);
        }

        this.rabbit$assetHome.setVisible(this.tabs.getCurrentId() == null);
        ci.cancel();
    }

    @Override
    public void rabbit$syncFilmLibraryLayout()
    {
        if (this.rabbit$assetHome != null && this.rabbit$assetHomeOn != rabbit$isOn())
        {
            this.syncLanding();
        }
    }

    @Inject(method = "requestNamesWhenStale", at = @At("HEAD"), cancellable = true, remap = false)
    private void rabbit$requestNamesForAssetHome(CallbackInfo ci)
    {
        if (this.rabbit$assetHome != null && this.rabbit$assetHome.isVisible())
        {
            this.update = false;
            this.requestNames();
            ci.cancel();
        }
    }

    @Inject(method = "fillNames", at = @At("RETURN"), remap = false)
    private void rabbit$fillAssetHome(Collection<String> names, CallbackInfo ci)
    {
        if (this.rabbit$assetHome != null)
        {
            this.rabbit$assetHome.fillNames(names);
        }
    }
}
