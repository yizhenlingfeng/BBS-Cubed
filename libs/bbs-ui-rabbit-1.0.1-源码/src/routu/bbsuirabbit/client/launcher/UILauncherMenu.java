package routu.bbsuirabbit.client.launcher;

import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIRenderingContext;

/** The BBS menu that hosts the launcher; one instance, reopened like BBS's dashboard. */
public class UILauncherMenu extends UIBaseMenu
{
    public final UILauncher launcher;

    public UILauncherMenu()
    {
        this.launcher = new UILauncher();
        this.launcher.full(this.viewport);
        this.main.add(this.launcher);
    }

    @Override
    public boolean canHideHUD()
    {
        return false;
    }

    @Override
    public boolean canPause()
    {
        return false;
    }

    @Override
    public void onOpen(UIBaseMenu oldMenu)
    {
        super.onOpen(oldMenu);
        this.launcher.onOpen();
    }

    @Override
    public void onClose(UIBaseMenu nextMenu)
    {
        super.onClose(nextMenu);
        this.launcher.onClose();
    }

    /** This is the title screen: Escape has nowhere to go back to. */
    @Override
    protected void closeMenu()
    {}

    @Override
    public void renderMenu(UIRenderingContext context, int mouseX, int mouseY)
    {
        try
        {
            super.renderMenu(context, mouseX, mouseY);
        }
        catch (Throwable e)
        {
            Launcher.fallBack(e);
        }
    }
}
