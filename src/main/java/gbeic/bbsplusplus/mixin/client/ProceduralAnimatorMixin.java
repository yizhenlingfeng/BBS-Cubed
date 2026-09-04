package gbeic.bbsplusplus.mixin.client;

import gbeic.bbsplusplus.utils.MolangVariableScopes;
import mchorse.bbs_mod.cubic.IModelInstance;
import mchorse.bbs_mod.cubic.animation.ProceduralAnimator;
import mchorse.bbs_mod.forms.entities.IEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * molang variable.* 按模型隔离的消费点之二:procedural 模型的动作管线
 * (细节见 {@link MolangVariableScopes} 与 {@link AnimatorMixin})。
 */
@Mixin(value = ProceduralAnimator.class, remap = false)
public abstract class ProceduralAnimatorMixin
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
