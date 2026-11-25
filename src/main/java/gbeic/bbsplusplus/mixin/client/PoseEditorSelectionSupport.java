package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIModelPoseEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import java.util.function.Consumer;

/**
 * 姿势编辑器写入与读取的共享逻辑（D-08）。
 *
 * <p>把原先散落在 {@code UIPoseEditorMixin} 里的「权威 Pose 获取、当前骨骼解析、
 * 写入分派」搬到这里，Mixin 只保留注入点和对本类的薄调用。三条写入路径（伪装编辑器
 * 的 ValuePose、Pose 关键帧宿主的静态 apply、普通 Pose）的判定完全保留原样。</p>
 *
 * <p>本类不持有 {@code UIPoseEditor} 之外的任何状态，也不接触 Mixin 的 UI 字段。</p>
 */
public final class PoseEditorSelectionSupport
{
    private PoseEditorSelectionSupport()
    {
    }

    /** 取当前上可用的权威 Pose：伪装编辑器优先用其 valuePose，否则回落到 editor.getPose()。 */
    public static Pose getAuthoritativePose(UIPoseEditor self)
    {
        if (self instanceof UIModelPoseEditor)
        {
            ValuePose valuePose = ((UIModelPoseEditorAccessor) self).bbspp_cml$getValuePose();

            if (valuePose != null && valuePose.get() != null)
            {
                return valuePose.get();
            }
        }

        return self.getPose();
    }

    /** 取当前选中骨骼的 PoseTransform，未选中或异常时返回 null。 */
    public static PoseTransform getCurrentPoseTransform(UIPoseEditor self)
    {
        String bone = self.getGroup();

        if (bone == null || bone.isEmpty())
        {
            return null;
        }

        try
        {
            Pose pose = getAuthoritativePose(self);

            return pose == null ? null : pose.get(bone);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * 把 writer 应用到一个 PoseTransform，并在必要时同步镜像到编辑器与实况 Pose。
     *
     * <p>三条写入路径：Pose 关键帧宿主走静态 apply；伪装编辑器走 valuePose 的
     * preNotify/postNotify（进 undo）；其余直接写入。未选中骨骼时 no-op。</p>
     */
    public static void writeToSelection(UIPoseEditor self, Consumer<PoseTransform> writer)
    {
        String bone = self.getGroup();

        if (bone == null || bone.isEmpty())
        {
            return;
        }

        if (self instanceof UIPoseKeyframeFactory.UIPoseFactoryEditor)
        {
            UIPoseFactoryEditorAccessor accessor = (UIPoseFactoryEditorAccessor) self;

            UIPoseKeyframeFactory.UIPoseFactoryEditor.apply(accessor.bbspp_cml$getEditor(), accessor.bbspp_cml$getKeyframe(), bone, writer);

            return;
        }

        /* 普通 Pose 没有目录时 Pose.get 返回 null，这里补一个空的 PoseTransform 再写，
         * 与 BBS 在缺少条目时的行为保持一致。 */
        Pose editorPose = self.getPose();
        ValuePose valuePose = self instanceof UIModelPoseEditor
            ? ((UIModelPoseEditorAccessor) self).bbspp_cml$getValuePose()
            : null;
        Pose livePose = valuePose == null ? null : valuePose.get();
        Pose targetPose = livePose != null ? livePose : editorPose;

        if (targetPose == null)
        {
            return;
        }

        if (valuePose != null)
        {
            valuePose.preNotify(IValueListener.FLAG_UNMERGEABLE);
        }

        PoseTransform target = targetPose.get(bone);

        if (target == null)
        {
            target = new PoseTransform();
            targetPose.transforms.put(bone, target);
        }

        writer.accept(target);

        /* 编辑器和权威数据不是同一份时，再写一份，避免视图不同步。 */
        if (editorPose != null && editorPose != targetPose)
        {
            PoseTransform editorTransform = editorPose.get(bone);

            if (editorTransform == null)
            {
                editorTransform = new PoseTransform();
                editorPose.transforms.put(bone, editorTransform);
            }

            writer.accept(editorTransform);
        }

        if (valuePose != null)
        {
            valuePose.postNotify(IValueListener.FLAG_UNMERGEABLE);
        }
    }
}
