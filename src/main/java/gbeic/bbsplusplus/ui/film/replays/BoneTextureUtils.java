package gbeic.bbsplusplus.ui.film.replays;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.utils.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for bone texture related operations in the replay editor.
 */
public class BoneTextureUtils {
    /**
     * Collects bone order from a model.
     */
    public static List<Pair<String, Integer>> collectBoneOrder(IModel model) {
        List<Pair<String, Integer>> orderedBones = new ArrayList<>();

        if (model instanceof Model cubicModel) {
            collectBonesFromGroups(cubicModel.topGroups, 0, orderedBones);
        } else {
            Collection<BOBJBone> bones = model.getAllBOBJBones();

            if (bones != null && !bones.isEmpty()) {
                for (BOBJBone bone : bones) {
                    orderedBones.add(new Pair<>(bone.name, 0));
                }
            }
        }

        return orderedBones;
    }

    /**
     * Collects bones from model groups recursively.
     */
    private static void collectBonesFromGroups(List<ModelGroup> groups, int level, List<Pair<String, Integer>> orderedBones) {
        for (ModelGroup group : groups) {
            orderedBones.add(new Pair<>(group.id, level));

            if (!group.children.isEmpty()) {
                collectBonesFromGroups(group.children, level + 1, orderedBones);
            }
        }
    }

    /**
     * Collects bone parents from a model.
     */
    public static Map<String, String> collectBoneParents(IModel model) {
        Map<String, String> parentByBone = new HashMap<>();

        if (model instanceof Model cubicModel) {
            collectBoneParentsFromGroups(cubicModel.topGroups, null, parentByBone);
        } else {
            Collection<BOBJBone> bones = model.getAllBOBJBones();

            if (bones != null && !bones.isEmpty()) {
                for (BOBJBone bone : bones) {
                    if (bone.parentBone != null) {
                        parentByBone.put(bone.name, bone.parentBone.name);
                    }
                }
            }
        }

        return parentByBone;
    }

    /**
     * Collects bone parents from model groups recursively.
     */
    private static void collectBoneParentsFromGroups(List<ModelGroup> groups, String parent, Map<String, String> parentByBone) {
        for (ModelGroup group : groups) {
            if (parent != null) {
                parentByBone.put(group.id, parent);
            }

            if (!group.children.isEmpty()) {
                collectBoneParentsFromGroups(group.children, group.id, parentByBone);
            }
        }
    }

    /**
     * Collects children from parent map.
     */
    public static Map<String, List<String>> collectChildren(Map<String, String> parentByBone) {
        Map<String, List<String>> childrenByBone = new HashMap<>();

        for (Map.Entry<String, String> entry : parentByBone.entrySet()) {
            childrenByBone.computeIfAbsent(entry.getValue(), (key) -> new ArrayList<>()).add(entry.getKey());
        }

        return childrenByBone;
    }

    /**
     * Gets model instance from a model form.
     */
    public static ModelInstance getModel(ModelForm modelForm) {
        return ModelFormRenderer.getModel(modelForm);
    }
}
