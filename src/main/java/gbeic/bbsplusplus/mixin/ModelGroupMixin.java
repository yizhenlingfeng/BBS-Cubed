package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.GroupTextureHolder;
import gbeic.bbsplusplus.api.GroupTextureGradeHolder;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给 {@link ModelGroup} 增加渲染期"骨骼纹理覆盖"字段（对齐 BBScml 的
 * {@code ModelGroup.textureOverride}，但以 @Unique + duck 接口形式外挂）。
 *
 * <p>生命周期：{@code Model.resetPose()} 每帧渲染前调用 {@code reset()} —— 在其 TAIL
 * 清空覆盖，保证共享 Model 实例在多个 form 间不串纹理；随后 {@code ModelMixin}
 * 在 applyPose TAIL 依据当前 Pose 重新写入。</p>
 */
@Mixin(value = ModelGroup.class, remap = false)
public class ModelGroupMixin implements GroupTextureHolder, GroupTextureGradeHolder
{
    @Unique
    private Link bbspp_cml$textureOverride;

    @Unique
    private final Color bbspp_cml$textureTint = new Color(1F, 1F, 1F, 0F);

    @Unique
    private float bbspp_cml$textureWhiten;

    @Override
    public Link bbspp_cml$getTextureOverride()
    {
        return this.bbspp_cml$textureOverride;
    }

    @Override
    public void bbspp_cml$setTextureOverride(Link texture)
    {
        this.bbspp_cml$textureOverride = texture;
    }

    @Override
    public Color bbspp_cml$getTextureTint()
    {
        return this.bbspp_cml$textureTint;
    }

    @Override
    public float bbspp_cml$getTextureWhiten()
    {
        return this.bbspp_cml$textureWhiten;
    }

    @Override
    public void bbspp_cml$setTextureTint(Color color)
    {
        this.bbspp_cml$textureTint.copy(color);
    }

    @Override
    public void bbspp_cml$setTextureWhiten(float value)
    {
        this.bbspp_cml$textureWhiten = MathUtils.clamp(value, 0F, 1F);
    }

    @Inject(method = "reset()V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$clearTextureOverride(CallbackInfo ci)
    {
        this.bbspp_cml$textureOverride = null;
        this.bbspp_cml$textureTint.set(Colors.WHITE);
        this.bbspp_cml$textureTint.a = 0F;
        this.bbspp_cml$textureWhiten = 0F;
    }
}
