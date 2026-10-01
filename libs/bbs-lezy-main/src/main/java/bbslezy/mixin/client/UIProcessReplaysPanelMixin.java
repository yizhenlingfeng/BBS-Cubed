package bbslezy.mixin.client;

import bbslezy.ui.LezyLookAt;
import bbslezy.ui.UIBakingProgressOverlayPanel;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.ReplayBatchProcessor;
import mchorse.bbs_mod.ui.film.replays.UIProcessReplaysPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Inserts look-at rotation keyframes on the current playback playhead tick
 * instead of rewriting the entire channel. Allows stacking multiple look-at
 * keyframes at different ticks across multiple replays.
 * For large selections (> 10 replays), processes asynchronously on a background
 * thread with a smooth progress bar overlay.
 */
@Mixin(value = UIProcessReplaysPanel.class, remap = false)
public abstract class UIProcessReplaysPanelMixin
{
    @Mutable
    @Final
    @Shadow
    private UIFilmPanel filmPanel;

    @Unique
    private int bbslezy$cursorTick;

    @Unique
    private boolean bbslezy$ranAsync;

    /**
     * Assign filmPanel immediately after super() before field initializers run,
     * preventing NPE in UINormalProcessView when PROCESS_STATE.operation == LOOK_AT.
     */
    @Inject(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/ui/framework/elements/overlay/UIConfirmOverlayPanel;<init>(Lmchorse/bbs_mod/l10n/keys/IKey;Lmchorse/bbs_mod/l10n/keys/IKey;Ljava/util/function/Consumer;)V",
            shift = At.Shift.AFTER
        )
    )
    private void bbslezy$initEarly(UIFilmPanel filmPanel, List replays, CallbackInfo ci)
    {
        this.filmPanel = filmPanel;
        this.bbslezy$cursorTick = filmPanel != null ? filmPanel.getCursor() : 0;
        this.bbslezy$ranAsync = false;
    }

    @Redirect(
        method = "applyNormal",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/ui/film/replays/ReplayBatchProcessor;applyNormal(Ljava/util/List;Ljava/util/List;Lmchorse/bbs_mod/ui/film/replays/ReplayBatchProcessor$Operation;Lmchorse/bbs_mod/ui/film/replays/ReplayBatchProcessor$NormalParams;)Lmchorse/bbs_mod/ui/film/replays/ReplayBatchProcessor$Error;"
        )
    )
    private ReplayBatchProcessor.Error bbslezy$lookAtAtCursor(
        List<ReplayBatchProcessor.VisibleReplay> selected,
        List<String> properties,
        ReplayBatchProcessor.Operation operation,
        ReplayBatchProcessor.NormalParams params)
    {
        if (operation != ReplayBatchProcessor.Operation.LOOK_AT)
        {
            this.bbslezy$ranAsync = false;
            return ReplayBatchProcessor.applyNormal(selected, properties, operation, params);
        }

        if (params.lookAtTarget == null)
        {
            return ReplayBatchProcessor.Error.NEED_TARGET;
        }

        int tick = this.filmPanel != null ? this.filmPanel.getCursor() : this.bbslezy$cursorTick;

        if (selected.size() > 5)
        {
            this.bbslezy$ranAsync = true;
            UIContext context = ((UIProcessReplaysPanel) (Object) this).getContext();
            UIBakingProgressOverlayPanel progressPanel = new UIBakingProgressOverlayPanel();

            if (context != null)
            {
                UIOverlay.addOverlay(context, progressPanel, 260, 80);
            }

            LezyLookAt.lookAtAsync(selected, params.lookAtTarget, tick, properties, progressPanel, this.filmPanel);

            return null;
        }

        this.bbslezy$ranAsync = false;
        return LezyLookAt.lookAt(selected, params.lookAtTarget, tick, properties, this.filmPanel);
    }

    @Redirect(
        method = "apply",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/film/BaseFilmController;createEntities()V"
        )
    )
    private void bbslezy$postponeCreateEntities(BaseFilmController controller)
    {
        if (!this.bbslezy$ranAsync)
        {
            controller.createEntities();
        }
    }

    @Redirect(
        method = "apply",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/ui/film/replays/UIReplaysEditor;updateChannelsList()V"
        )
    )
    private void bbslezy$postponeUpdateChannels(UIReplaysEditor editor)
    {
        if (!this.bbslezy$ranAsync)
        {
            editor.updateChannelsList();
        }
    }
}
