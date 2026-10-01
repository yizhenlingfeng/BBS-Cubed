package bbslezy.mixin.client;

import bbslezy.ui.LezyReplayActions;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.ReplayListEntry;
import mchorse.bbs_mod.ui.film.replays.UIReplayList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UINumberOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Three additions to the replay list's own right-click menu, added after BBS's own so the
 * existing entries are untouched: select everything, select the same model, and a duplicate that
 * counts the whole selection.
 *
 * <p>The list's context consumers are additive and run after BBS's own, so injecting at the tail
 * of the constructor appends rather than replaces — BBS's entries keep their order and behaviour,
 * and a failure here leaves the menu as BBS made it.</p>
 */
@Mixin(value = UIReplayList.class, remap = false)
public class ReplayListMixin
{
    @Shadow
    public UIFilmPanel panel;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void bbslezy$addMenuItems(CallbackInfo ci)
    {
        UIReplayList self = (UIReplayList) (Object) this;

        ((UIList<ReplayListEntry>) (Object) this).context((menu) ->
        {
            Film film = this.panel.getData();

            if (film == null)
            {
                return;
            }

            this.bbslezy$selectionItems(menu, self, film);
        });
    }

    private void bbslezy$selectionItems(ContextMenuManager menu, UIReplayList self, Film film)
    {
        menu.action(Icons.ALL_DIRECTIONS, L10n.lang("bbslezy.ui.replays.select_all"), () ->
        {
            LezyReplayActions.selectAll(self);
        });

        menu.action(Icons.REFRESH, L10n.lang("bbslezy.ui.replays.reset_replay"), () ->
        {
            LezyReplayActions.resetReplays(self, this.panel);
        });

        if (self.getSelectedReplayFirst() == null)
        {
            return;
        }

        menu.action(Icons.MATERIAL, L10n.lang("bbslezy.ui.replays.select_same_model"), () ->
        {
            LezyReplayActions.selectSameModel(self, film);
        });

        menu.action(Icons.DUPE, L10n.lang("bbslezy.ui.replays.duplicate_total"), () ->
        {
            this.bbslezy$openDuplicateTotal(self, film);
        });
    }

    private void bbslezy$openDuplicateTotal(UIReplayList self, Film film)
    {
        UIContext context = self.getContext();

        UINumberOverlayPanel numberPanel = new UINumberOverlayPanel(
            L10n.lang("bbslezy.ui.replays.duplicate_total"),
            L10n.lang("bbslezy.ui.replays.duplicate_total_description"),
            (n) ->
            {
                List<Replay> selected = new ArrayList<>(self.getSelectedReplays());
                Replay last = LezyReplayActions.duplicateToTotal(film, selected, (int) (double) n);

                self.refreshReplayList();

                if (last != null)
                {
                    self.scrollToReplay(last);
                }
            });

        numberPanel.value.limit(1).integer();
        numberPanel.value.setValue(1D);

        UIOverlay.addOverlay(context, numberPanel);
    }
}
