package bbslezy.utils;

import mchorse.bbs_mod.graphics.window.Window;

import java.util.function.Supplier;

public class LezyOS
{
    public static Supplier<String> osName = () -> System.getProperty("os.name", "");

    public static boolean isWindows()
    {
        return osName.get().toLowerCase().contains("win");
    }

    public static boolean isLinuxLike()
    {
        String os = osName.get().toLowerCase();

        return os.contains("nux") || os.contains("nix") || os.contains("aix");
    }

    public static boolean updateCursorHold(Object holder, boolean currentlyHolding, boolean shouldHold)
    {
        if (isWindows())
        {
            return false;
        }

        try
        {
            if (shouldHold)
            {
                Window.setCursorHidden(holder, true);
                return true;
            }
            else if (currentlyHolding)
            {
                Window.setCursorHidden(holder, false);
            }
        }
        catch (Throwable ignored)
        {}

        return false;
    }
}
