package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.presets.AutoSavePresetState;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseManager;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 姿势编辑器自动保存注入。
 * <p>
 * 修改入口：applyToBone（位置/旋转/缩放）、setFix/setColor/setLighting（变换编辑器单骨骼修改）、
 * flipPose（翻转）。三个 applyXToSelection 批量方法内部对每个选中骨骼循环调用 setFix/setColor/setLighting，
 * 已被单骨骼注入覆盖，不再重复注入，避免按选中骨骼数量放大触发次数。
 * 数据快照通过 supplier 延迟到防抖触发后才构建，拖动期间不产生序列化开销。
 * </p>
 * <p>
 * 预设组名存在 group 字段中，不是 getGroup() 方法（后者返回当前骨骼组名）。
 * </p>
 */
@Mixin(value = UIPoseEditor.class, remap = true)
public abstract class UIPoseEditorMixin
{
    @Shadow private String group;
    @Shadow private Pose pose;

    // 变换修改（位置/旋转/缩放）
    @Inject(method = "applyToBone", at = @At("TAIL"))
    private void bbspp$autoSaveOnApplyBone(CallbackInfo ci)
    {
        bbspp$tryAutoSave();
    }

    // 单个骨骼的 fix/color/lighting 修改（变换编辑器回调直接调用这些，批量方法内部也循环调用它们）
    @Inject(method = "setFix", at = @At("TAIL"))
    private void bbspp$autoSaveOnSetFix(PoseTransform pt, float value, CallbackInfo ci)
    {
        bbspp$tryAutoSave();
    }

    @Inject(method = "setColor", at = @At("TAIL"))
    private void bbspp$autoSaveOnSetColor(PoseTransform pt, int color, CallbackInfo ci)
    {
        bbspp$tryAutoSave();
    }

    @Inject(method = "setLighting", at = @At("TAIL"))
    private void bbspp$autoSaveOnSetLighting(PoseTransform pt, float lighting, CallbackInfo ci)
    {
        bbspp$tryAutoSave();
    }

    // 翻转姿势
    @Inject(method = "flipPose", at = @At("TAIL"))
    private void bbspp$autoSaveOnFlip(CallbackInfo ci)
    {
        bbspp$tryAutoSave();
    }

    // 切换/加载姿势时重置自动保存状态（防止挂起任务写入新姿势、避免脏检查基准错位）
    @Inject(method = "setPose", at = @At("HEAD"))
    private void bbspp$resetAutoSaveOnSetPose(Pose pose, String group, CallbackInfo ci)
    {
        AutoSavePresetState.clear("pose");
    }

    private void bbspp$tryAutoSave()
    {
        if (!AutoSavePresetState.isEnabled("pose"))
        {
            return;
        }
        String preset = AutoSavePresetState.getSelectedPreset("pose");
        if (preset == null || preset.isEmpty() || this.group == null || this.group.isEmpty() || this.pose == null)
        {
            return;
        }

        AutoSavePresetState.scheduleSave("pose", PoseManager.INSTANCE,
                this.group, preset, () -> this.pose == null ? null : this.pose.toData());
    }
}
