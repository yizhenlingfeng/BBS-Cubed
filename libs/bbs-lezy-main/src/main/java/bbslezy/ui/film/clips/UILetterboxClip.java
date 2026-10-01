package bbslezy.ui.film.clips;

import bbslezy.camera.clips.screen.LetterboxClip;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

public class UILetterboxClip extends UIClip<LetterboxClip>
{
    public UITrackpad height;
    public UITrackpad width;
    public UITrackpad smoothness;
    public UIColor color;
    public UIButton edit;
    public UIKeyframeEditor keyframes;

    public UILetterboxClip(LetterboxClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.height = this.trackpad(this.clip.height).limit(0D, 1.5D).values(0.01D, 0.05D, 0.1D);
        this.height.tooltip(IKey.raw("Bar thickness (0.48 = standard 2.39:1 cinema scope)"));

        this.width = this.trackpad(this.clip.width).limit(0D, 1D).values(0.01D, 0.05D, 0.1D);
        this.width.tooltip(IKey.raw("Bar width coverage"));

        this.smoothness = this.trackpad(this.clip.smoothness).limit(0D, 1D).values(0.02D, 0.05D, 0.1D);
        this.smoothness.tooltip(IKey.raw("Inner edge gradient feathering (0 = solid hard bars, 1 = full soft fade)"));

        this.color = new UIColor((c) -> this.editor.editMultiple(this.clip.color, (v) -> v.set(Color.rgba(c))));
        this.color.tooltip(IKey.raw("Letterbox bar color (default black)"));

        this.keyframes = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes.view.rulerRenderer((context) ->
        {
            UIReplaysEditor.renderRuler(context, this.keyframes.view, (UIClipsPanel) this.editor, (Clips) this.clip.getParent(), this.clip.tick.get());
        });
        this.keyframes.view.duration(() -> this.clip.duration.get());
        this.keyframes.setUndoId("letterbox_keyframes");

        this.edit = new UIButton(UIKeys.GENERAL_EDIT, (b) ->
        {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
            this.keyframes.view.getGraph().clearSelection();
        });
        this.edit.keys().register(Keys.FORMS_EDIT, () -> this.edit.clickItself());
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(IKey.raw("Bar Size & Shape"), this.height, this.width, this.smoothness));
        this.panels.add(this.section(IKey.raw("Color"), this.color));
        this.panels.add(this.section(IKey.raw("Animate with Keyframes"), this.edit).tooltip(
            IKey.raw("Optional: animate bar height, width, rotation, and color over time")
        ));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.color.setColor(this.clip.color.get().getARGBColor());
        this.keyframes.view.removeAllSheets();

        for (KeyframeChannel<?> channel : this.clip.channels)
        {
            int sheetColor = channel.getId().hashCode() & Colors.RGB;
            this.keyframes.view.addSheet(new UIKeyframeSheet(channel.getId(), IKey.constant(channel.getId()), sheetColor, channel, null));
        }
    }

    @Override
    public void applyUndoData(MapType data)
    {
        if ("letterbox_keyframes".equals(data.getString("embed")))
        {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
        }

        super.applyUndoData(data);
    }

    @Override
    public void collectUndoData(MapType data)
    {
        if (this.keyframes.hasParent())
        {
            data.putString("embed", "letterbox_keyframes");
        }

        super.collectUndoData(data);
    }
}
