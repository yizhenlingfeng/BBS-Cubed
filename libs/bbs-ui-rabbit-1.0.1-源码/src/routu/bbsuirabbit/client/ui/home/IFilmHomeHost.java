package routu.bbsuirabbit.client.ui.home;

/** Implemented by UIEditorDashboardPanel (UIEditorDashboardPanelFilmHomeMixin); the home exists only on the film panel. */
public interface IFilmHomeHost
{
    UIFilmLibraryHome rabbit$getHome();

    /** Whether the home setting changed since the empty tab was last laid out. */
    boolean rabbit$homeOutOfDate();
}
