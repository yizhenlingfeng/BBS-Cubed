package gbeic.bbsplusplus.mixin.morph;

import gbeic.bbsplusplus.client.morph.ModelMorphData;
import gbeic.bbsplusplus.client.morph.ModelMorphKeyframeTargets;
import gbeic.bbsplusplus.client.ui.morph.UICubeMappingEditorPanel;
import gbeic.bbsplusplus.keyframes.IModelMorphKeyframe;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.settings.values.base.BaseValue;
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

import java.util.function.Consumer;

/** 在 BBS 的 Model 关键帧面板上添加 Morph 控件。 */
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
                    bbsppp$ensureModels(current, keyframe);
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
            ModelMorphData working = current == null ? new ModelMorphData() : current.copy();

            bbsppp$ensureModels(working, keyframe);

            String sourceId = bbsppp$modelId(keyframe);
            String targetId = ModelMorphKeyframeTargets.nextModelId(keyframe);

            UICubeMappingEditorPanel.open(working, sourceId, targetId, () ->
            {
                bbsppp$apply(editor, form, working.copy());
            });
        });

        edit.tooltip(L10n.lang("bbsppp.ui.keyframes.model_morph_edit.tooltip"));

        this.scroll.add(enabled, textureSync, edit);
    }

    private static void bbsppp$ensureModels(ModelMorphData data, Keyframe<?> keyframe)
    {
        if (data.sourceModel().isEmpty())
        {
            data.setSourceModel(bbsppp$modelId(keyframe));
        }

        if (data.targetModel().isEmpty())
        {
            data.setTargetModel(ModelMorphKeyframeTargets.nextModelId(keyframe));
        }
    }

    private static void bbsppp$mutate(UIKeyframes editor, ModelForm form, Keyframe<?> keyframe,
                                      Consumer<ModelMorphData> mutator)
    {
        ModelMorphData current = keyframe instanceof IModelMorphKeyframe morph
            ? morph.bbsppp$getModelMorph() : null;

        current = current == null ? new ModelMorphData() : current.copy();
        mutator.accept(current);
        bbsppp$apply(editor, form, current);
    }

    private static void bbsppp$apply(UIKeyframes editor, ModelForm form, ModelMorphData data)
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
        if (sheet == null || sheet.channel == null || !bbsppp$isModelTrackId(sheet.channel.getId()))
        {
            return false;
        }

        Form sheetForm = sheet.property == null ? sheet.form : FormUtils.getForm(sheet.property);

        return sheetForm == form;
    }

    private static ModelForm bbsppp$findModelForm(UIKeyframes editor, Keyframe<?> keyframe)
    {
        BaseValue channel = keyframe.getParent();
        String channelId = channel.getId();
        ModelForm fallback = null;
        boolean ambiguous = false;

        /* Different forms can expose the same track ID in the editor. */
        for (UIKeyframeSheet sheet : editor.getGraph().getSheets())
        {
            if (sheet.channel == null || !channelId.equals(sheet.channel.getId()))
            {
                continue;
            }

            Form form = sheet.property == null ? sheet.form : FormUtils.getForm(sheet.property);

            if (!(form instanceof ModelForm modelForm))
            {
                continue;
            }

            if (sheet.channel == channel)
            {
                return modelForm;
            }

            ambiguous |= fallback != null && fallback != modelForm;
            fallback = modelForm;
        }

        return ambiguous ? null : fallback;
    }

    private static String bbsppp$modelId(Keyframe<?> keyframe)
    {
        Object value = keyframe.getValue();

        return value instanceof String string ? string : "";
    }
}
