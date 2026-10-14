package gbeic.bbsplusplus.mixin.client;

import mchorse.bbs_mod.forms.renderers.MobFormRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 修复 MobFormRenderer.renderUIPreview() 未初始化 entity 导致 FBO 空白的问题。
 *
 * BBS 2.7 (1.21.11) 移植时，renderUIPreview() 直接检查 this.entity == null 就 return，
 * 但整个方法没有调用 ensureEntity()。而 render3D() 第一件事就是 this.ensureEntity()。
 *
 * 结果：form 列表首次渲染时 entity 还是 null，renderUIPreview() 空返回，
 * FBO 清空后什么都没画进去 → player/steve 等 mob 模型格子全部空白。
 *
 * 修复：在 renderUIPreview() HEAD 注入 ensureEntity() 调用。
 */
@Mixin(MobFormRenderer.class)
public abstract class MobFormRendererEnsureEntityMixin
{
    @Invoker("ensureEntity")
    public abstract void bbspp$invokeEnsureEntity();

    @Inject(method = "renderUIPreview", at = @At("HEAD"))
    private void bbspp$ensureEntityBeforePreview(CallbackInfo ci)
    {
        this.bbspp$invokeEnsureEntity();
    }
}
