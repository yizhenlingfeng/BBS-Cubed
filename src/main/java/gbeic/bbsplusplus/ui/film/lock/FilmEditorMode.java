package gbeic.bbsplusplus.ui.film.lock;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * The three mutually exclusive film editor pages: camera, replay and (optional) POV.
 * Each mode owns the action bar icon and the vertical timeline tab that represents it.
 */
public enum FilmEditorMode
{
    CAMERA("camera", Icons.FRUSTUM, FilmEditorLockUIKeys.TIMELINE_CAMERA),
    REPLAY("replay", Icons.SCENE, FilmEditorLockUIKeys.TIMELINE_REPLAY),
    POV("pov", Icons.LOOKING, FilmEditorLockUIKeys.TIMELINE_POV);

    private final String id;
    private final Icon icon;
    private final IKey label;

    FilmEditorMode(String id, Icon icon, IKey label)
    {
        this.id = id;
        this.icon = icon;
        this.label = label;
    }

    public String id()
    {
        return this.id;
    }

    public Icon icon()
    {
        return this.icon;
    }

    public IKey label()
    {
        return this.label;
    }

    public static FilmEditorMode byId(String id)
    {
        if (id != null)
        {
            for (FilmEditorMode mode : values())
            {
                if (mode.id.equals(id))
                {
                    return mode;
                }
            }
        }

        return null;
    }
}
