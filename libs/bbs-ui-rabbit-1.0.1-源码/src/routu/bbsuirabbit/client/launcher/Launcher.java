package routu.bbsuirabbit.client.launcher;

import routu.bbsuirabbit.RabbitSettings;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_437;
import net.minecraft.class_442;
import net.minecraft.class_500;
import net.minecraft.class_525;
import net.minecraft.class_526;

/**
 * The BBS-Rabbit launcher takes the place of vanilla's title screen, world list and server
 * list: whenever the game would open one of those (start-up, leaving a world, a mod asking for
 * the title screen), the launcher opens instead. A setting turns it off, and anything going
 * wrong inside it turns it off for the session and hands back vanilla's title screen.
 */
public final class Launcher
{
    private static UILauncherMenu menu;
    private static boolean failed;

    private Launcher()
    {}

    public static boolean enabled()
    {
        /* Before BBS has registered its settings the default (on) applies */
        return !failed && (RabbitSettings.titleLauncher == null || RabbitSettings.titleLauncher.get());
    }

    /** setScreen's argument: the launcher where vanilla would show its menus. */
    public static class_437 wrap(class_437 screen)
    {
        if (screen instanceof class_525 create)
        {
            WorldActions.onCreateScreen(create);

            return screen;
        }

        if (!enabled())
        {
            return screen;
        }

        class_310 mc = class_310.method_1551();

        /* Still loading resources: nothing of BBS's UI is ready yet */
        if (screen == null && mc.method_18506() != null)
        {
            return screen;
        }

        boolean title = screen instanceof class_442 || (screen == null && mc.field_1687 == null);
        boolean worlds = screen instanceof class_526;
        boolean servers = screen instanceof class_500;

        if (!title && !worlds && !servers)
        {
            return screen;
        }

        try
        {
            if (menu == null)
            {
                menu = new UILauncherMenu();
            }

            menu.launcher.showView(servers ? UILauncher.VIEW_SERVERS : UILauncher.VIEW_SAVES);

            return new UIScreen(class_2561.method_43473(), menu);
        }
        catch (Throwable e)
        {
            fail(e);

            return screen == null ? new class_442() : screen;
        }
    }

    /** Whether the launcher is the screen on display. */
    public static boolean isShowing()
    {
        return menu != null && class_310.method_1551().field_1755 instanceof UIScreen && UIScreen.getCurrentMenu() == menu;
    }

    /**
     * BBS's UI scale (0 = Minecraft's own) times the launcher's zoom, while the launcher shows:
     * its stickers and cards are meant to be seen bigger than BBS's dense editor panels.
     */
    public static float scale(float custom)
    {
        if (!isShowing())
        {
            return custom;
        }

        float zoom = RabbitSettings.launcherScale == null ? 1.3F : RabbitSettings.launcherScale.get();

        if (Math.abs(zoom - 1F) < 0.001F)
        {
            return custom;
        }

        float base = custom;

        if (base <= 0F)
        {
            class_310 mc = class_310.method_1551();

            base = mc.method_22683().method_4476((Integer) mc.field_1690.method_42474().method_41753(), (Boolean) mc.field_1690.method_42437().method_41753());
        }

        return base * zoom;
    }

    public static void fail(Throwable e)
    {
        failed = true;
        System.err.println("[BBS UI Rabbit] The launcher failed and is off until restart; vanilla's title screen takes over");
        e.printStackTrace();
    }

    /** Leave for vanilla's title screen after a failure inside the launcher. */
    public static void fallBack(Throwable e)
    {
        fail(e);
        class_310.method_1551().method_1507(new class_442());
    }
}
