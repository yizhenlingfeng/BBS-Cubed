package gbeic.bbsplusplus.client.ui.pose;

import mchorse.bbs_mod.cubic.IBoneHierarchy;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 子骨骼遍历与递归写入（D-09）。
 *
 * <p>把 {@code UIPoseEditorMixin} 里的 {@code forEachSelectedChildren} 搬到这里，
 * 保留「同时写入实况 Pose 和编辑器 Pose、缺失条目补空 PoseTransform、
 * 前后 preNotify/postNotify 进 undo」的原语义。写完通过 {@code afterWrite} 回调
 * 触发 Mixin 侧的 UI 同步（原 {@code syncGlintToggle/syncOutlineControls}）。</p>
 *
 * <p>伪装编辑器的 {@code valuePose} 由调用方（Mixin）通过 {@code valuePoseOf} 解析后传入，
 * 这样本类无需直接引用 Mixin 包内的 Accessor 接口。</p>
 */
public final class PoseBoneHierarchyWriter
{
    private PoseBoneHierarchyWriter()
    {
    }

    /**
     * 对当前选中骨骼的所有子骨骼应用 writer。
     *
     * @param hierarchy 模型骨骼层级（Mixin 的 @Shadow model）
     * @param afterWrite 写完后的 UI 同步回调，可为 null
     * @param valuePoseOf 从宿主取伪装编辑器 valuePose 的解析器，可为 null
     */
    public static void forEachSelectedChildren(UIPoseEditor self, IBoneHierarchy hierarchy,
        Consumer<PoseTransform> writer, Runnable afterWrite,
        Function<UIPoseEditor, ValuePose> valuePoseOf)
    {
        Pose editorPose = self.getPose();
        ValuePose valuePose = valuePoseOf == null ? null : valuePoseOf.apply(self);
        Pose livePose = valuePose == null ? null : valuePose.get();

        if ((editorPose == null && livePose == null) || hierarchy == null)
        {
            return;
        }

        String bone = self.getGroup();

        if (bone == null || bone.isEmpty())
        {
            return;
        }

        if (valuePose != null)
        {
            valuePose.preNotify(IValueListener.FLAG_UNMERGEABLE);
        }

        Collection<String> children = hierarchy.getAllChildrenKeys(bone);

        if (children != null)
        {
            for (String child : children)
            {
                if (livePose != null)
                {
                    PoseTransform transform = livePose.get(child);

                    if (transform == null)
                    {
                        transform = new PoseTransform();
                        livePose.transforms.put(child, transform);
                    }

                    writer.accept(transform);
                }

                if (editorPose != null && editorPose != livePose)
                {
                    PoseTransform transform = editorPose.get(child);

                    if (transform == null)
                    {
                        transform = new PoseTransform();
                        editorPose.transforms.put(child, transform);
                    }

                    writer.accept(transform);
                }
            }
        }

        if (valuePose != null)
        {
            valuePose.postNotify(IValueListener.FLAG_UNMERGEABLE);
        }

        if (afterWrite != null)
        {
            afterWrite.run();
        }
    }
}
