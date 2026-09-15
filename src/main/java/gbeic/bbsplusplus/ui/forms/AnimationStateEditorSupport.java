package gbeic.bbsplusplus.ui.forms;

import gbeic.bbsplusplus.api.AnimationStateLayoutHost;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.states.AnimationState;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.forms.editors.states.keyframes.UIAnimationStateEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.pose.PoseBones;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 动画状态 Pose/IK 时间线业务，Mixin 只负责调用时机。
 * <p>
 * BBS 2.6 起，动画状态关键帧编辑器的轨道行（含骨骼、IK 目标/极向、IK 控制）与折叠树
 * 已由原生 {@code TrackCatalog} + {@code FoldState} 统一构建，因此这里不再自行追加 IK 行、
 * 也不再手动维护姿势分页的展开态；仅保留原生没有的右键能力：把选中 Pose 关键帧烘焙成
 * 逐骨骼肢体轨道、以及一键清空 IK 轨道。
 */
public class AnimationStateEditorSupport
{
    private final UIAnimationStateEditor stateEditor;
    private final UIFormEditor editor;
    private final Supplier<UIKeyframeEditor> keyframeEditorSupplier;
    private final Supplier<AnimationState> stateSupplier;

    public AnimationStateEditorSupport(
        UIAnimationStateEditor stateEditor,
        UIFormEditor editor,
        Supplier<UIKeyframeEditor> keyframeEditorSupplier,
        Supplier<AnimationState> stateSupplier
    )
    {
        this.stateEditor = stateEditor;
        this.editor = editor;
        this.keyframeEditorSupplier = keyframeEditorSupplier;
        this.stateSupplier = stateSupplier;
    }

    /** 切换状态前给自定义布局留出准备时机。 */
    public void beforeSetState()
    {
        if (this.editor instanceof AnimationStateLayoutHost host)
        {
            host.bbspp_cml$prepareStateKeyframeEditor();
        }
    }

    public void finishStateKeyframeSetup(AnimationState nextState)
    {
        UIKeyframeEditor currentEditor = this.keyframeEditorSupplier.get();

        if (currentEditor != null && nextState != null)
        {
            this.addReplayPoseContextActions(currentEditor, nextState);
        }

        if (this.editor instanceof AnimationStateLayoutHost host)
        {
            host.bbspp_cml$attachStateKeyframeEditor(currentEditor);
        }
    }

    private String getPoseFormPath(UIKeyframeSheet sheet)
    {
        if (sheet.channel.getFactory() != KeyframeFactories.POSE || sheet.id.contains("pose_overlay"))
        {
            return null;
        }

        if (sheet.id.equals("pose"))
        {
            return "";
        }

        String suffix = FormUtils.PATH_SEPARATOR + "pose";

        return sheet.id.endsWith(suffix)
            ? sheet.id.substring(0, sheet.id.length() - suffix.length())
            : null;
    }

    private void addReplayPoseContextActions(
        UIKeyframeEditor currentEditor,
        AnimationState animationState
    )
    {
        UIKeyframes view = currentEditor.view;

        view.context((menu) ->
        {
            if (this.keyframeEditorSupplier.get() != currentEditor || this.stateSupplier.get() != animationState)
            {
                return;
            }

            UIKeyframeSheet sheet = view.getGraph().getSheet(this.editor.getContext().mouseY);
            ModelForm rootModel = this.editor.form instanceof ModelForm modelForm ? modelForm : null;
            ModelForm poseModel = this.getPoseModelForm(sheet);

            if (sheet != null
                && poseModel != null
                && poseModel.boneTracks.get()
                && this.getPoseFormPath(sheet) != null
                && sheet.selection.hasAny())
            {
                menu.action(Icons.LIMB, UIKeys.FILM_REPLAY_CONTEXT_POSES_TO_LIMBS, () ->
                {
                    if (this.keyframeEditorSupplier.get() != currentEditor || this.stateSupplier.get() != animationState)
                    {
                        return;
                    }

                    this.posesToLimbTracks(animationState, sheet, poseModel);
                    sheet.selection.removeSelected();
                    this.stateEditor.setState(animationState);
                });
            }

            if (rootModel != null)
            {
                ModelInstance model = ModelFormRenderer.getModel(rootModel);
                List<String> controllers = ModelIKRuntime.getControllers(model);

                if (!controllers.isEmpty())
                {
                    menu.action(Icons.CLOSE, UIKeys.FILM_REPLAY_CONTEXT_CLEAR_IK, () ->
                    {
                        if (this.keyframeEditorSupplier.get() != currentEditor || this.stateSupplier.get() != animationState)
                        {
                            return;
                        }

                        this.clearIKTracks(animationState, rootModel);
                        this.stateEditor.setState(animationState);
                    });
                }
            }
        });
    }

    private ModelForm getPoseModelForm(UIKeyframeSheet sheet)
    {
        String formPath = sheet == null ? null : this.getPoseFormPath(sheet);

        if (formPath == null)
        {
            return null;
        }

        Form form = formPath.isEmpty() ? this.editor.form : FormUtils.getForm(this.editor.form, formPath);

        return form instanceof ModelForm modelForm ? modelForm : null;
    }

    @SuppressWarnings("unchecked")
    private void posesToLimbTracks(
        AnimationState animationState,
        UIKeyframeSheet poseSheet,
        ModelForm modelForm
    )
    {
        String formPath = this.getPoseFormPath(poseSheet);

        if (formPath == null)
        {
            return;
        }

        ModelInstance model = ModelFormRenderer.getModel(modelForm);

        if (model == null)
        {
            return;
        }

        List<String> bones = new ArrayList<>(model.model.getGroupKeysInHierarchyOrder());

        bones.removeIf((bone) -> PoseBones.isHidden(model.getDisabledBones(), bone));
        List<Keyframe<Pose>> selected = (List<Keyframe<Pose>>) (List<?>) poseSheet.selection.getSelected();

        BaseValue.edit(animationState.properties, (properties) ->
        {
            for (Keyframe<Pose> keyframe : selected)
            {
                Pose pose = keyframe.getValue();

                if (pose == null)
                {
                    continue;
                }

                for (String bone : bones)
                {
                    TrackId track = TrackId.bone(formPath, bone);
                    KeyframeChannel<PoseTransform> limbChannel =
                        (KeyframeChannel<PoseTransform>) properties.getOrCreate(modelForm, track);

                    if (limbChannel == null)
                    {
                        continue;
                    }

                    PoseTransform transform = pose.get(bone);
                    int index = limbChannel.insert(keyframe.getTick(), (PoseTransform) transform.copy());
                    Keyframe<PoseTransform> limbKeyframe = limbChannel.get(index);

                    limbKeyframe.copyOverExtra(keyframe);
                }
            }
        });
    }

    private void clearIKTracks(AnimationState animationState, ModelForm modelForm)
    {
        ModelInstance model = ModelFormRenderer.getModel(modelForm);

        if (model == null)
        {
            return;
        }

        String formPath = FormUtils.getPath(modelForm);
        List<String> controllers = ModelIKRuntime.getControllers(model);
        List<String> poleControllers = ModelIKRuntime.getPoleControllers(model);

        BaseValue.edit(animationState.properties, (FormProperties properties) ->
        {
            for (String controller : controllers)
            {
                this.clearChannel(properties, TrackId.ikTarget(formPath, controller));
            }

            for (String controller : poleControllers)
            {
                this.clearChannel(properties, TrackId.poleTarget(formPath, controller));
            }
        });
    }

    private void clearChannel(FormProperties properties, TrackId track)
    {
        KeyframeChannel<?> channel = properties.get(track);

        if (channel != null)
        {
            channel.removeAll();
        }
    }
}
