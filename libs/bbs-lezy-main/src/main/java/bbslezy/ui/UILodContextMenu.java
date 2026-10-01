package bbslezy.ui;

import bbslod.LodSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;

public class UILodContextMenu extends UIContextMenu
{
    private final UIElement column;

    public UILodContextMenu()
    {
        UIToggle enabled = new UIToggle(L10n.lang("bbslezy.ui.lod.enabled"), (b) ->
        {
            LodSettings.enabled.set(b.getValue());
        });
        enabled.setValue(LodSettings.enabled.get());

        UITrackpad limit = new UITrackpad((v) ->
        {
            LodSettings.renderLimit.set(v.intValue());
        });
        limit.limit(LodSettings.renderLimit).values(5D).increment(1D).setValue(LodSettings.renderLimit.get());
        limit.tooltip(L10n.lang("bbslezy.config.general.render_limit-comment"));

        UITrackpad focus = new UITrackpad((v) ->
        {
            LodSettings.focusDistance.set(v.doubleValue());
        });
        focus.limit(LodSettings.focusDistance).values(1D).increment(1D).setValue(LodSettings.focusDistance.get());
        focus.tooltip(L10n.lang("bbslezy.config.general.focus_distance-comment"));

        this.column = UI.column(4, 6,
            enabled,
            UI.label(L10n.lang("bbslezy.ui.lod.render_limit")),
            limit,
            UI.label(L10n.lang("bbslezy.ui.lod.focus_distance")),
            focus
        );
        this.column.relative(this).w(150);

        this.add(this.column);
        this.column.resize();
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public void setMouse(UIContext context)
    {
        this.xy(context.mouseX(), context.mouseY())
            .wh(this.column.area.w, this.column.area.h)
            .bounds(context.menu.overlay, 5);
    }
}
