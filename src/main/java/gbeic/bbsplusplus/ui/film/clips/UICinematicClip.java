package gbeic.bbsplusplus.ui.film.clips;

import gbeic.bbsplusplus.clips.screen.CinematicClip;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

public class UICinematicClip extends UIClip<CinematicClip>
{
    public UIButton edit;
    public UIKeyframeEditor keyframes;

    public UICinematicClip(CinematicClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.keyframes = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes.view.duration(() -> this.clip.duration.get());
        this.keyframes.setUndoId("cinematic_keyframes");

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

        this.panels.add(UI.column(UI.label(UIKeys.CAMERA_PANELS_KEYFRAMES), this.edit).marginTop(6));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        UIKeyframes view = this.keyframes.view;
        view.removeAllSheets();

        /* 批量添加所有通道为独立 sheet，并附中文标题（同 UIHotbarClip）。 */
        KeyframeChannel[] channels = this.clip.channels;

        for (int i = 0; i < channels.length; i++)
        {
            KeyframeChannel channel = channels[i];

            view.addSheet(new UIKeyframeSheet(channel.getId(), ClipTrackTitles.titleOf(channel),
                UIKeyframeEditor.COLORS[i % UIKeyframeEditor.COLORS.length], false, channel, null));
        }

        view.getGraph().clearSelection();
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        if (data.getString("embed").equals("cinematic_keyframes"))
        {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
        }
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        if (this.keyframes.hasParent())
        {
            data.putString("embed", "cinematic_keyframes");
        }
    }
}
