package routu.bbsuirabbit.mixin;

import routu.bbsuirabbit.client.ui.home.IFilmHomeHost;
import routu.bbsuirabbit.client.ui.home.UIFilmLibraryHome;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.UIEditorDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.landing.UILandingScreen;
import mchorse.bbs_mod.ui.dashboard.panels.tabs.UITabList;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/**
 * The film panel's empty tab shows BBS++'s film library home instead of BBS's landing screen.
 *
 * <p>The landing screen stays mounted (other panels, other add-ons and the tours still find it)
 * but is kept hidden on the film panel, so neither BBS's version nor an add-on's restyle of it
 * (bbsrefreshedui) shows. Turning the setting off gives the landing screen back.</p>
 */
@Mixin(value = UIEditorDashboardPanel.class, remap = false)
public abstract class UIEditorDashboardPanelFilmHomeMixin extends UIDashboardPanel implements IFilmHomeHost
{
    @Shadow(remap = false)
    public UILandingScreen landing;

    @Shadow(remap = false)
    @Final
    public UITabList tabs;

    @Shadow(remap = false)
    protected boolean update;

    @Unique
    private UIFilmLibraryHome rabbit$home;

    @Unique
    private boolean rabbit$homeOn;

    private UIEditorDashboardPanelFilmHomeMixin(UIDashboard dashboard)
    {
        super(dashboard);
    }

    @Shadow(remap = false)
    protected abstract <T extends UIElement> T layoutUnderTopBar(T element);

    @Shadow(remap = false)
    public abstract void syncLanding();

    @Shadow(remap = false)
    public abstract void requestNames();

    @Override
    public UIFilmLibraryHome rabbit$getHome()
    {
        return this.rabbit$home;
    }

    @Inject(method = "mountLanding", at = @At("RETURN"), remap = false)
    private void rabbit$mountFilmHome(CallbackInfo ci)
    {
        if ((Object) this instanceof UIFilmPanel panel && this.rabbit$home == null)
        {
            this.rabbit$home = new UIFilmLibraryHome(panel);
            this.rabbit$home.setVisible(false);
            this.add(this.layoutUnderTopBar(this.rabbit$home));
            this.syncLanding();
        }
    }

    @Inject(method = "syncLanding", at = @At("HEAD"), cancellable = true, remap = false)
    private void rabbit$syncFilmHome(CallbackInfo ci)
    {
        if (this.rabbit$home == null)
        {
            return;
        }

        this.rabbit$homeOn = UIFilmLibraryHome.isHomeOn();

        if (!this.rabbit$homeOn)
        {
            this.rabbit$home.setVisible(false);

            return;
        }

        if (this.landing != null)
        {
            this.landing.setVisible(false);
        }

        this.rabbit$home.setVisible(this.tabs.getCurrentId() == null);
        ci.cancel();
    }

    /** The setting can be flipped while the panel is up (UIFilmPanelFilmHomeMixin asks every frame). */
    @Override
    public boolean rabbit$homeOutOfDate()
    {
        return this.rabbit$home != null && this.rabbit$homeOn != UIFilmLibraryHome.isHomeOn();
    }

    /** The names are refreshed whenever the home is what the panel shows, as BBS does for its landing screen. */
    @Inject(method = "requestNamesWhenStale", at = @At("HEAD"), cancellable = true, remap = false)
    private void rabbit$requestNamesForHome(CallbackInfo ci)
    {
        if (this.rabbit$home != null && this.rabbit$home.isVisible())
        {
            this.update = false;
            this.requestNames();
            ci.cancel();
        }
    }

    @Inject(method = "fillNames", at = @At("RETURN"), remap = false)
    private void rabbit$fillFilmHome(Collection<String> names, CallbackInfo ci)
    {
        if (this.rabbit$home != null)
        {
            this.rabbit$home.fillNames(names);
        }
    }
}
