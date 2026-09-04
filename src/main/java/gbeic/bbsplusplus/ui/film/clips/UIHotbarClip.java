package gbeic.bbsplusplus.ui.film.clips;

import gbeic.bbsplusplus.clips.HotbarClip;
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
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

public class UIHotbarClip extends UIClip<HotbarClip>
{
    public UIButton edit;
    public UIKeyframeEditor keyframes;

    public UIHotbarClip(HotbarClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.keyframes = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes.view.duration(() -> this.clip.duration.get());
        this.keyframes.setUndoId("hotbar_keyframes");

        this.edit = new UIButton(UIKeys.CAMERA_PANELS_EDIT_KEYFRAMES, (b) ->
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

        this.panels.add(UI.column(UI.label(UIKeys.CAMERA_PANELS_KEYFRAMES), this.edit).marginTop(12));
    }

    @Override
    public void fillData()
    {
        super.fillData();
        this.ensureHardcoreIsBoolean();

        UIKeyframes view = this.keyframes.view;
        view.removeAllSheets();

        /* 批量添加所有通道为独立 sheet —— 旧 setChannel 循环会清空再放单个，最终只剩末尾通道。 */
        KeyframeChannel[] channels = this.clip.channels;

        for (int i = 0; i < channels.length; i++)
        {
            KeyframeChannel channel = channels[i];

            /* 用带 IKey title 的 6 参构造，title 取翻译键 bbs.ui.camera.clips.bbs:<id>，
             * 缺失时 L10n 回退到 key 字符串，至少可见。 */
            view.addSheet(new UIKeyframeSheet(channel.getId(), ClipTrackTitles.titleOf(channel),
                UIKeyframeEditor.COLORS[i % UIKeyframeEditor.COLORS.length], false, channel, null));
        }

        view.getGraph().clearSelection();
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        if ("hotbar".equals(data.getString("embed")))
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
            data.putString("embed", "hotbar");
        }
    }

    private void ensureHardcoreIsBoolean()
    {
        if (this.clip.hardcore.getFactory() == KeyframeFactories.BOOLEAN)
        {
            return;
        }

        MapType data = this.clip.hardcore.toData().asMap();
        data.putString("type", "boolean");
        this.clip.hardcore.fromData(data);
    }
}
