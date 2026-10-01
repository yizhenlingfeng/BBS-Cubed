package bbslezy.ui.forms.editors.panels;

import bbslezy.forms.utils.Illusion;
import bbslezy.forms.utils.LezyIllusionHelper;
import bbslezy.forms.values.ValueIllusion;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFormPanel;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.function.Consumer;

public class UIIllusionFormPanel extends UIFormPanel<Form>
{
    public UIToggle enabled;
    public UITrackpad count;
    public UITrackpad spread;
    public UIToggle front;
    public UIToggle back;
    public UIToggle left;
    public UIToggle right;
    public UIToggle up;
    public UIToggle down;
    public UIToggle uniform;
    public UITrackpad spacing;
    public UITrackpad offset;
    public UITrackpad opacity;
    public UIToggle opacityUniform;
    public UIToggle invert;
    public UIToggle real;
    public UITrackpad delay;
    public UITrackpad distort;
    public UIToggle distortUniform;
    public UIToggle distortInvert;
    public UIPropTransform illusionTransform;
    public UIToggle gradual;
    public UIToggle gradualInvert;

    public UIIllusionFormPanel(UIForm editor)
    {
        super(editor);
        this.enabled = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_enabled"), (b) -> this.editIllusion((il) -> il.enabled = b.getValue()));
        this.enabled.tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_enabled-tooltip"));

        this.count = new UITrackpad((v) -> this.editIllusion((il) -> il.count = v.intValue()));
        this.count.limit(0D).integer().tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_count-tooltip"));

        this.spread = new UITrackpad((v) -> this.editIllusion((il) -> il.spread = v.floatValue()));
        this.spread.tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_spread-tooltip"));

        this.front = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_front"), (b) -> this.toggleDirection(Illusion.FRONT, b.getValue()));
        this.back = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_back"), (b) -> this.toggleDirection(Illusion.BACK, b.getValue()));
        this.left = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_left"), (b) -> this.toggleDirection(Illusion.LEFT, b.getValue()));
        this.right = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_right"), (b) -> this.toggleDirection(Illusion.RIGHT, b.getValue()));
        this.up = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_up"), (b) -> this.toggleDirection(Illusion.UP, b.getValue()));
        this.down = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_down"), (b) -> this.toggleDirection(Illusion.DOWN, b.getValue()));

        this.uniform = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_uniform"), (b) -> this.editIllusion((il) -> il.uniform = b.getValue()));
        this.uniform.tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_uniform-tooltip"));

        this.spacing = new UITrackpad((v) -> this.editIllusion((il) -> il.spacing = v.floatValue()));
        this.spacing.tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_spacing-tooltip"));

        this.offset = new UITrackpad((v) -> this.editIllusion((il) -> il.offset = v.floatValue()));
        this.offset.tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_offset-tooltip"));

        this.opacity = new UITrackpad((v) -> this.editIllusion((il) -> il.opacity = v.floatValue() / 100F));
        this.opacity.limit(0D, 100D).tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_opacity-tooltip"));

        this.opacityUniform = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_opacity_uniform"), (b) -> this.editIllusion((il) -> il.opacityUniform = b.getValue()));
        this.opacityUniform.tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_opacity_uniform-tooltip"));

        this.invert = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_invert"), (b) -> this.editIllusion((il) -> il.invert = b.getValue()));

        this.real = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_real"), (b) -> this.editIllusion((il) -> il.real = b.getValue()));
        this.real.tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_real-tooltip"));

        this.delay = new UITrackpad((v) -> this.editIllusion((il) -> il.delay = v.floatValue()));
        this.delay.limit(0D).tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_delay-tooltip"));

        this.distort = new UITrackpad((v) -> this.editIllusion((il) -> il.distort = v.floatValue()));
        this.distort.limit(0D, 1D).tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_distort-tooltip"));

        this.distortUniform = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_distort_uniform"), (b) -> this.editIllusion((il) -> il.distortUniform = b.getValue()));
        this.distortInvert = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_distort_invert"), (b) -> this.editIllusion((il) -> il.distortInvert = b.getValue()));
        this.illusionTransform = new UIPropTransform().callbacks(null, () ->
        {
            if (this.form != null)
            {
                Transform t = this.illusionTransform.getTransform();

                LezyIllusionHelper.setIllusionTransform(this.form, t);
            }
        }).barBackground();
        this.illusionTransform.valueBinding(() ->
        {
            Transform t = LezyIllusionHelper.getIllusionTransform(this.form);

            this.illusionTransform.setTransform(t != null ? t : (this.form != null ? LezyIllusionHelper.getIllusion(this.form).transform : null));
        });
        this.gradual = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_gradual"), (b) -> this.editIllusion((il) -> il.gradual = b.getValue()));
        this.gradual.tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_gradual-tooltip"));

        this.gradualInvert = new UIToggle(L10n.lang("bbslezy.ui.forms.editors.general.illusion_gradual_invert"), (b) -> this.editIllusion((il) -> il.gradualInvert = b.getValue()));
        this.gradualInvert.tooltip(L10n.lang("bbslezy.ui.forms.editors.general.illusion_gradual_invert-tooltip"));

        UISection general = new UISection(L10n.lang("bbslezy.ui.forms.editors.general.illusion_section_general"));
        general.fields.add(
            this.enabled,
            UI.labelRow(L10n.lang("bbslezy.ui.forms.editors.general.illusion_count"), this.count),
            UI.labelRow(L10n.lang("bbslezy.ui.forms.editors.general.illusion_spread"), this.spread),
            UI.row(this.front, this.back),
            UI.row(this.left, this.right),
            UI.row(this.up, this.down),
            this.uniform,
            UI.labelRow(L10n.lang("bbslezy.ui.forms.editors.general.illusion_spacing"), this.spacing),
            UI.labelRow(L10n.lang("bbslezy.ui.forms.editors.general.illusion_offset"), this.offset),
            this.real
        );

        UISection appearance = new UISection(L10n.lang("bbslezy.ui.forms.editors.general.illusion_section_appearance"));
        appearance.fields.add(
            UI.labelRow(L10n.lang("bbslezy.ui.forms.editors.general.illusion_opacity"), this.opacity),
            UI.row(this.opacityUniform, this.invert),
            UI.labelRow(L10n.lang("bbslezy.ui.forms.editors.general.illusion_delay"), this.delay),
            UI.labelRow(L10n.lang("bbslezy.ui.forms.editors.general.illusion_distort"), this.distort),
            UI.row(this.distortUniform, this.distortInvert)
        );

        UISection transformSection = new UISection(L10n.lang("bbslezy.ui.forms.editors.general.illusion_section_transform"));
        transformSection.fields.add(
            this.illusionTransform,
            UI.row(this.gradual, this.gradualInvert)
        );

        this.options.add(general, appearance, transformSection);
    }

    private void editIllusion(Consumer<Illusion> consumer)
    {
        if (this.form == null)
        {
            return;
        }

        Illusion illusion = LezyIllusionHelper.getIllusion(this.form);

        if (illusion == null)
        {
            illusion = new Illusion();
        }
        else
        {
            illusion = illusion.copy();
        }

        consumer.accept(illusion);
        LezyIllusionHelper.setIllusion(this.form, illusion);
    }

    private void toggleDirection(int bit, boolean enabled)
    {
        this.editIllusion((il) -> il.directions = enabled ? il.directions | bit : il.directions & ~bit);
    }

    @Override
    public void startEdit(Form form)
    {
        super.startEdit(form);

        Illusion illusion = LezyIllusionHelper.getIllusion(form);

        if (illusion == null)
        {
            illusion = new Illusion();
        }
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

        Transform t = LezyIllusionHelper.getIllusionTransform(form);

        this.illusionTransform.setTransform(t != null ? t : illusion.transform);

        this.options.resize();
    }
}
