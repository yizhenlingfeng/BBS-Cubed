package gbeic.bbsplusplus.ui.film.replays;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extended UIReplaysEditor with bone texture support from CML edition.
 * Adapted for BBS FS 2.3.1 API.
 */
public class UIReplaysEditorExtended {
    /**
     * Collects limb tracks from a form.
     */
    public static void collectLimbTracks(ModelForm form, Set<String> propertyPaths) {
        if (form == null) {
            return;
        }

        ModelInstance model = ModelFormRenderer.getModel(form);

        if (model != null) {
            String path = FormUtils.getPath(form);
            List<Pair<String, Integer>> orderedBones = BoneTextureUtils.collectBoneOrder(model.model);

            for (Pair<String, Integer> bone : orderedBones) {
                if (bone.a.startsWith("armor_") || bone.a.endsWith("_item")) {
                    continue;
                }

                propertyPaths.add(StringUtils.combinePaths(path, "pose") + ":" + bone.a);
                propertyPaths.add(StringUtils.combinePaths(path, "pose_overlay") + ":" + bone.a);

                for (int i = 0, c = form.additionalOverlays.size(); i < c; i++) {
                    propertyPaths.add(StringUtils.combinePaths(path, "pose_overlay" + i) + ":" + bone.a);
                }
            }
        }
    }

    /**
     * Orders limb tracks based on bone hierarchy.
     */
    public static void orderLimbTracks(ModelForm form, List<UIKeyframeSheet> limbs) {
        if (form == null || limbs.isEmpty()) {
            return;
        }

        ModelInstance model = ModelFormRenderer.getModel(form);

        if (model == null) {
            return;
        }

        List<Pair<String, Integer>> orderedBones = BoneTextureUtils.collectBoneOrder(model.model);

        if (orderedBones.isEmpty()) {
            return;
        }

        Map<String, UIKeyframeSheet> limbByBone = new HashMap<>();

        for (UIKeyframeSheet limb : limbs) {
            int colon = limb.id.indexOf(':');

            if (colon == -1) {
                continue;
            }

            limbByBone.put(limb.id.substring(colon + 1), limb);
        }

        List<UIKeyframeSheet> reordered = new ArrayList<>();

        for (Pair<String, Integer> bone : orderedBones) {
            UIKeyframeSheet limb = limbByBone.get(bone.a);

            if (limb == null) {
                continue;
            }

            reordered.add(limb);
        }

        limbs.clear();
        limbs.addAll(reordered);
    }

    /**
     * Checks if a bone has child tracks.
     */
    public static boolean hasChildTrack(String boneName, Map<String, List<String>> childrenByBone, Map<String, UIKeyframeSheet> limbByBone) {
        List<String> children = childrenByBone.get(boneName);

        if (children == null || children.isEmpty()) {
            return false;
        }

        for (String child : children) {
            if (limbByBone.containsKey(child)) {
                return true;
            }
        }

        return false;
    }
}
