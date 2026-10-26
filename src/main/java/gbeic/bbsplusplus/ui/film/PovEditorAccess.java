package gbeic.bbsplusplus.ui.film;

import gbeic.bbsplusplus.client.ClientFeatures;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflection bridge to the optional BBS-POV addon.
 *
 * <p>BBS-POV is a compile-time-unknown dependency: its editor and action bar icon
 * are exposed through {@code bbsPov$getEditor()} and the private
 * {@code bbsPov$openEditor} field which its mixin merges into {@link UIFilmPanel}.
 * The bridge resolves both lazily and remembers when the addon is absent.</p>
 */
public final class PovEditorAccess
{
    private static Method editorMethod;
    private static Field iconField;
    private static Method timelineVisibilityMethod;
    private static boolean editorMissing;
    private static boolean iconMissing;

    private PovEditorAccess()
    {}

    public static UIElement editor(UIFilmPanel panel)
    {
        if (editorMissing || panel == null)
        {
            return null;
        }

        if (editorMethod == null)
        {
            try
            {
                editorMethod = panel.getClass().getMethod("bbsPov$getEditor");
            }
            catch (NoSuchMethodException e)
            {
                editorMissing = true;
                ClientFeatures.LOGGER.warn("[POV] bbsPov$getEditor not found (BBS-POV addon missing or not mixed in)");

                return null;
            }
        }

        try
        {
            Object value = editorMethod.invoke(panel);

            return value instanceof UIElement ? (UIElement) value : null;
        }
        catch (ReflectiveOperationException e)
        {
            return null;
        }
    }

    public static UIIcon icon(UIFilmPanel panel)
    {
        if (iconMissing || panel == null)
        {
            return null;
        }

        if (iconField == null)
        {
            try
            {
                iconField = panel.getClass().getDeclaredField("bbsPov$openEditor");
                iconField.setAccessible(true);
            }
            catch (NoSuchFieldException e)
            {
                iconMissing = true;
                ClientFeatures.LOGGER.warn("[POV] bbsPov$openEditor not found (BBS-POV addon missing or not mixed in)");

                return null;
            }
        }

        try
        {
            Object value = iconField.get(panel);

            return value instanceof UIIcon ? (UIIcon) value : null;
        }
        catch (IllegalAccessException e)
        {
            return null;
        }
    }

    /** Calls the POV editor's public {@code setTimelineVisible(boolean)} without a compile-time type. */
    public static void setTimelineVisible(UIElement editor, boolean visible)
    {
        if (editor == null)
        {
            return;
        }

        if (timelineVisibilityMethod == null)
        {
            try
            {
                timelineVisibilityMethod = editor.getClass().getMethod("setTimelineVisible", boolean.class);
            }
            catch (NoSuchMethodException e)
            {
                return;
            }
        }

        try
        {
            timelineVisibilityMethod.invoke(editor, visible);
        }
        catch (ReflectiveOperationException ignored)
        {
            /* Editor state changed under us; visibility is best-effort. */
        }
    }
}
