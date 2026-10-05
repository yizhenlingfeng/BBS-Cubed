package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.BBSAddonsSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * IK 链编译期循环守卫放开注入。
 * <p>
 * 原版 {@code ModelIKCache#compileFresh} 在编译时会跳过目标落在本链上的链
 * （{@code if (chainIds.contains(target)) continue;}），即 UI 即便允许选择也不会生效。
 * 设置关闭「IK目标约束」时让该 contains 判定恒为 false，链照常编译。
 * </p>
 * <p>
 * ordinal=0 的依据：compileFresh 内 {@code List.contains} 仅两处——
 * 第 122 行 {@code chainIds.contains(target)}（ordinal 0，本注入点）与
 * 第 134 行 {@code chainIds.contains(poleTarget)}（ordinal 1，极向量约束，保持原版）；
 * {@code getAllGroupKeys()} 返回 {@code Collection}，其 contains 不参与 List 计数。
 * </p>
 * <p>
 * 目标类为包私有，故用 targets 字符串形式指定，编译期不引用其 Class。
 * </p>
 */
@Mixin(targets = "mchorse.bbs_mod.cubic.ik.ModelIKCache", remap = true)
public abstract class ModelIKCacheMixin
{
    @Redirect(
        method = "compileFresh",
        at = @At(value = "INVOKE",
                 target = "Ljava/util/List;contains(Ljava/lang/Object;)Z",
                 ordinal = 0)
    )
    private static boolean bbspp$allowCyclicTarget(List<String> chainIds, Object target)
    {
        if (BBSAddonsSettings.ikTargetCycleConstraint != null
                && !BBSAddonsSettings.ikTargetCycleConstraint.get())
        {
            return false;
        }

        return chainIds.contains(target);
    }
}
