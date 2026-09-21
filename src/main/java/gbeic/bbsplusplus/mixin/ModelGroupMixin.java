package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.GroupGlintHolder;
import gbeic.bbsplusplus.api.GroupPbrHolder;
import gbeic.bbsplusplus.api.GroupTextureHolder;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.colors.Color;
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
 *
 * <p>"附魔光效"（{@link GroupGlintHolder}）与纹理覆盖同一生命周期：reset 清空、
 * applyPose 重填。</p>
 *
 * <p>此前这里还有逐骨骼「纹理调色 / 白化」字段（{@code GroupTextureGradeHolder}），
 * 已随姿势材质栏一并移除。</p>
 */
@Mixin(value = ModelGroup.class, remap = false)
public class ModelGroupMixin implements GroupTextureHolder, GroupGlintHolder, GroupPbrHolder
{
    @Unique
    private Link bbspp_cml$textureOverride;

    @Unique
    private boolean bbspp_cml$glint;

    @Unique
    private final Color bbspp_cml$glintColor = new Color(1F, 1F, 1F, 1F);

    /* PBR 渲染期覆盖值，全 0 = 无覆盖（回落材质级 PBR）。 */
    @Unique
    private float bbspp_cml$pbrSmoothness;

    @Unique
    private float bbspp_cml$pbrMetallic;

    @Unique
    private float bbspp_cml$pbrSss;

    @Unique
    private float bbspp_cml$pbrEmission;

    @Unique
    private float bbspp_cml$pbrRelief;

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
    public boolean bbspp_cml$getGlint()
    {
        return this.bbspp_cml$glint;
    }

    @Override
    public void bbspp_cml$setGlint(boolean glint)
    {
        this.bbspp_cml$glint = glint;
    }

    @Override
    public Color bbspp_cml$getGlintColor()
    {
        return this.bbspp_cml$glintColor;
    }

    @Override
    public void bbspp_cml$setGlintColor(Color color)
    {
        if (color != null)
        {
            this.bbspp_cml$glintColor.copy(color);
        }
    }

    /* ---- PBR getters / setters ---- */

    @Override
    public float bbspp_cml$getSmoothness()
    {
        return this.bbspp_cml$pbrSmoothness;
    }

    @Override
    public void bbspp_cml$setSmoothness(float value)
    {
        this.bbspp_cml$pbrSmoothness = value;
    }

    @Override
    public float bbspp_cml$getMetallic()
    {
        return this.bbspp_cml$pbrMetallic;
    }

    @Override
    public void bbspp_cml$setMetallic(float value)
    {
        this.bbspp_cml$pbrMetallic = value;
    }

    @Override
    public float bbspp_cml$getSss()
    {
        return this.bbspp_cml$pbrSss;
    }

    @Override
    public void bbspp_cml$setSss(float value)
    {
        this.bbspp_cml$pbrSss = value;
    }

    @Override
    public float bbspp_cml$getEmission()
    {
        return this.bbspp_cml$pbrEmission;
    }

    @Override
    public void bbspp_cml$setEmission(float value)
    {
        this.bbspp_cml$pbrEmission = value;
    }

    @Override
    public float bbspp_cml$getRelief()
    {
        return this.bbspp_cml$pbrRelief;
    }

    @Override
    public void bbspp_cml$setRelief(float value)
    {
        this.bbspp_cml$pbrRelief = value;
    }

    @Inject(method = "reset()V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$clearTextureOverride(CallbackInfo ci)
    {
        this.bbspp_cml$textureOverride = null;
        this.bbspp_cml$glint = false;
        this.bbspp_cml$glintColor.set(0xFFFFFFFF);
        this.bbspp_cml$pbrSmoothness = 0F;
        this.bbspp_cml$pbrMetallic = 0F;
        this.bbspp_cml$pbrSss = 0F;
        this.bbspp_cml$pbrEmission = 0F;
        this.bbspp_cml$pbrRelief = 0F;
    }
}
