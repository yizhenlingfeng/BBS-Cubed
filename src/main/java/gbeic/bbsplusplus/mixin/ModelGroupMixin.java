package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.BoneOutlineHolder;
import gbeic.bbsplusplus.api.GlintSettingsHolder;
import gbeic.bbsplusplus.api.GroupGlintHolder;
import gbeic.bbsplusplus.api.GroupOutlineHolder;
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
 * 为 {@link ModelGroup} 提供渲染期覆盖字段：骨骼纹理、附魔光效、PBR 与描边。
 * 这些字段由 {@code ModelMixin} 在 {@code applyPose} 时写入，并由 {@code reset()} 在每帧
 * 开始前清空，避免共享 {@link ModelGroup} 实例在多个 form 之间串数据。
 */
@Mixin(value = ModelGroup.class, remap = false)
public class ModelGroupMixin implements GroupTextureHolder, GroupGlintHolder, GlintSettingsHolder, GroupPbrHolder, GroupOutlineHolder
{
    @Unique
    private Link bbspp_cml$textureOverride;

    @Unique
    private boolean bbspp_cml$glint;

    @Unique
    private final Color bbspp_cml$glintColor = new Color(1F, 1F, 1F, 1F);

    @Unique
    private float bbspp_cml$glintSpeed = GlintSettingsHolder.DEFAULT_SPEED;

    @Unique
    private float bbspp_cml$glintBrightness = GlintSettingsHolder.DEFAULT_BRIGHTNESS;

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

    /* 描边渲染期覆盖值（由 applyPose 从 PoseTransform 复制），默认关闭。 */
    @Unique
    private boolean bbspp_cml$outline;

    @Unique
    private final Color bbspp_cml$outlineColor = new Color(1F, 1F, 1F, 1F);

    @Unique
    private float bbspp_cml$outlineWidth = BoneOutlineHolder.DEFAULT_WIDTH;

    @Unique
    private float bbspp_cml$outlineGlow = BoneOutlineHolder.DEFAULT_GLOW;

    @Unique
    private float bbspp_cml$outlineVanillaGlow = BoneOutlineHolder.DEFAULT_VANILLA_GLOW;

    @Unique
    private float bbspp_cml$outlineDecalSize = BoneOutlineHolder.DEFAULT_DECAL;

    @Unique
    private boolean bbspp_cml$outlineFaceCull = BoneOutlineHolder.DEFAULT_FACE_CULL;

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

    @Override
    public float bbspp_cml$getGlintSpeed()
    {
        return this.bbspp_cml$glintSpeed;
    }

    @Override
    public void bbspp_cml$setGlintSpeed(float speed)
    {
        this.bbspp_cml$glintSpeed = Math.max(0F, Math.min(GlintSettingsHolder.MAX_SPEED, speed));
    }

    @Override
    public float bbspp_cml$getGlintBrightness()
    {
        return this.bbspp_cml$glintBrightness;
    }

    @Override
    public void bbspp_cml$setGlintBrightness(float brightness)
    {
        this.bbspp_cml$glintBrightness = Math.max(0F, Math.min(GlintSettingsHolder.MAX_BRIGHTNESS, brightness));
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

    /* ---- outline getters / setters ---- */

    @Override
    public boolean bbspp_cml$getOutline()
    {
        return this.bbspp_cml$outline;
    }

    @Override
    public void bbspp_cml$setOutline(boolean outline)
    {
        this.bbspp_cml$outline = outline;
    }

    @Override
    public Color bbspp_cml$getOutlineColor()
    {
        return this.bbspp_cml$outlineColor;
    }

    @Override
    public void bbspp_cml$setOutlineColor(Color color)
    {
        if (color != null)
        {
            this.bbspp_cml$outlineColor.copy(color);
        }
    }

    @Override
    public float bbspp_cml$getOutlineWidth()
    {
        return this.bbspp_cml$outlineWidth;
    }

    @Override
    public void bbspp_cml$setOutlineWidth(float width)
    {
        this.bbspp_cml$outlineWidth = width;
    }

    @Override
    public float bbspp_cml$getOutlineGlow()
    {
        return this.bbspp_cml$outlineGlow;
    }

    @Override
    public void bbspp_cml$setOutlineGlow(float glow)
    {
        this.bbspp_cml$outlineGlow = glow;
    }

    @Override
    public float bbspp_cml$getOutlineVanillaGlow()
    {
        return this.bbspp_cml$outlineVanillaGlow;
    }

    @Override
    public void bbspp_cml$setOutlineVanillaGlow(float glow)
    {
        this.bbspp_cml$outlineVanillaGlow = glow;
    }

    @Override
    public float bbspp_cml$getOutlineDecalSize()
    {
        return this.bbspp_cml$outlineDecalSize;
    }

    @Override
    public void bbspp_cml$setOutlineDecalSize(float size)
    {
        this.bbspp_cml$outlineDecalSize = size;
    }

    @Override
    public boolean bbspp_cml$getOutlineFaceCull()
    {
        return this.bbspp_cml$outlineFaceCull;
    }

    @Override
    public void bbspp_cml$setOutlineFaceCull(boolean faceCull)
    {
        this.bbspp_cml$outlineFaceCull = faceCull;
    }

    @Inject(method = "reset()V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$clearTextureOverride(CallbackInfo ci)
    {
        this.bbspp_cml$textureOverride = null;
        this.bbspp_cml$glint = false;
        this.bbspp_cml$glintColor.set(0xFFFFFFFF);
        this.bbspp_cml$glintSpeed = GlintSettingsHolder.DEFAULT_SPEED;
        this.bbspp_cml$glintBrightness = GlintSettingsHolder.DEFAULT_BRIGHTNESS;
        this.bbspp_cml$pbrSmoothness = 0F;
        this.bbspp_cml$pbrMetallic = 0F;
        this.bbspp_cml$pbrSss = 0F;
        this.bbspp_cml$pbrEmission = 0F;
        this.bbspp_cml$pbrRelief = 0F;

        this.bbspp_cml$outline = false;
        this.bbspp_cml$outlineColor.set(0xFFFFFFFF);
        this.bbspp_cml$outlineWidth = BoneOutlineHolder.DEFAULT_WIDTH;
        this.bbspp_cml$outlineGlow = BoneOutlineHolder.DEFAULT_GLOW;
        this.bbspp_cml$outlineVanillaGlow = BoneOutlineHolder.DEFAULT_VANILLA_GLOW;
        this.bbspp_cml$outlineDecalSize = BoneOutlineHolder.DEFAULT_DECAL;
        this.bbspp_cml$outlineFaceCull = BoneOutlineHolder.DEFAULT_FACE_CULL;
    }
}
