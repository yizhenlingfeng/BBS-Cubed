package gbeic.bbsplusplus.mixin.client.compat;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Keeps the local replay selection stable while Together applies a remote
 * replay-list structure update. Together 1.6.10 snapshots the old list with
 * shallow references, which can be mutated by list deserialization before its
 * index remapping runs and make the selection advance to the following replay.
 */
@Pseudo
@Mixin(targets = "mchorse.bbs_together.network.TogetherClientNetwork", priority = 500, remap = false)
public abstract class TogetherClientNetworkReplaySelectionMixin
{
    private static final ThreadLocal<SelectionSnapshot> bbspp_cml$SELECTION = new ThreadLocal<>();

    @Inject(method = "lambda$handleFilmDataSyncPacket$47", at = @At("HEAD"), require = 0, remap = false)
    private static void bbspp_cml$captureReplaySelection(String filmId, List<String> path, BaseType data, CallbackInfo ci)
    {
        bbspp_cml$SELECTION.remove();

        if (!bbspp_cml$isReplayStructurePath(path))
        {
            return;
        }

        UIFilmPanel panel = bbspp_cml$getFilmPanel(filmId);

        if (panel == null || panel.replayEditor == null)
        {
            return;
        }

        Film film = panel.getData();
        Replay selected = panel.replayEditor.getReplay();
        List<Replay> replays = film.replays.getList();
        int index = bbspp_cml$identityIndex(replays, selected);

        if (index < 0)
        {
            return;
        }

        String signature = bbspp_cml$signature(selected);
        int ordinal = 0;

        for (int i = 0; i < index; i++)
        {
            if (signature.equals(bbspp_cml$signature(replays.get(i))))
            {
                ordinal += 1;
            }
        }

        bbspp_cml$SELECTION.set(new SelectionSnapshot(filmId, selected, signature, ordinal, index));
    }

    @Inject(method = "lambda$handleFilmDataSyncPacket$47", at = @At("TAIL"), require = 0, remap = false)
    private static void bbspp_cml$restoreReplaySelection(String filmId, List<String> path, BaseType data, CallbackInfo ci)
    {
        SelectionSnapshot snapshot = bbspp_cml$SELECTION.get();

        bbspp_cml$SELECTION.remove();

        if (snapshot == null || !snapshot.filmId.equals(filmId))
        {
            return;
        }

        UIFilmPanel panel = bbspp_cml$getFilmPanel(filmId);

        if (panel == null || panel.replayEditor == null)
        {
            return;
        }

        List<Replay> replays = ((Film) panel.getData()).replays.getList();
        Replay selected = bbspp_cml$findReplay(replays, snapshot);

        if (selected == null)
        {
            if (replays.isEmpty())
            {
                panel.replayEditor.setReplay(null);
            }

            return;
        }

        panel.replayEditor.setReplay(selected);

        if (panel.replayEditor.replaysList != null)
        {
            UIReplayList list = panel.replayEditor.replaysList.replays;

            if (list != null)
            {
                list.scrollToReplay(selected);
            }
        }
    }

    private static Replay bbspp_cml$findReplay(List<Replay> replays, SelectionSnapshot snapshot)
    {
        int identity = bbspp_cml$identityIndex(replays, snapshot.reference);

        if (identity >= 0)
        {
            return replays.get(identity);
        }

        int ordinal = 0;

        for (Replay replay : replays)
        {
            if (snapshot.signature.equals(bbspp_cml$signature(replay)))
            {
                if (ordinal == snapshot.ordinal)
                {
                    return replay;
                }

                ordinal += 1;
            }
        }

        return replays.isEmpty() ? null : replays.get(Math.min(snapshot.index, replays.size() - 1));
    }

    private static UIFilmPanel bbspp_cml$getFilmPanel(String filmId)
    {
        UIDashboard dashboard = BBSModClient.getDashboardIfCreated();
        UIFilmPanel panel = dashboard == null ? null : dashboard.getPanel(UIFilmPanel.class);

        if (panel == null || !panel.isVisible() || panel.getData() == null)
        {
            return null;
        }

        Film film = panel.getData();

        return filmId != null && filmId.equals(film.getId()) ? panel : null;
    }

    private static boolean bbspp_cml$isReplayStructurePath(List<String> path)
    {
        if (path == null)
        {
            return false;
        }

        for (int i = 0; i < path.size(); i++)
        {
            if ("replays".equals(path.get(i)))
            {
                return path.size() <= i + 2;
            }
        }

        return false;
    }

    private static int bbspp_cml$identityIndex(List<Replay> replays, Replay replay)
    {
        if (replay != null)
        {
            for (int i = 0; i < replays.size(); i++)
            {
                if (replays.get(i) == replay)
                {
                    return i;
                }
            }
        }

        return -1;
    }

    private static String bbspp_cml$signature(Replay replay)
    {
        return replay == null ? "" : replay.toData().toString();
    }

    private static class SelectionSnapshot
    {
        public final String filmId;
        public final Replay reference;
        public final String signature;
        public final int ordinal;
        public final int index;

        public SelectionSnapshot(String filmId, Replay reference, String signature, int ordinal, int index)
        {
            this.filmId = filmId;
            this.reference = reference;
            this.signature = signature;
            this.ordinal = ordinal;
            this.index = index;
        }
    }
}
