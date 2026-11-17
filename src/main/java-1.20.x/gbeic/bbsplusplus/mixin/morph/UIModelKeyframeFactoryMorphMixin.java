package gbeic.bbsplusplus.mixin.morph;

import gbeic.bbsplusplus.client.morph.ModelMorphData;
import gbeic.bbsplusplus.client.morph.ModelMorphKeyframeTargets;
import gbeic.bbsplusplus.client.ui.morph.UICubeMappingEditorPanel;
import gbeic.bbsplusplus.keyframes.IModelMorphKeyframe;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIModelKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 BBS 原生 Model 关键帧面板上增加 Morph 开关与映射编辑器入口。
 *
 * <p>注入目标是 {@link UIModelKeyframeFactory} 构造函数尾部：原版面板只有模型选择按钮，
 * 这里追加形变状态、下一关键帧模型、纹理同步开关与「编辑映射」按钮。</p>
 */
@Mixin(value = UIModelKeyframeFactory.class, remap = false)
public abstract class UIModelKeyframeFactoryMorphMixin extends UIKeyframeFactory<String>
{
    public UIModelKeyframeFactoryMorphMixin(Keyframe<String> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);
    }

    @Inject(method = "<init>", at = @At("TAIL"), require = 1)
    private void bbsppp$addModelMorphControls(Keyframe<String> keyframe, UIKeyframes editor, CallbackInfo ci)
    {
        if (!bbsppp$isModelTrack(keyframe) || !(keyframe instanceof IModelMorphKeyframe morphKeyframe))
        {
            return;
        }

        ModelForm form = bbsppp$findModelForm(editor, keyframe);

        if (form == null)
        {
            return;
        }

        ModelMorphData data = morphKeyframe.bbsppp$getModelMorph();
        UIToggle enabled = new UIToggle(L10n.lang("bbsppp.ui.keyframes.model_morph"), (button) ->
        {
            bbsppp$mutate(editor, form, keyframe, current ->
            {
                current.setEnabled(button.getValue());

                if (button.getValue())
                {
                    bbsppp$ensureModels(current, form, keyframe);
                }
            });

        });

        enabled.setValue(data != null && data.enabled());
        enabled.tooltip(L10n.lang("bbsppp.ui.keyframes.model_morph.tooltip"));

        UIToggle textureSync = new UIToggle(L10n.lang("bbsppp.ui.keyframes.model_morph_texture_sync"), (button) ->
        {
            bbsppp$mutate(editor, form, keyframe, current -> current.setTextureSync(button.getValue()));
        });

        textureSync.setValue(data == null || data.textureSync());
        textureSync.tooltip(L10n.lang("bbsppp.ui.keyframes.model_morph_texture_sync.tooltip"));

        UIButton edit = new UIButton(L10n.lang("bbsppp.ui.keyframes.model_morph_edit"), (button) ->
        {
            ModelMorphData current = morphKeyframe.bbsppp$getModelMorph();
            final ModelMorphData working = current == null ? new ModelMorphData() : current.copy();

            bbsppp$ensureModels(working, form, keyframe);

            String sourceId = bbsppp$modelId(keyframe);
            String targetId = bbsppp$nextModelId(keyframe);

            UICubeMappingEditorPanel.open(working, sourceId, targetId, () ->
            {
                bbsppp$apply(editor, form, keyframe, working.copy());
            });
        });

        edit.tooltip(L10n.lang("bbsppp.ui.keyframes.model_morph_edit.tooltip"));

        this.scroll.add(
            enabled,
            textureSync,
            edit
        );
    }

    private static void bbsppp$ensureModels(ModelMorphData data, ModelForm form, Keyframe<?> keyframe)
    {
        if (data.sourceModel().isEmpty())
        {
            data.setSourceModel(bbsppp$modelId(keyframe));
        }

        if (data.targetModel().isEmpty() && form != null)
        {
            data.setTargetModel(bbsppp$nextModelId(keyframe));
        }
    }

    private static void bbsppp$mutate(UIKeyframes editor, ModelForm form, Keyframe<?> keyframe,
                                      java.util.function.Consumer<ModelMorphData> mutator)
    {
        ModelMorphData current = keyframe instanceof IModelMorphKeyframe morph
            ? morph.bbsppp$getModelMorph() : null;

        current = current == null ? new ModelMorphData() : current.copy();
        mutator.accept(current);
        bbsppp$apply(editor, form, keyframe, current);
    }

    private static void bbsppp$apply(UIKeyframes editor, ModelForm form, Keyframe<?> keyframe, ModelMorphData data)
    {
        editor.cacheKeyframes();

        for (UIKeyframeSheet sheet : editor.getGraph().getSheets())
        {
            if (!bbsppp$isModelSheet(sheet, form))
            {
                continue;
            }

            for (Keyframe<?> selected : sheet.selection.getSelected())
            {
                if (selected instanceof IModelMorphKeyframe morph)
                {
                    morph.bbsppp$setModelMorph(data);
                }
            }
        }

        editor.submitKeyframes();
    }

    private static boolean bbsppp$isModelTrack(Keyframe<?> keyframe)
    {
        return keyframe.getParent() != null && bbsppp$isModelTrackId(keyframe.getParent().getId());
    }

    private static boolean bbsppp$isModelTrackId(String id)
    {
        return "model".equals(id) || (id != null && id.endsWith("/model"));
    }

    private static boolean bbsppp$isModelSheet(UIKeyframeSheet sheet, ModelForm form)
    {
        if (sheet == null || !bbsppp$isModelTrackId(sheet.channel.getId()))
        {
            return false;
        }

        Form sheetForm = sheet.property == null ? sheet.form : FormUtils.getForm(sheet.property);

        return sheetForm == form;
    }

    private static ModelForm bbsppp$findModelForm(UIKeyframes editor, Keyframe<?> keyframe)
    {
        for (UIKeyframeSheet sheet : editor.getGraph().getSheets())
        {
            if (sheet.channel != keyframe.getParent())
            {
                continue;
            }

            Form form = sheet.property == null ? sheet.form : FormUtils.getForm(sheet.property);

            return form instanceof ModelForm modelForm ? modelForm : null;
        }

        return null;
    }

    private static String bbsppp$modelId(Keyframe<?> keyframe)
    {
        Object value = keyframe.getValue();

        return value instanceof String string ? string : "";
    }

    /**
     * 下一个有实际时间间隔的 Model 关键帧模型 ID。
     *
     * <p>同模型 ID 也必须保留：它可以表示同一模型内部的 Cube 重组映射。
     * 这里只跳过同 tick 的并列关键帧，因为它们没有可播放的时间区间。</p>
     */
    private static String bbsppp$nextModelId(Keyframe<?> keyframe)
    {
        return ModelMorphKeyframeTargets.nextModelId(keyframe);
    }
}
