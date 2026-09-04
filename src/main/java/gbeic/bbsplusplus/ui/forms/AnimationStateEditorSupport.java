package gbeic.bbsplusplus.ui.forms;

import gbeic.bbsplusplus.api.AnimationStateLayoutHost;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.film.replays.PerLimbService;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.states.AnimationState;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.forms.editors.states.keyframes.UIAnimationStateEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.pose.PoseBones;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** 动画状态 Pose/IK 时间线业务，Mixin 只负责调用时机。 */
public class AnimationStateEditorSupport
{
    private final UIAnimationStateEditor stateEditor;
    private final UIFormEditor editor;
    private final Supplier<UIKeyframeEditor> keyframeEditorSupplier;
    private final Supplier<AnimationState> stateSupplier;
    private final Map<AnimationState, Set<String>> expandedPoseTabs = new IdentityHashMap<>();

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

    public void rememberExpandedPoseTabs(AnimationState nextState)
    {
        if (this.stateSupplier.get() != null && this.keyframeEditorSupplier.get() != null)
        {
            this.expandedPoseTabs.put(
                this.stateSupplier.get(),
                new HashSet<>(this.keyframeEditorSupplier.get().view.getDopeSheet().getExpandedPoseTabIds())
            );
        }

        if (this.editor instanceof AnimationStateLayoutHost host)
        {
            host.bbspp_cml$prepareStateKeyframeEditor();
        }
    }

    public void addStateIKSheets(List<UIKeyframeSheet> sheets, Form form)
    {
        if (this.stateSupplier.get() != null && form instanceof ModelForm modelForm)
        {
            UIReplaysEditorUtils.addIKControlSheet(modelForm, this.stateSupplier.get().properties, sheets);
            UIReplaysEditorUtils.addIKTargetSheets(modelForm, this.stateSupplier.get().properties, sheets);
            UIReplaysEditorUtils.addPoleTargetSheets(modelForm, this.stateSupplier.get().properties, sheets);
        }
    }

    public void finishStateKeyframeSetup(AnimationState nextState)
    {
        UIKeyframeEditor currentEditor = this.keyframeEditorSupplier.get();

        if (currentEditor != null && nextState != null)
        {
            this.configurePoseTabs(currentEditor.view, nextState);
            this.addReplayPoseContextActions(currentEditor, nextState);
        }

        if (this.editor instanceof AnimationStateLayoutHost host)
        {
            host.bbspp_cml$attachStateKeyframeEditor(currentEditor);
        }
    }

    private void configurePoseTabs(UIKeyframes view, AnimationState animationState)
    {
        UIKeyframeDopeSheet dopeSheet = view.getDopeSheet();
        Map<String, UIKeyframeSheet> poseByFormPath = new HashMap<>();
        Map<UIKeyframeSheet, List<UIKeyframeSheet>> tabs = new LinkedHashMap<>();
        Map<UIKeyframeSheet, Integer> depths = new HashMap<>();

        for (UIKeyframeSheet sheet : dopeSheet.getSheets())
        {
            String formPath = this.getPoseFormPath(sheet);

            if (formPath != null)
            {
                poseByFormPath.put(formPath, sheet);
                tabs.put(sheet, new ArrayList<>());
            }
        }

        for (UIKeyframeSheet sheet : dopeSheet.getSheets())
        {
            PerLimbService.PoseBonePath path = PerLimbService.parsePoseBonePath(sheet.id);

            if (path == null)
            {
                continue;
            }

            UIKeyframeSheet root = poseByFormPath.get(path.formPath());

            if (root == null)
            {
                continue;
            }

            tabs.get(root).add(sheet);
            depths.put(sheet, this.getBoneDepth(path.formPath(), path.bone()));
        }

        tabs.entrySet().removeIf((entry) -> entry.getValue().isEmpty());
        Set<String> expanded = this.expandedPoseTabs.getOrDefault(
            animationState,
            Collections.emptySet()
        );

        dopeSheet.configurePoseTabs(tabs, depths, expanded);
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

    private int getBoneDepth(String formPath, String bone)
    {
        Form form = formPath.isEmpty() ? this.editor.form : FormUtils.getForm(this.editor.form, formPath);

        if (!(form instanceof ModelForm modelForm))
        {
            return 0;
        }

        ModelInstance model = ModelFormRenderer.getModel(modelForm);

        if (model == null)
        {
            return 0;
        }

        int depth = 0;
        String current = bone;

        while (current != null && !current.isEmpty())
        {
            current = model.model.getParentGroupKey(current);

            if (current != null && !current.isEmpty())
            {
                depth++;
            }
        }

        return depth;
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
                    String boneKey = PerLimbService.toPoseBoneKey(formPath, bone);
                    KeyframeChannel<PoseTransform> limbChannel =
                        (KeyframeChannel<PoseTransform>) animationState.properties.getOrCreate(modelForm, boneKey);

                    if (limbChannel == null)
                    {
                        continue;
                    }

                    PoseTransform transform = pose.get(bone);
                    int index = limbChannel.insert(keyframe.getTick(), (PoseTransform) transform.copy());
                    Keyframe<PoseTransform> limbKeyframe = limbChannel.get(index);

                    this.copyKeyframeMetadata(keyframe, limbKeyframe);
                }
            }
        });
    }

    private void copyKeyframeMetadata(Keyframe<?> source, Keyframe<?> target)
    {
        target.getInterpolation().copy(source.getInterpolation());
        target.setShape(source.getShape());
        target.setColor(source.getColor() == null ? null : source.getColor().copy());
        target.setDuration(source.getDuration());
        target.lx = source.lx;
        target.ly = source.ly;
        target.rx = source.rx;
        target.ry = source.ry;
        target.lx_m = source.lx_m == null ? null : new ArrayList<>(source.lx_m);
        target.ly_m = source.ly_m == null ? null : new ArrayList<>(source.ly_m);
        target.rx_m = source.rx_m == null ? null : new ArrayList<>(source.rx_m);
        target.ry_m = source.ry_m == null ? null : new ArrayList<>(source.ry_m);
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

        BaseValue.edit(animationState.properties, (properties) ->
        {
            for (String controller : controllers)
            {
                this.clearChannel(
                    animationState,
                    PerLimbService.toIKTargetKey(formPath, controller)
                );
            }

            for (String controller : poleControllers)
            {
                this.clearChannel(
                    animationState,
                    PerLimbService.toPoleTargetKey(formPath, controller)
                );
            }
        });
    }

    private void clearChannel(AnimationState animationState, String id)
    {
        KeyframeChannel<?> channel = animationState.properties.properties.get(id);

        if (channel != null)
        {
            channel.removeAll();
        }
    }
}
