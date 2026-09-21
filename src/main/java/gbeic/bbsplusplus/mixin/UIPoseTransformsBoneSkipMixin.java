package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.pose.PoseBoneSkipData;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * 让变换面板和 Gizmo 对骨骼的实际编辑自动恢复该骨骼参与当前 Pose 帧。
 *
 * <p>通过重定向原版批量写入入口，把“清除跳过标记”和参数变更放进同一轮关键帧通知，
 * 避免产生两个撤销步骤。BBS 2.6 起普通编辑、重置与自动关键帧（原录制期写入）统一走
 * {@code applyToSelection}，由 {@code forEachSelectedKeyframe} 在播放头处补帧，因此不再需要
 * 单独的 {@code applyDuringRecording} 重定向。</p>
 */
@Mixin(value = UIPoseKeyframeFactory.UIPoseTransforms.class, remap = true)
public class UIPoseTransformsBoneSkipMixin
{
    /**
     * 注入目标：{@code UIPoseTransforms#applyToSelection} 的多骨骼写入调用。
     * 注入原因：数字输入与 Gizmo 最终都从这里修改选中骨骼（含自动关键帧播放头补帧）。
     * 修改后的行为：每根目标骨骼先恢复参与当前帧，再在同一撤销块中应用变换。
     */
    @Redirect(
        method = "applyToSelection",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/ui/framework/elements/input/keyframes/factories/UIPoseKeyframeFactory$UIPoseFactoryEditor;applyBones(Lmchorse/bbs_mod/ui/framework/elements/input/keyframes/UIKeyframes;Lmchorse/bbs_mod/utils/keyframes/Keyframe;Ljava/util/List;Ljava/util/function/BiConsumer;)V"
        )
    )
    private void bbspp$unskipBeforeTransformEdit(UIKeyframes editor, Keyframe<?> keyframe, List<String> bones,
                                                 BiConsumer<String, PoseTransform> consumer)
    {
        UIPoseKeyframeFactory.UIPoseFactoryEditor.apply(editor, keyframe, (pose) ->
        {
            for (String bone : bones)
            {
                PoseBoneSkipData.setSkipped(pose, bone, false);
                consumer.accept(bone, pose.getOrCreate(bone));
            }
        });
    }

    /*
     * 2.7 起 UIPoseTransforms#reset() 不再直接调用 UIPoseFactoryEditor.apply(...)，
     * 而是走 this.applyToTarget(...) -> applyToSelection(...) -> applyBones(...)，
     * 已经被上面 applyBones 的 @Redirect 覆盖（重置时同样会先 setSkipped(false)），
     * 因此旧的 reset 重定向在 2.7 中目标调用不存在，必须移除。
     */
}
