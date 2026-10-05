package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.BBSAddonsSettings;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * IK 目标循环判定放开注入。
 * <p>
 * 原版 {@link ModelIKRuntime#isCyclicTarget} 判定选中骨骼是否落在本 IK 链上，
 * 被 UIModelIKFormPanel 三处共用：骨骼选择菜单置灰、吸管/视口拾取拒绝写入、
 * 以及目标标签后的 "(CYCLE!)" 标记。在设置关闭「IK目标约束」时让该判定恒为 false，
 * 一处注入同时放开 UI 全部三处限制。
 * </p>
 * <p>
 * 注意：此处只放开 UI；链的实际编译由 {@code ModelIKCache#compileFresh} 中的
 * {@code chainIds.contains(target)} 守卫，见 {@link ModelIKCacheMixin}。
 * </p>
 */
@Mixin(value = ModelIKRuntime.class, remap = true)
public abstract class ModelIKRuntimeMixin
{
    @Inject(method = "isCyclicTarget", at = @At("HEAD"), cancellable = true)
    private static void bbspp$disableIKTargetCycleGuard(
            IModel model, String tip, int chainLength, String target,
            CallbackInfoReturnable<Boolean> cir)
    {
        if (BBSAddonsSettings.ikTargetCycleConstraint != null
                && !BBSAddonsSettings.ikTargetCycleConstraint.get())
        {
            cir.setReturnValue(false);
        }
    }
}
