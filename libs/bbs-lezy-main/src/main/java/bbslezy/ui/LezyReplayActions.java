package bbslezy.ui;
import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.film.replays.ReplayListEntry;
import mchorse.bbs_mod.ui.film.replays.UIReplayList;
import mchorse.bbs_mod.network.ClientNetwork;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The replay panel features that BBS's UI has no hook for: instant scroll to either end of the
 * list, and the three selections and duplicate modes a mass-produced scene needs.
 *
 * <p>None of the methods here hold state: they read the list, do the work against the film, and
 * hand the list back its own new selection. The list's own {@link UIReplayList#refreshReplayList()}
 * is what rebuilds the rows, so every caller ends in it — the selection is what that method
 * carries over the rebuild, so setting it before the refresh is what keeps the new pick.</p>
 *
 * <p>Selections are set through {@code selection.setAll}, not by mutating the list the caller
 * passed in: {@link UIReplayList#getSelectedReplays()} returns a fresh list every call, and
 * editing that copy moves nothing on screen.</p>
 */
public class LezyReplayActions
{
    /**
     * Every row in the list, the folders with the replays in them: the rows BBS draws are the
     * entries themselves, and a category the duplicates came in is one of them, so picking
     * everything means the category rows come along for whatever the user does to the selection
     * next — removing them, moving them, not only the replays inside.
     */
    public static void selectAll(UIReplayList list)
    {
        list.selection.setAll(list.getList());
        list.refreshReplayList();
    }

    /**
     * Resets film actors and replays without having to exit and re-enter the film editor.
     * Respawn entities to clear death states or displaced actors.
     */
    public static void resetReplays(UIReplayList list, UIFilmPanel panel)
    {
        if (panel != null)
        {
            panel.notifyServer(ActionState.RESTART);
            respawnCast(panel);

            if (panel.getController() != null)
            {
                panel.getController().createEntities();
            }

            panel.setCursor(panel.getCursor());
        }

        list.refreshReplayList();
    }

    /**
     * Asks the server to reconcile its actors, which is the only thing that puts a body back.
     *
     * <p>{@code RESTART} is not that thing here. In the film editor the server takes the rewind
     * path, and a rewind only walks the tick counter and re-poses the actors that are still
     * standing - it never rebuilds the cast. A replay an action clip killed has been dropped from
     * the server's actor map for good, so from then on it is drawn (the client rebuilds its own
     * entities) but has no body behind it: the next Damage clip looks the actor up, gets nothing,
     * and the replay stops dying for the rest of the session.</p>
     *
     * <p>Syncing the film root is what makes the server reconcile, and the dead come back standing
     * at the replay's keyframed position. The value does not have to change for this - the server
     * reacts to the path, not the payload - so the film's data is left exactly as it was.</p>
     */
    private static void respawnCast(UIFilmPanel panel)
    {
        Film film = panel.getData();

        if (film != null)
        {
            ClientNetwork.sendSyncData(film.getId(), film);
        }
    }


    /**
     * Everything that shares a model with the picked replays: the point is a duplicate farm,
     * where the copies are the ones the user wants as a set. Non-model forms fall back to the
     * whole form data, so two replays of the same billboard count as the same thing.
     */
    public static void selectSameModel(UIReplayList list, Film film)
    {
        Set<String> wanted = new HashSet<>();

        for (Replay replay : list.getSelectedReplays())
        {
            String identity = identityOf(replay.form.get());

            if (identity != null)
            {
                wanted.add(identity);
            }
        }

        if (wanted.isEmpty())
        {
            return;
        }

        List<ReplayListEntry> entries = new ArrayList<>();

        for (ReplayListEntry entry : list.getList())
        {
            if (entry.isReplay() && wanted.contains(identityOf(entry.replay.form.get())))
            {
                entries.add(entry);
            }
        }

        list.selection.setAll(entries);
        list.refreshReplayList();
    }

    private static String identityOf(Form form)
    {
        if (form instanceof ModelForm model)
        {
            String modelId = model.model.get();

            return modelId.isEmpty() ? null : modelId;
        }

        return form == null ? null : form.toData().toString();
    }

    /* ---------------------------- duplicate ---------------------------- */

    /**
     * The number asked for is a target for the whole selection, not a per-replay count: asking for
     * 150 with three replays picked gives each one 50, not 150 each, because what the user is
     * counting is the crowd, not the actors in it.
     *
     * <p>Each picked replay gets its own new category, and the original moves into it and counts
     * as the first of its share — so a share of 50 means 49 copies, not 50. A remainder that does
     * not divide evenly goes to the earlier picks, one each, so 150 over four is 38,38,37,37
     * rather than a pile on the last one.</p>
     *
     * @param prefix the category name each replay's share is filed under, numbered per replay
     * @return the last copy made, for the caller to scroll to
     */
    public static Replay duplicateToTotal(Film film, List<Replay> selected, int total)
    {
        if (selected.isEmpty() || total <= 0)
        {
            return null;
        }

        int count = selected.size();
        int base = total / count;
        int remainder = total % count;

        Set<String> used = new HashSet<>();

        for (Replay replay : film.replays.getList())
        {
            used.add(replay.category.get());
        }

        Replay last = null;

        for (int i = 0; i < count; i++)
        {
            /* The original takes one of the share, so it is one fewer copy to make. */
            int share = base + (i < remainder ? 1 : 0);
            Replay source = selected.get(i);
            String category = uniqueCategory(used, source.getName() + " D #");

            source.category.set(category);
            used.add(category);

            for (int copy = 1; copy < share; copy++)
            {
                last = copyReplay(film, source, category);
            }
        }

        return last;
    }

    private static Replay copyReplay(Film film, Replay source, String category)
    {
        Replay copy = film.replays.addReplay();

        copy.copy(source);
        copy.category.set(category);

        return copy;
    }

    private static String uniqueCategory(Set<String> used, String prefix)
    {
        for (int i = 1; i < 10000; i++)
        {
            String candidate = prefix + i;

            if (!used.contains(candidate))
            {
                return candidate;
            }
        }

        return prefix + System.nanoTime();
    }
}
