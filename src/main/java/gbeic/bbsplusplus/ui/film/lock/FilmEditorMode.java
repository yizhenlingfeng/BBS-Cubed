package gbeic.bbsplusplus.ui.film.lock;

/**
 * The three mutually exclusive film editor pages: camera, replay and (optional) POV.
 */
public enum FilmEditorMode
{
    CAMERA("camera"),
    REPLAY("replay"),
    POV("pov");

    private final String id;

    FilmEditorMode(String id)
    {
        this.id = id;
    }

    public String id()
    {
        return this.id;
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
