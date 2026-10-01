package bbslezy.ui.framework.elements.input.keyframes.factories;

import bbslezy.forms.utils.Illusion;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.function.Consumer;

public class UIIllusionKeyframeFactory extends UIKeyframeFactory<Illusion>
{
    private UIToggle enabled;
    private UITrackpad count;
    private UITrackpad spread;
    private UIToggle front;
    private UIToggle back;
    private UIToggle left;
    private UIToggle right;
    private UIToggle up;
    private UIToggle down;
    private UIToggle uniform;
    private UITrackpad spacing;
    private UITrackpad offset;
    private UITrackpad opacity;
    private UIToggle opacityUniform;
    private UIToggle invert;
    private UIToggle gradual;
    private UIToggle gradualInvert;
    private UIPropTransform illusionTransform;
    private UIToggle real;
    private UITrackpad delay;
    private UITrackpad distort;
    private UIToggle distortUniform;
    private UIToggle distortInvert;

    public UIIllusionKeyframeFactory(Keyframe<Illusion> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);
        this.enabled = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_enabled"), (b) -> this.editKeyframe((il) -> il.enabled = b.getValue()));
        this.enabled.tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_enabled-tooltip"));

        this.count = new UITrackpad((v) -> this.editKeyframe((il) -> il.count = v.intValue()));
        this.count.limit(0D).integer().tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_count-tooltip"));
        this.spread = new UITrackpad((v) -> this.editKeyframe((il) -> il.spread = v.floatValue()));
        this.spread.tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_spread-tooltip"));

        this.front = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_front"), (b) -> this.toggleDirection(Illusion.FRONT, b.getValue()));
        this.back = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_back"), (b) -> this.toggleDirection(Illusion.BACK, b.getValue()));
        this.left = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_left"), (b) -> this.toggleDirection(Illusion.LEFT, b.getValue()));
        this.right = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_right"), (b) -> this.toggleDirection(Illusion.RIGHT, b.getValue()));
        this.up = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_up"), (b) -> this.toggleDirection(Illusion.UP, b.getValue()));
        this.down = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_down"), (b) -> this.toggleDirection(Illusion.DOWN, b.getValue()));

        this.uniform = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_uniform"), (b) -> this.editKeyframe((il) -> il.uniform = b.getValue()));
        this.uniform.tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_uniform-tooltip"));

        this.spacing = new UITrackpad((v) -> this.editKeyframe((il) -> il.spacing = v.floatValue()));
        this.spacing.tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_spacing-tooltip"));

        this.offset = new UITrackpad((v) -> this.editKeyframe((il) -> il.offset = v.floatValue()));
        this.offset.tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_offset-tooltip"));

        this.opacity = new UITrackpad((v) -> this.editKeyframe((il) -> il.opacity = v.floatValue() / 100F));
        this.opacity.limit(0D, 100D).tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_opacity-tooltip"));

        this.opacityUniform = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_opacity_uniform"), (b) -> this.editKeyframe((il) -> il.opacityUniform = b.getValue()));
        this.opacityUniform.tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_opacity_uniform-tooltip"));

        this.invert = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_invert"), (b) -> this.editKeyframe((il) -> il.invert = b.getValue()));

        this.real = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_real"), (b) -> this.editKeyframe((il) -> il.real = b.getValue()));
        this.real.tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_real-tooltip"));

        this.delay = new UITrackpad((v) -> this.editKeyframe((il) -> il.delay = v.floatValue()));
        this.delay.limit(0D).tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_delay-tooltip"));

        this.distort = new UITrackpad((v) -> this.editKeyframe((il) -> il.distort = v.floatValue()));
        this.distort.limit(0D, 1D).tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_distort-tooltip"));

        this.distortUniform = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_distort_uniform"), (b) -> this.editKeyframe((il) -> il.distortUniform = b.getValue()));
        this.distortInvert = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_distort_invert"), (b) -> this.editKeyframe((il) -> il.distortInvert = b.getValue()));

        this.gradual = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_gradual"), (b) -> this.editKeyframe((il) -> il.gradual = b.getValue()));
        this.gradual.tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_gradual-tooltip"));

        this.gradualInvert = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_gradual_invert"), (b) -> this.editKeyframe((il) -> il.gradualInvert = b.getValue()));
        this.gradualInvert.tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_gradual_invert-tooltip"));

        this.illusionTransform = new UIPropTransform();
        this.illusionTransform.callbacks(null, () ->
        {
            if (this.keyframe != null && this.keyframe.getValue() != null)
            {
                this.editKeyframe((il) -> {
                    il.transform.copy(this.illusionTransform.getTransform());
                });
            }
        });

        UIElement fields = UI.column(5, 0,
            this.enabled,
            UI.labelRow(L10n.lang("bbslezy.ui.forms.editors.general.illusion_count"), this.count),
            UI.labelRow(L10n.lang("bbslezy.ui.forms.editors.general.illusion_spread"), this.spread),
            UI.row(this.front, this.back),
            UI.row(this.left, this.right),
            UI.row(this.up, this.down),
            this.uniform,
            UI.labelRow(L10n.lang("bbslezy.ui.forms.editors.general.illusion_spacing"), this.spacing),
            UI.labelRow(L10n.lang("bbslezy.ui.forms.editors.general.illusion_offset"), this.offset),
            UI.labelRow(L10n.lang("bbslezy.ui.forms.editors.general.illusion_opacity"), this.opacity),
            UI.row(this.opacityUniform, this.invert),
            UI.labelRow(L10n.lang("bbslezy.ui.forms.editors.general.illusion_delay"), this.delay),
            UI.labelRow(L10n.lang("bbslezy.ui.forms.editors.general.illusion_distort"), this.distort),
            UI.row(this.distortUniform, this.distortInvert),
            this.real,
            this.illusionTransform,
            UI.row(this.gradual, this.gradualInvert)
        );

        this.scroll.add(fields);
        this.updateKeyframe(keyframe);
    }

    private void editKeyframe(Consumer<Illusion> consumer)
    {
        boolean[] applied = {false};

        UIReplaysEditorUtils.forEachSelectedKeyframe(this.editor, this.keyframe, (selected) ->
        {
            applied[0] = true;

            if (selected.getValue() instanceof Illusion illusion)
            {
                Illusion copy = illusion.copy();

                consumer.accept(copy);
                selected.setValue(copy, true);
            }
        });

        if (!applied[0] && this.keyframe != null && this.keyframe.getValue() != null)
        {
            Illusion copy = this.keyframe.getValue().copy();

            consumer.accept(copy);
            this.keyframe.setValue(copy, true);
        }
    }

    private void toggleDirection(int bit, boolean enabled)
    {
        this.editKeyframe((il) -> il.directions = enabled ? il.directions | bit : il.directions & ~bit);
    }

    private void updateKeyframe(Keyframe<Illusion> keyframe)
    {
        if (keyframe == null || keyframe.getValue() == null)
        {
            return;
        }

        Illusion illusion = keyframe.getValue();

        this.enabled.setValue(illusion.enabled);
        this.count.setValue(illusion.count);
        this.spread.setValue(illusion.spread);
        this.front.setValue((illusion.directions & Illusion.FRONT) != 0);
        this.back.setValue((illusion.directions & Illusion.BACK) != 0);
        this.left.setValue((illusion.directions & Illusion.LEFT) != 0);
        this.right.setValue((illusion.directions & Illusion.RIGHT) != 0);
        this.up.setValue((illusion.directions & Illusion.UP) != 0);
        this.down.setValue((illusion.directions & Illusion.DOWN) != 0);
        this.uniform.setValue(illusion.uniform);
        this.spacing.setValue(illusion.spacing);
        this.offset.setValue(illusion.offset);
        this.opacity.setValue(illusion.opacity * 100F);
        this.opacityUniform.setValue(illusion.opacityUniform);
        this.invert.setValue(illusion.invert);
        this.real.setValue(illusion.real);
        this.delay.setValue(illusion.delay);
        this.distort.setValue(illusion.distort);
        this.distortUniform.setValue(illusion.distortUniform);
        this.distortInvert.setValue(illusion.distortInvert);
        this.gradual.setValue(illusion.gradual);
        this.gradualInvert.setValue(illusion.gradualInvert);
        this.illusionTransform.setTransform(illusion.transform);
    }
}
