package gbeic.bbsplusplus.client.ui.pose;

import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 姿势编辑器写入与读取的共享逻辑（D-08）。
 *
 * <p>把原先散落在 {@code UIPoseEditorMixin} 里的「权威 Pose 获取、当前骨骼解析、
 * 写入分派」搬到这里，Mixin 只保留注入点和对本类的薄调用。三条写入路径（伪装编辑器
 * 的 ValuePose、Pose 关键帧宿主的静态 apply、普通 Pose）的判定完全保留原样。</p>
 *
 * <p>本类不持有 {@code UIPoseEditor} 之外的任何状态，也不接触 Mixin 的 UI 字段。
 * 伪装编辑器的 {@code valuePose} 由调用方（Mixin）通过 {@code valuePoseOf} 解析后传入，
 * 这样本类无需直接引用 Mixin 包内的 Accessor 接口。</p>
 */
public final class PoseEditorSelectionSupport
{
    private PoseEditorSelectionSupport()
    {
    }

    /**
     * 取当前上可用的权威 Pose：伪装编辑器优先用其 valuePose，否则回落到 editor.getPose()。
     *
     * @param valuePoseOf 从宿主取伪装编辑器 valuePose 的解析器，可为 null（普通 Pose 宿主）
     */
    public static Pose getAuthoritativePose(UIPoseEditor self, Function<UIPoseEditor, ValuePose> valuePoseOf)
    {
        ValuePose valuePose = valuePoseOf == null ? null : valuePoseOf.apply(self);

        if (valuePose != null && valuePose.get() != null)
        {
            return valuePose.get();
        }

        return self.getPose();
    }

    /**
     * 取当前选中骨骼的 PoseTransform，未选中或异常时返回 null。
     */
    public static PoseTransform getCurrentPoseTransform(UIPoseEditor self, Function<UIPoseEditor, ValuePose> valuePoseOf)
    {
        String bone = self.getGroup();

        if (bone == null || bone.isEmpty())
        {
            return null;
        }

        try
        {
            Pose pose = getAuthoritativePose(self, valuePoseOf);

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
     *
     * @param factoryEditorOf 从宿主取 Pose 关键帧属性面板访问器的解析器，宿主不是该面板时为 null
     */
    public static void writeToSelection(UIPoseEditor self, Consumer<PoseTransform> writer,
        Function<UIPoseEditor, PoseKeyframeEditor> factoryEditorOf,
        Function<UIPoseEditor, ValuePose> valuePoseOf)
    {
        String bone = self.getGroup();

        if (bone == null || bone.isEmpty())
        {
            return;
        }

        PoseKeyframeEditor factoryEditor = factoryEditorOf == null ? null : factoryEditorOf.apply(self);

        if (factoryEditor != null)
        {
            UIPoseKeyframeFactory.UIPoseFactoryEditor.apply(factoryEditor.editor(), factoryEditor.keyframe(), bone, writer);

            return;
        }

        /* 普通 Pose 没有目录时 Pose.get 返回 null，这里补一个空的 PoseTransform 再写，
         * 与 BBS 在缺少条目时的行为保持一致。 */
        Pose editorPose = self.getPose();
        ValuePose valuePose = valuePoseOf == null ? null : valuePoseOf.apply(self);
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

    /** Pose 关键帧属性面板的 editor / keyframe 携带值（由 Mixin 侧的 Accessor 解出）。 */
    public record PoseKeyframeEditor(
        mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes editor,
        mchorse.bbs_mod.utils.keyframes.Keyframe<Pose> keyframe)
    {
    }
}
