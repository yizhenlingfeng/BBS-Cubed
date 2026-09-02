package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.ui.presets.AutoSavePresetState;
import mchorse.bbs_mod.data.types.MapType;
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
 * 关键：变换编辑器的 fix/color/lighting 修改直接调用 setFix/setColor/setLighting，
 * 不经过 applyToBone，必须在这些方法上也注入。
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

    // 单个骨骼的 fix/color/lighting 修改（变换编辑器回调直接调用这些）
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
    private void bbspp$autoSaveOnSetLighting(PoseTransform pt, boolean lighting, CallbackInfo ci)
    {
        bbspp$tryAutoSave();
    }

    // 批量修改（应用到选中的所有骨骼）
    @Inject(method = "applyFixToSelection", at = @At("TAIL"))
    private void bbspp$autoSaveOnApplyFix(float value, CallbackInfo ci)
    {
        bbspp$tryAutoSave();
    }

    @Inject(method = "applyColorToSelection", at = @At("TAIL"))
    private void bbspp$autoSaveOnApplyColor(int color, CallbackInfo ci)
    {
        bbspp$tryAutoSave();
    }

    @Inject(method = "applyLightingToSelection", at = @At("TAIL"))
    private void bbspp$autoSaveOnApplyLighting(boolean lighting, CallbackInfo ci)
    {
        bbspp$tryAutoSave();
    }

    // 翻转姿势
    @Inject(method = "flipPose", at = @At("TAIL"))
    private void bbspp$autoSaveOnFlip(CallbackInfo ci)
    {
        bbspp$tryAutoSave();
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

        MapType data = this.pose.toData();
        if (data != null)
        {
            AutoSavePresetState.scheduleSave("pose", PoseManager.INSTANCE, this.group, preset, data);
        }
    }
}
