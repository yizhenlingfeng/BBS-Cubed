package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIModelPoseEditor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link UIModelPoseEditor} 的私有 {@code valuePose} 字段，
 * 供 {@code UIPoseEditorMixin} 在写入骨骼纹理前后触发
 * {@code preNotify/postNotify}（进 undo 记录并标记 form 数据已变更）。
 */
@Mixin(value = UIModelPoseEditor.class, remap = false)
public interface UIModelPoseEditorAccessor
{
    @Accessor(value = "valuePose", remap = false)
    ValuePose bbspp_cml$getValuePose();
}
