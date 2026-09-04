package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.utils.MolangVariableScopes;
import mchorse.bbs_mod.cubic.IModelInstance;
import mchorse.bbs_mod.cubic.animation.Animator;
import mchorse.bbs_mod.forms.entities.IEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * molang variable.* 按模型隔离的消费点之一:动作管线应用前后换入/换出该
 * 模型的变量作用域(细节见 {@link MolangVariableScopes})。TAIL 用 RETURN
 * 匹配所有返回点,确保任何提前 return 都不漏换出。
 */
@Mixin(value = Animator.class, remap = false)
public abstract class AnimatorMixin
{
    @Inject(method = "applyActions", at = @At("HEAD"), remap = false)
    private void bbspp_cml$swapIn(IEntity target, IModelInstance armature, float transition, CallbackInfo ci)
    {
        MolangVariableScopes.beforeApply(armature);
    }

    @Inject(method = "applyActions", at = @At("RETURN"), remap = false)
    private void bbspp_cml$swapOut(IEntity target, IModelInstance armature, float transition, CallbackInfo ci)
    {
        MolangVariableScopes.afterApply(armature);
    }
}
