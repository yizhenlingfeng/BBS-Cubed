package gbeic.bbsplusplus.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/** Runtime-only visibility switches for film authoring helpers. */
public final class FilmVisibilityController
{
    private static final String LUMEN_MOD = "lumencore";
    private static final String IRL_MOD = "irlite";
    private static final String VFX_MOD = "vfxlights";
    private static final String LUMEN_RENDERER = "mc.lumencore.client.integration.bbs.render.LumenGizmoRenderer";

    private static boolean lumen = true;
    private static boolean irl = true;
    private static boolean cameraReference = true;
    private static boolean vfx = true;
    private static boolean ik = true;
    private static boolean disguise = true;

    private FilmVisibilityController()
    {
    }

    public static boolean isLumenAvailable()
    {
        return FabricLoader.getInstance().isModLoaded(LUMEN_MOD);
    }

    public static boolean isIrlAvailable()
    {
        return FabricLoader.getInstance().isModLoaded(IRL_MOD);
    }

    public static boolean isVfxAvailable()
    {
        return FabricLoader.getInstance().isModLoaded(VFX_MOD);
    }

    public static boolean isLumenVisible()
    {
        syncLumen();

        return lumen;
    }

    public static boolean isIrlVisible()
    {
        return irl;
    }

    public static boolean isCameraReferenceVisible()
    {
        syncCameraReference();

        return cameraReference;
    }

    public static boolean isVfxVisible()
    {
        return vfx;
    }

    public static boolean isIkVisible()
    {
        return ik;
    }

    public static boolean isDisguiseVisible()
    {
        return disguise;
    }

    public static void setLumenVisible(boolean visible)
    {
        lumen = visible;

        if (!isLumenAvailable())
        {
            return;
        }

        try
        {
            Class<?> renderer = Class.forName(LUMEN_RENDERER, false, FilmVisibilityController.class.getClassLoader());
            Method isVisible = renderer.getMethod("isVisible");
            Method toggleVisible = renderer.getMethod("toggleVisible");

            if ((Boolean) isVisible.invoke(null) != visible)
            {
                toggleVisible.invoke(null);
            }
        }
        catch (Throwable ignored)
        {
            /* Optional integration: a mismatched Lumen build must not break BBS. */
        }
    }

    public static void setIrlVisible(boolean visible)
    {
        irl = visible;
    }

    public static void setCameraReferenceVisible(boolean visible)
    {
        cameraReference = visible;

        if (BBSSettings.recordingCameraPreview != null)
        {
            BBSSettings.recordingCameraPreview.set(visible);
        }
    }

    public static void setVfxVisible(boolean visible)
    {
        vfx = visible;
    }

    public static void setIkVisible(boolean visible)
    {
        ik = visible;

        if (BBSSettings.ikDebug != null)
        {
            BBSSettings.ikDebug.enabled.set(visible);
        }
    }

    public static void setDisguiseVisible(boolean visible)
    {
        disguise = visible;
    }

    /** Keeps non-visual light forms active while film disguises are hidden. */
    public static boolean shouldRenderFilmForm(Form form)
    {
        if (disguise || !isFilmPanelActive() || form == null)
        {
            return true;
        }

        String className = form.getClass().getName();

        return className.startsWith("mc.lumencore.integration.bbs.form.")
            || className.startsWith("qualet.irlite.forms.")
            || className.startsWith("com.bbsvfx.vfxlights.forms.");
    }

    public static void openMenu(UIContext context)
    {
        if (context == null)
        {
            return;
        }

        syncLumen();
        syncCameraReference();
        syncIkState();
        context.replaceContextMenu((menu) -> fillMenu(context, menu));
    }

    private static void syncLumen()
    {
        if (!isLumenAvailable())
        {
            return;
        }

        try
        {
            Class<?> renderer = Class.forName(LUMEN_RENDERER, false, FilmVisibilityController.class.getClassLoader());
            lumen = (Boolean) renderer.getMethod("isVisible").invoke(null);
        }
        catch (Throwable ignored)
        {
            /* Keep the last local value when an optional integration is unavailable. */
        }
    }

    private static void syncIkState()
    {
        if (BBSSettings.ikDebug != null)
        {
            ik = BBSSettings.ikDebug.enabled.get();
        }
    }

    private static void syncCameraReference()
    {
        if (BBSSettings.recordingCameraPreview != null)
        {
            cameraReference = BBSSettings.recordingCameraPreview.get();
        }
    }

    private static void fillMenu(UIContext context, ContextMenuManager menu)
    {
        if (isLumenAvailable())
        {
            menu.action(lumen ? Icons.VISIBLE : Icons.INVISIBLE, FilmVisibilityUIKeys.LUMEN, lumen,
                () -> toggleAndReopen(context, () -> setLumenVisible(!lumen)));
        }

        if (isIrlAvailable())
        {
            menu.action(irl ? Icons.VISIBLE : Icons.INVISIBLE, FilmVisibilityUIKeys.IRL, irl,
                () -> toggleAndReopen(context, () -> setIrlVisible(!irl)));
        }

        if (isVfxAvailable())
        {
            menu.action(vfx ? Icons.VISIBLE : Icons.INVISIBLE, FilmVisibilityUIKeys.VFX, vfx,
                () -> toggleAndReopen(context, () -> setVfxVisible(!vfx)));
        }

        menu.action(cameraReference ? Icons.VISIBLE : Icons.INVISIBLE, FilmVisibilityUIKeys.CAMERA_REFERENCE,
            cameraReference, () -> toggleAndReopen(context,
                () -> setCameraReferenceVisible(!cameraReference)));
        menu.action(ik ? Icons.VISIBLE : Icons.INVISIBLE, FilmVisibilityUIKeys.IK, ik,
            () -> toggleAndReopen(context, () -> setIkVisible(!ik)));
        menu.action(disguise ? Icons.VISIBLE : Icons.INVISIBLE, FilmVisibilityUIKeys.DISGUISE, disguise,
            () -> toggleAndReopen(context, () -> setDisguiseVisible(!disguise)));
    }

    private static void toggleAndReopen(UIContext context, Runnable toggle)
    {
        toggle.run();
        openMenu(context);
    }

    private static boolean isFilmPanelActive()
    {
        UIDashboard dashboard = mchorse.bbs_mod.BBSModClient.getDashboardIfCreated();

        return dashboard != null && UIScreen.getCurrentMenu() == dashboard
            && dashboard.getPanels().panel instanceof UIFilmPanel;
    }

    /** True when the current film panel is locked and the opt-in hard lock is enabled. */
    public static boolean preventsLockedLayoutResizing(UIFilmPanel panel)
    {
        return panel != null && panel.dock != null && panel.dock.isLocked()
            && gbeic.bbsplusplus.settings.CMLSettings.lockedLayoutPreventsResizing != null
            && gbeic.bbsplusplus.settings.CMLSettings.lockedLayoutPreventsResizing.get();
    }

    /** Same guard for a dock: applies when the dock belongs to a film panel (2.5 shared UIDockLayout). */
    public static boolean preventsLockedLayoutResizing(mchorse.bbs_mod.ui.framework.elements.layout.UIDockLayout dock)
    {
        if (dock == null
            || gbeic.bbsplusplus.settings.CMLSettings.lockedLayoutPreventsResizing == null
            || !gbeic.bbsplusplus.settings.CMLSettings.lockedLayoutPreventsResizing.get())
        {
            return false;
        }

        mchorse.bbs_mod.ui.framework.elements.UIElement parent = dock;

        while (parent != null)
        {
            if (parent instanceof UIFilmPanel film)
            {
                return preventsLockedLayoutResizing(film);
            }

            parent = parent.getParent();
        }

        return false;
    }
}
