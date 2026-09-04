package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link UIPoseKeyframeFactory.UIPoseFactoryEditor} 的私有 editor/keyframe，
 * 供 {@code UIPoseEditorMixin} 在关键帧宿主下走其静态
 * {@code apply(editor, keyframe, group, consumer)} 写入路径
 * （多选关键帧同步 + preNotify/postNotify 进 undo）。
 */
@Mixin(value = UIPoseKeyframeFactory.UIPoseFactoryEditor.class, remap = false)
public interface UIPoseFactoryEditorAccessor
{
    @Accessor(value = "editor", remap = false)
    UIKeyframes bbspp_cml$getEditor();

    @Accessor(value = "keyframe", remap = false)
    Keyframe<Pose> bbspp_cml$getKeyframe();
}
