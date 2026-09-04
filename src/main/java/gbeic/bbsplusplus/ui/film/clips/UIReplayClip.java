package gbeic.bbsplusplus.ui.film.clips;

import gbeic.bbsplusplus.clips.ReplayClip;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;

public class UIReplayClip extends UIClip<ReplayClip>
{
    public UIToggle reverse;
    public UITrackpad speed;
    public UITrackpad propagationRange;

    public UIReplayClip(ReplayClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        this.reverse = new UIToggle(L10n.lang("bbspp.ui.camera.replay.reverse"),
            (button) -> this.editor.editMultiple(this.clip.reverse,
                (value) -> value.set(button.getValue())));

        this.speed = new UITrackpad((number) -> this.editor.editMultiple(this.clip.speed,
            (value) -> value.set(number)));
        this.speed.limit(this.clip.speed).values(0.1D, 0.05D, 1D).increment(1D);
        this.speed.tooltip(L10n.lang("bbspp.ui.camera.replay.speed_tooltip"));

        this.propagationRange = new UITrackpad((number) ->
            this.editor.editMultiple(this.clip.propagationRange, number.intValue()));
        this.propagationRange.integer().limit(this.clip.propagationRange).tooltip(
            L10n.lang("bbspp.ui.camera.replay.propagation_range_tooltip"));
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(
            L10n.lang("bbspp.ui.camera.replay.section"),
            this.reverse,
            UI.column(
                UI.label(L10n.lang("bbspp.ui.camera.replay.speed")),
                this.speed
            ),
            UI.column(
                UI.label(L10n.lang("bbspp.ui.camera.replay.propagation_range")),
                this.propagationRange
            )
        ));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.reverse.setValue(this.clip.reverse.get());
        this.speed.setValue(this.clip.speed.get());
        this.propagationRange.setValue(this.clip.propagationRange.get());
    }
}
