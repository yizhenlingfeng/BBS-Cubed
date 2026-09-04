package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.api.PoseTabStateProvider;
import gbeic.bbsplusplus.pbr.ui.UIBonePBRKeyframeFactory;
import gbeic.bbsplusplus.utils.AnimationInterpInheritance;
import gbeic.bbsplusplus.utils.BonePriority;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.film.replays.PerLimbService;
import mchorse.bbs_mod.ui.film.ICursor;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds bone-track priority selection and preserves imported animation interpolation.
 */
@Mixin(value = UIReplaysEditorUtils.class, remap = false)
public abstract class UIReplaysEditorUtilsMixin
{
    @Inject(
        method = "pickForm(Lmchorse/bbs_mod/ui/framework/elements/input/keyframes/UIKeyframeEditor;Lmchorse/bbs_mod/ui/film/ICursor;Lmchorse/bbs_mod/forms/forms/Form;Ljava/lang/String;Z)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void bbspp_cml$pickPrioritySheetBeforePoseShortcut(UIKeyframeEditor keyframeEditor, ICursor cursor, Form form, String bone, boolean insert, CallbackInfo ci)
    {
        if (insert || form == null || keyframeEditor == null || bone == null || bone.isEmpty())
        {
            return;
        }

        IUIKeyframeGraph graph = keyframeEditor.view.getGraph();
        UIKeyframeSheet target = null;

        if (BonePriority.isExpandedLimbPriorityEnabled())
        {
            String formPath = FormUtils.getPath(form);
            String boneKey = PerLimbService.toPoseBoneKey(formPath, bone);
            UIKeyframeSheet limb = bbspp_cml$findSheet(graph, boneKey);

            if (limb != null
                && keyframeEditor.view.getDopeSheet() instanceof PoseTabStateProvider poseTabs
                && poseTabs.bbspp_cml$isExpandedPoseChild(limb))
            {
                target = limb;
            }
        }

        if (target == null)
        {
            String priority = BonePriority.getPriorityTrack();

            if (priority != null)
            {
                target = bbspp_cml$findSheet(graph, priority);
            }
        }

        if (target == null)
        {
            return;
        }

        KeyframeSegment segment = target.channel.find(cursor.getCursor());
        Keyframe closest = segment == null ? null : segment.getClosest();

        if (closest != null)
        {
            graph.selectKeyframe(closest);
            cursor.setCursor((int) closest.getTick());
        }

        if (keyframeEditor.editor instanceof UIPoseKeyframeFactory poseFactory)
        {
            poseFactory.poseEditor.selectBone(bone);
        }
        else if (keyframeEditor.editor instanceof UIBonePBRKeyframeFactory bonePBRFactory)
        {
            bonePBRFactory.selectBone(bone);
        }

        ci.cancel();
    }

    @Inject(method = "resolveBoneSheet", at = @At("RETURN"), cancellable = true, remap = false)
    private static void bbspp_cml$applyBonePriority(UIKeyframeEditor keyframeEditor, String boneKey, String formPath, CallbackInfoReturnable<UIKeyframeSheet> cir)
    {
        IUIKeyframeGraph graph = keyframeEditor.view.getGraph();

        if (BonePriority.isExpandedLimbPriorityEnabled())
        {
            UIKeyframeSheet limb = bbspp_cml$findSheet(graph, boneKey);

            if (limb != null
                && keyframeEditor.view.getDopeSheet() instanceof PoseTabStateProvider poseTabs
                && poseTabs.bbspp_cml$isExpandedPoseChild(limb))
            {
                cir.setReturnValue(limb);

                return;
            }
        }

        String priority = BonePriority.getPriorityTrack();

        if (priority == null)
        {
            return;
        }

        UIKeyframeSheet sheet = bbspp_cml$findSheet(graph, priority);

        if (sheet != null && sheet != cir.getReturnValue())
        {
            cir.setReturnValue(sheet);
        }
    }

    private static UIKeyframeSheet bbspp_cml$findSheet(IUIKeyframeGraph graph, String id)
    {
        UIKeyframeSheet sheet = graph.getSheet(id);

        if (sheet != null)
        {
            return sheet;
        }

        for (UIKeyframeSheet candidate : graph.getSheets())
        {
            if (candidate.id != null && candidate.id.equalsIgnoreCase(id))
            {
                return candidate;
            }
        }

        return null;
    }

    @Inject(method = "fillAnimationPose", at = @At("TAIL"), remap = false)
    private static void bbspp_cml$inheritInterpolation(UIKeyframeSheet sheet, float i, ModelInstance model, IEntity entity, Animation animation, int current, CallbackInfo ci)
    {
        IInterp inherited = AnimationInterpInheritance.pickInterp(animation, i);

        if (inherited == null)
        {
            return;
        }

        float tick = current + i;

        for (Object object : sheet.channel.getKeyframes())
        {
            Keyframe keyframe = (Keyframe) object;

            if (Math.abs(keyframe.getTick() - tick) < 0.001F)
            {
                keyframe.getInterpolation().setInterp(inherited);

                return;
            }
        }
    }
}
