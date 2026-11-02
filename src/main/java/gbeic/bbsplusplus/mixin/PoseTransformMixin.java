package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.BoneOutlineHolder;
import gbeic.bbsplusplus.api.BonePbrHolder;
import gbeic.bbsplusplus.api.BoneTextureHolder;
import gbeic.bbsplusplus.api.GlintHolder;
import gbeic.bbsplusplus.api.GlintSettingsHolder;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.interps.AutoBezier;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.resources.LinkUtils;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 为 {@link PoseTransform} 挂载逐骨骼的纹理、附魔光效、PBR 与描边参数，并把它们接入
 * 序列化（{@code toData/fromData}）、拷贝（{@code copy}）、相等比较（{@code equals}）、
 * 重置（{@code identity}）、插值（{@code lerp/autoLerp}）与叠加（{@code add}）。
 *
 * <p>这些钩子必须成组维护：漏掉 {@code equals} 会让关键帧误判相等，漏掉
 * {@code lerp/autoLerp} 会让回放时的参数丢失或闪烁。</p>
 */
@Mixin(value = PoseTransform.class, remap = false)
public class PoseTransformMixin implements BoneTextureHolder, GlintHolder, GlintSettingsHolder, BonePbrHolder, BoneOutlineHolder
{
    @Unique
    private Link bbspp_cml$texture;

    @Unique
    private boolean bbspp_cml$glint;

    /** 光效颜色，默认白色 = 原版观感。alpha 通道会一并乘进最终亮度。 */
    @Unique
    private final Color bbspp_cml$glintColor = new Color(1F, 1F, 1F, 1F);

    /** 光效流动速度倍率，1 = 原速，0 = 静止。 */
    @Unique
    private float bbspp_cml$glintSpeed = GlintSettingsHolder.DEFAULT_SPEED;

    /** 光效图层自发光强度，1 = 原始亮度。 */
    @Unique
    private float bbspp_cml$glintBrightness = GlintSettingsHolder.DEFAULT_BRIGHTNESS;

    /* PBR 五值（LabPBR specular channels），0~1，全 0 = 无 PBR。 */
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

    /* 逐骨骼描边设置：跟随姿势/关键帧保存，不再写入 model config。 */
    @Unique
    private boolean bbspp_cml$outline;

    @Unique
    private final Color bbspp_cml$outlineColor = new Color(1F, 1F, 1F, 1F);

    @Unique
    private float bbspp_cml$outlineWidth = BoneOutlineHolder.DEFAULT_WIDTH;

    @Unique
    private float bbspp_cml$outlineGlow = BoneOutlineHolder.DEFAULT_GLOW;

    @Unique
    private float bbspp_cml$outlineDecalSize = BoneOutlineHolder.DEFAULT_DECAL;

    @Override
    public Link bbspp_cml$getTexture()
    {
        return this.bbspp_cml$texture;
    }

    @Override
    public void bbspp_cml$setTexture(Link texture)
    {
        this.bbspp_cml$texture = texture;
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
        this.bbspp_cml$glintSpeed = clampGlintSetting(speed, GlintSettingsHolder.MAX_SPEED);
    }

    @Override
    public float bbspp_cml$getGlintBrightness()
    {
        return this.bbspp_cml$glintBrightness;
    }

    @Override
    public void bbspp_cml$setGlintBrightness(float brightness)
    {
        this.bbspp_cml$glintBrightness = clampGlintSetting(brightness, GlintSettingsHolder.MAX_BRIGHTNESS);
    }

    @Unique
    private static float clampGlintSetting(float value, float max)
    {
        return Math.max(0F, Math.min(max, value));
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
    public float bbspp_cml$getOutlineDecalSize()
    {
        return this.bbspp_cml$outlineDecalSize;
    }

    @Override
    public void bbspp_cml$setOutlineDecalSize(float size)
    {
        this.bbspp_cml$outlineDecalSize = size;
    }

    @Inject(method = "toData", at = @At("TAIL"), remap = false)
    private void bbspp_cml$writeTexture(MapType data, CallbackInfo ci)
    {
        if (this.bbspp_cml$texture != null)
        {
            data.put("texture", LinkUtils.toData(this.bbspp_cml$texture));
        }

        if (this.bbspp_cml$glint)
        {
            data.putBool("glint", true);
        }
        if (this.bbspp_cml$glintColor.getARGBColor() != 0xFFFFFFFF)
        {
            data.putInt("glint_color", this.bbspp_cml$glintColor.getARGBColor());
        }
        if (this.bbspp_cml$glintSpeed != GlintSettingsHolder.DEFAULT_SPEED)
        {
            data.putFloat("glint_speed", this.bbspp_cml$glintSpeed);
        }
        if (this.bbspp_cml$glintBrightness != GlintSettingsHolder.DEFAULT_BRIGHTNESS)
        {
            data.putFloat("glint_brightness", this.bbspp_cml$glintBrightness);
        }

        if (this.bbspp_cml$pbrSmoothness != 0F) data.putFloat("pbr_smoothness", this.bbspp_cml$pbrSmoothness);
        if (this.bbspp_cml$pbrMetallic != 0F)  data.putFloat("pbr_metallic", this.bbspp_cml$pbrMetallic);
        if (this.bbspp_cml$pbrSss != 0F)       data.putFloat("pbr_sss", this.bbspp_cml$pbrSss);
        if (this.bbspp_cml$pbrEmission != 0F)  data.putFloat("pbr_emission", this.bbspp_cml$pbrEmission);
        if (this.bbspp_cml$pbrRelief != 0F)    data.putFloat("pbr_relief", this.bbspp_cml$pbrRelief);

        boolean outlineData = this.bbspp_cml$outline
            || this.bbspp_cml$outlineColor.getARGBColor() != 0xFFFFFFFF
            || this.bbspp_cml$outlineWidth != BoneOutlineHolder.DEFAULT_WIDTH
            || this.bbspp_cml$outlineGlow != BoneOutlineHolder.DEFAULT_GLOW
            || this.bbspp_cml$outlineDecalSize != BoneOutlineHolder.DEFAULT_DECAL;

        if (outlineData)
        {
            if (this.bbspp_cml$outline)
            {
                data.putBool("outline", true);
            }

            data.putInt("outline_color", this.bbspp_cml$outlineColor.getARGBColor());
            data.putFloat("outline_width", this.bbspp_cml$outlineWidth);
            data.putFloat("outline_glow", this.bbspp_cml$outlineGlow);
            data.putFloat("outline_decal", this.bbspp_cml$outlineDecalSize);
        }
    }

    @Inject(method = "fromData", at = @At("TAIL"), remap = false)
    private void bbspp_cml$readTexture(MapType data, CallbackInfo ci)
    {
        if (data.has("texture"))
        {
            this.bbspp_cml$texture = LinkUtils.create(data.get("texture"));
        }
        else
        {
            this.bbspp_cml$texture = null;
        }

        this.bbspp_cml$glint = data.getBool("glint");
        this.bbspp_cml$glintColor.set(data.getInt("glint_color", 0xFFFFFFFF));
        this.bbspp_cml$glintSpeed = data.has("glint_speed")
            ? data.getFloat("glint_speed") : GlintSettingsHolder.DEFAULT_SPEED;
        this.bbspp_cml$glintBrightness = data.has("glint_brightness")
            ? data.getFloat("glint_brightness") : GlintSettingsHolder.DEFAULT_BRIGHTNESS;

        this.bbspp_cml$pbrSmoothness = data.getFloat("pbr_smoothness");
        this.bbspp_cml$pbrMetallic = data.getFloat("pbr_metallic");
        this.bbspp_cml$pbrSss = data.getFloat("pbr_sss");
        this.bbspp_cml$pbrEmission = data.getFloat("pbr_emission");
        this.bbspp_cml$pbrRelief = data.getFloat("pbr_relief");

        this.bbspp_cml$outline = data.getBool("outline");
        this.bbspp_cml$outlineColor.set(data.getInt("outline_color", 0xFFFFFFFF));
        this.bbspp_cml$outlineWidth = data.has("outline_width")
            ? data.getFloat("outline_width") : BoneOutlineHolder.DEFAULT_WIDTH;
        this.bbspp_cml$outlineGlow = data.has("outline_glow")
            ? data.getFloat("outline_glow") : BoneOutlineHolder.DEFAULT_GLOW;
        this.bbspp_cml$outlineDecalSize = data.has("outline_decal")
            ? data.getFloat("outline_decal") : BoneOutlineHolder.DEFAULT_DECAL;
    }

    @Inject(method = "copy(Lmchorse/bbs_mod/utils/pose/Transform;)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$copyTexture(mchorse.bbs_mod.utils.pose.Transform transform, CallbackInfo ci)
    {
        if (transform instanceof BoneTextureHolder holder)
        {
            this.bbspp_cml$texture = LinkUtils.copy(holder.bbspp_cml$getTexture());
        }

        if (transform instanceof GlintHolder holder)
        {
            this.bbspp_cml$glint = holder.bbspp_cml$getGlint();
            this.bbspp_cml$glintColor.copy(holder.bbspp_cml$getGlintColor());
        }

        if (transform instanceof GlintSettingsHolder holder)
        {
            this.bbspp_cml$glintSpeed = holder.bbspp_cml$getGlintSpeed();
            this.bbspp_cml$glintBrightness = holder.bbspp_cml$getGlintBrightness();
        }

        if (transform instanceof BonePbrHolder holder)
        {
            this.bbspp_cml$pbrSmoothness = holder.bbspp_cml$getSmoothness();
            this.bbspp_cml$pbrMetallic = holder.bbspp_cml$getMetallic();
            this.bbspp_cml$pbrSss = holder.bbspp_cml$getSss();
            this.bbspp_cml$pbrEmission = holder.bbspp_cml$getEmission();
            this.bbspp_cml$pbrRelief = holder.bbspp_cml$getRelief();
        }

        if (transform instanceof BoneOutlineHolder holder)
        {
            this.bbspp_cml$outline = holder.bbspp_cml$getOutline();
            this.bbspp_cml$outlineColor.copy(holder.bbspp_cml$getOutlineColor());
            this.bbspp_cml$outlineWidth = holder.bbspp_cml$getOutlineWidth();
            this.bbspp_cml$outlineGlow = holder.bbspp_cml$getOutlineGlow();
            this.bbspp_cml$outlineDecalSize = holder.bbspp_cml$getOutlineDecalSize();
        }
    }

    @Inject(method = "equals(Ljava/lang/Object;)Z", at = @At("RETURN"), cancellable = true, remap = false)
    private void bbspp_cml$equalsTexture(Object obj, CallbackInfoReturnable<Boolean> cir)
    {
        if (cir.getReturnValueZ() && obj instanceof BoneTextureHolder holder)
        {
            Link mine = this.bbspp_cml$texture;
            Link other = holder.bbspp_cml$getTexture();

            if (mine == null ? other != null : !mine.equals(other))
            {
                cir.setReturnValue(false);
            }
        }

        /* 布尔量同样必须参与判等：否则"只有光效不同"的两个 Pose 会被判等，
         * 关键帧去重/值比较场景会把开了光效的帧错误地合并掉。 */
        if (cir.getReturnValueZ() && obj instanceof GlintHolder holder)
        {
            if (this.bbspp_cml$glint != holder.bbspp_cml$getGlint()
                || this.bbspp_cml$glintColor.getARGBColor() != holder.bbspp_cml$getGlintColor().getARGBColor())
            {
                cir.setReturnValue(false);
            }
        }

        if (cir.getReturnValueZ() && obj instanceof GlintSettingsHolder holder)
        {
            if (Float.compare(this.bbspp_cml$glintSpeed, holder.bbspp_cml$getGlintSpeed()) != 0
                || Float.compare(this.bbspp_cml$glintBrightness, holder.bbspp_cml$getGlintBrightness()) != 0)
            {
                cir.setReturnValue(false);
            }
        }

        if (cir.getReturnValueZ() && obj instanceof BonePbrHolder holder)
        {
            if (this.bbspp_cml$pbrSmoothness != holder.bbspp_cml$getSmoothness()
                || this.bbspp_cml$pbrMetallic != holder.bbspp_cml$getMetallic()
                || this.bbspp_cml$pbrSss != holder.bbspp_cml$getSss()
                || this.bbspp_cml$pbrEmission != holder.bbspp_cml$getEmission()
                || this.bbspp_cml$pbrRelief != holder.bbspp_cml$getRelief())
            {
                cir.setReturnValue(false);
            }
        }

        if (cir.getReturnValueZ() && obj instanceof BoneOutlineHolder holder)
        {
            if (this.bbspp_cml$outline != holder.bbspp_cml$getOutline()
                || this.bbspp_cml$outlineColor.getARGBColor() != holder.bbspp_cml$getOutlineColor().getARGBColor()
                || this.bbspp_cml$outlineWidth != holder.bbspp_cml$getOutlineWidth()
                || this.bbspp_cml$outlineGlow != holder.bbspp_cml$getOutlineGlow()
                || this.bbspp_cml$outlineDecalSize != holder.bbspp_cml$getOutlineDecalSize())
            {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "identity()V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$identityTexture(CallbackInfo ci)
    {
        this.bbspp_cml$texture = null;
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
        this.bbspp_cml$outlineDecalSize = BoneOutlineHolder.DEFAULT_DECAL;
    }

    @Inject(
        method = "lerp(Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/interps/IInterp;F)V",
        at = @At("TAIL"),
        remap = false
    )
    private void bbspp_cml$lerpTexture(mchorse.bbs_mod.utils.pose.Transform preA, mchorse.bbs_mod.utils.pose.Transform a, mchorse.bbs_mod.utils.pose.Transform b, mchorse.bbs_mod.utils.pose.Transform postB, mchorse.bbs_mod.utils.interps.IInterp interp, float x, CallbackInfo ci)
    {
        this.bbspp_cml$texture = LinkUtils.copy(bbspp_cml$closestTexture(a, b, x));
        this.bbspp_cml$glint = bbspp_cml$closestGlint(a, b, x);

        /* 光效颜色与调色一样可插值（照抄 textureTint 的实现）。 */
        if (preA instanceof GlintHolder preG && a instanceof GlintHolder curG
            && b instanceof GlintHolder nextG && postB instanceof GlintHolder postG)
        {
            Color preC = preG.bbspp_cml$getGlintColor();
            Color curC = curG.bbspp_cml$getGlintColor();
            Color nextC = nextG.bbspp_cml$getGlintColor();
            Color postC = postG.bbspp_cml$getGlintColor();

            this.bbspp_cml$glintColor.set(
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preC.r, curC.r, nextC.r, postC.r, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preC.g, curC.g, nextC.g, postC.g, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preC.b, curC.b, nextC.b, postC.b, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preC.a, curC.a, nextC.a, postC.a, x)), 0F, 1F)
            );
        }

        /* 光效速度/亮度是连续浮点，随关键帧插值。 */
        if (preA instanceof GlintSettingsHolder preS && a instanceof GlintSettingsHolder curS
            && b instanceof GlintSettingsHolder nextS && postB instanceof GlintSettingsHolder postS)
        {
            this.bbspp_cml$glintSpeed = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(
                preS.bbspp_cml$getGlintSpeed(), curS.bbspp_cml$getGlintSpeed(),
                nextS.bbspp_cml$getGlintSpeed(), postS.bbspp_cml$getGlintSpeed(), x)), 0F, GlintSettingsHolder.MAX_SPEED);
            this.bbspp_cml$glintBrightness = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(
                preS.bbspp_cml$getGlintBrightness(), curS.bbspp_cml$getGlintBrightness(),
                nextS.bbspp_cml$getGlintBrightness(), postS.bbspp_cml$getGlintBrightness(), x)), 0F, GlintSettingsHolder.MAX_BRIGHTNESS);
        }

        /* PBR 五值是连续浮点，可插值（与 glintColor 同构）。 */
        if (preA instanceof BonePbrHolder preP && a instanceof BonePbrHolder curP
            && b instanceof BonePbrHolder nextP && postB instanceof BonePbrHolder postP)
        {
            this.bbspp_cml$pbrSmoothness = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preP.bbspp_cml$getSmoothness(), curP.bbspp_cml$getSmoothness(), nextP.bbspp_cml$getSmoothness(), postP.bbspp_cml$getSmoothness(), x)), 0F, 1F);
            this.bbspp_cml$pbrMetallic = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preP.bbspp_cml$getMetallic(), curP.bbspp_cml$getMetallic(), nextP.bbspp_cml$getMetallic(), postP.bbspp_cml$getMetallic(), x)), 0F, 1F);
            this.bbspp_cml$pbrSss = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preP.bbspp_cml$getSss(), curP.bbspp_cml$getSss(), nextP.bbspp_cml$getSss(), postP.bbspp_cml$getSss(), x)), 0F, 1F);
            this.bbspp_cml$pbrEmission = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preP.bbspp_cml$getEmission(), curP.bbspp_cml$getEmission(), nextP.bbspp_cml$getEmission(), postP.bbspp_cml$getEmission(), x)), 0F, 1F);
            this.bbspp_cml$pbrRelief = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preP.bbspp_cml$getRelief(), curP.bbspp_cml$getRelief(), nextP.bbspp_cml$getRelief(), postP.bbspp_cml$getRelief(), x)), 0F, 1F);
        }

        /* 描边开关按最近关键帧，颜色/宽度/辉光/细化可插值。 */
        this.bbspp_cml$outline = bbspp_cml$closestOutline(a, b, x);

        if (preA instanceof BoneOutlineHolder preO && a instanceof BoneOutlineHolder curO
            && b instanceof BoneOutlineHolder nextO && postB instanceof BoneOutlineHolder postO)
        {
            Color preC = preO.bbspp_cml$getOutlineColor();
            Color curC = curO.bbspp_cml$getOutlineColor();
            Color nextC = nextO.bbspp_cml$getOutlineColor();
            Color postC = postO.bbspp_cml$getOutlineColor();

            this.bbspp_cml$outlineColor.set(
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preC.r, curC.r, nextC.r, postC.r, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preC.g, curC.g, nextC.g, postC.g, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preC.b, curC.b, nextC.b, postC.b, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preC.a, curC.a, nextC.a, postC.a, x)), 0F, 1F)
            );

            this.bbspp_cml$outlineWidth = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(
                preO.bbspp_cml$getOutlineWidth(), curO.bbspp_cml$getOutlineWidth(),
                nextO.bbspp_cml$getOutlineWidth(), postO.bbspp_cml$getOutlineWidth(), x)), 0F, 10F);
            this.bbspp_cml$outlineGlow = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(
                preO.bbspp_cml$getOutlineGlow(), curO.bbspp_cml$getOutlineGlow(),
                nextO.bbspp_cml$getOutlineGlow(), postO.bbspp_cml$getOutlineGlow(), x)), 0F, 255F);
            this.bbspp_cml$outlineDecalSize = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(
                preO.bbspp_cml$getOutlineDecalSize(), curO.bbspp_cml$getOutlineDecalSize(),
                nextO.bbspp_cml$getOutlineDecalSize(), postO.bbspp_cml$getOutlineDecalSize(), x)), 0F, 16F);
        }
    }

    @Inject(
        method = "autoLerp(Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/pose/Transform;FFFFZF)V",
        at = @At("TAIL"),
        remap = false
    )
    private void bbspp_cml$autoLerpTexture(mchorse.bbs_mod.utils.pose.Transform preA, mchorse.bbs_mod.utils.pose.Transform a, mchorse.bbs_mod.utils.pose.Transform b, mchorse.bbs_mod.utils.pose.Transform postB, float pt, float at, float bt, float qt, boolean clamped, float x, CallbackInfo ci)
    {
        this.bbspp_cml$texture = LinkUtils.copy(bbspp_cml$closestTexture(a, b, x));
        this.bbspp_cml$glint = bbspp_cml$closestGlint(a, b, x);

        /* 光效颜色与调色一样可插值（照抄 textureTint 的 AutoBezier 实现）。 */
        if (preA instanceof GlintHolder preG && a instanceof GlintHolder curG
            && b instanceof GlintHolder nextG && postB instanceof GlintHolder postG)
        {
            Color preC = preG.bbspp_cml$getGlintColor();
            Color curC = curG.bbspp_cml$getGlintColor();
            Color nextC = nextG.bbspp_cml$getGlintColor();
            Color postC = postG.bbspp_cml$getGlintColor();

            this.bbspp_cml$glintColor.set(
                (float) MathUtils.clamp(AutoBezier.get(preC.r, curC.r, nextC.r, postC.r, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preC.g, curC.g, nextC.g, postC.g, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preC.b, curC.b, nextC.b, postC.b, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preC.a, curC.a, nextC.a, postC.a, pt, at, bt, qt, clamped, x), 0F, 1F)
            );
        }

        /* 光效速度/亮度的 AutoBezier 插值。 */
        if (preA instanceof GlintSettingsHolder preS && a instanceof GlintSettingsHolder curS
            && b instanceof GlintSettingsHolder nextS && postB instanceof GlintSettingsHolder postS)
        {
            this.bbspp_cml$glintSpeed = (float) MathUtils.clamp(AutoBezier.get(
                preS.bbspp_cml$getGlintSpeed(), curS.bbspp_cml$getGlintSpeed(),
                nextS.bbspp_cml$getGlintSpeed(), postS.bbspp_cml$getGlintSpeed(),
                pt, at, bt, qt, clamped, x), 0F, GlintSettingsHolder.MAX_SPEED);
            this.bbspp_cml$glintBrightness = (float) MathUtils.clamp(AutoBezier.get(
                preS.bbspp_cml$getGlintBrightness(), curS.bbspp_cml$getGlintBrightness(),
                nextS.bbspp_cml$getGlintBrightness(), postS.bbspp_cml$getGlintBrightness(),
                pt, at, bt, qt, clamped, x), 0F, GlintSettingsHolder.MAX_BRIGHTNESS);
        }

        /* PBR 五值的 AutoBezier 插值。 */
        if (preA instanceof BonePbrHolder preP && a instanceof BonePbrHolder curP
            && b instanceof BonePbrHolder nextP && postB instanceof BonePbrHolder postP)
        {
            this.bbspp_cml$pbrSmoothness = (float) MathUtils.clamp(AutoBezier.get(preP.bbspp_cml$getSmoothness(), curP.bbspp_cml$getSmoothness(), nextP.bbspp_cml$getSmoothness(), postP.bbspp_cml$getSmoothness(), pt, at, bt, qt, clamped, x), 0F, 1F);
            this.bbspp_cml$pbrMetallic = (float) MathUtils.clamp(AutoBezier.get(preP.bbspp_cml$getMetallic(), curP.bbspp_cml$getMetallic(), nextP.bbspp_cml$getMetallic(), postP.bbspp_cml$getMetallic(), pt, at, bt, qt, clamped, x), 0F, 1F);
            this.bbspp_cml$pbrSss = (float) MathUtils.clamp(AutoBezier.get(preP.bbspp_cml$getSss(), curP.bbspp_cml$getSss(), nextP.bbspp_cml$getSss(), postP.bbspp_cml$getSss(), pt, at, bt, qt, clamped, x), 0F, 1F);
            this.bbspp_cml$pbrEmission = (float) MathUtils.clamp(AutoBezier.get(preP.bbspp_cml$getEmission(), curP.bbspp_cml$getEmission(), nextP.bbspp_cml$getEmission(), postP.bbspp_cml$getEmission(), pt, at, bt, qt, clamped, x), 0F, 1F);
            this.bbspp_cml$pbrRelief = (float) MathUtils.clamp(AutoBezier.get(preP.bbspp_cml$getRelief(), curP.bbspp_cml$getRelief(), nextP.bbspp_cml$getRelief(), postP.bbspp_cml$getRelief(), pt, at, bt, qt, clamped, x), 0F, 1F);
        }

        /* 描边开关按最近关键帧，颜色/宽度/辉光/细化可插值。 */
        this.bbspp_cml$outline = bbspp_cml$closestOutline(a, b, x);

        if (preA instanceof BoneOutlineHolder preO && a instanceof BoneOutlineHolder curO
            && b instanceof BoneOutlineHolder nextO && postB instanceof BoneOutlineHolder postO)
        {
            Color preC = preO.bbspp_cml$getOutlineColor();
            Color curC = curO.bbspp_cml$getOutlineColor();
            Color nextC = nextO.bbspp_cml$getOutlineColor();
            Color postC = postO.bbspp_cml$getOutlineColor();

            this.bbspp_cml$outlineColor.set(
                (float) MathUtils.clamp(AutoBezier.get(preC.r, curC.r, nextC.r, postC.r, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preC.g, curC.g, nextC.g, postC.g, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preC.b, curC.b, nextC.b, postC.b, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preC.a, curC.a, nextC.a, postC.a, pt, at, bt, qt, clamped, x), 0F, 1F)
            );

            this.bbspp_cml$outlineWidth = (float) MathUtils.clamp(AutoBezier.get(
                preO.bbspp_cml$getOutlineWidth(), curO.bbspp_cml$getOutlineWidth(),
                nextO.bbspp_cml$getOutlineWidth(), postO.bbspp_cml$getOutlineWidth(),
                pt, at, bt, qt, clamped, x), 0F, 10F);
            this.bbspp_cml$outlineGlow = (float) MathUtils.clamp(AutoBezier.get(
                preO.bbspp_cml$getOutlineGlow(), curO.bbspp_cml$getOutlineGlow(),
                nextO.bbspp_cml$getOutlineGlow(), postO.bbspp_cml$getOutlineGlow(),
                pt, at, bt, qt, clamped, x), 0F, 255F);
            this.bbspp_cml$outlineDecalSize = (float) MathUtils.clamp(AutoBezier.get(
                preO.bbspp_cml$getOutlineDecalSize(), curO.bbspp_cml$getOutlineDecalSize(),
                nextO.bbspp_cml$getOutlineDecalSize(), postO.bbspp_cml$getOutlineDecalSize(),
                pt, at, bt, qt, clamped, x), 0F, 16F);
        }
    }

    @Inject(method = "add(Lmchorse/bbs_mod/utils/pose/Transform;)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$addTexture(mchorse.bbs_mod.utils.pose.Transform transform, CallbackInfo ci)
    {
        if (transform instanceof BoneTextureHolder holder)
        {
            Link texture = holder.bbspp_cml$getTexture();

            if (texture != null)
            {
                this.bbspp_cml$texture = LinkUtils.copy(texture);
            }
        }

        /* 叠加语义（对齐 texture 的"源有则覆盖"）：源开了光效就打开，
         * 源的颜色非默认白则覆盖 —— 只开过颜色但没开开关的源不会污染这里。 */
        if (transform instanceof GlintHolder holder)
        {
            if (holder.bbspp_cml$getGlint())
            {
                this.bbspp_cml$glint = true;
            }

            Color glintColor = holder.bbspp_cml$getGlintColor();

            if (glintColor != null && glintColor.getARGBColor() != 0xFFFFFFFF)
            {
                this.bbspp_cml$glintColor.copy(glintColor);
            }
        }

        if (transform instanceof GlintSettingsHolder holder)
        {
            if (holder.bbspp_cml$getGlintSpeed() != GlintSettingsHolder.DEFAULT_SPEED)
            {
                this.bbspp_cml$glintSpeed = holder.bbspp_cml$getGlintSpeed();
            }
            if (holder.bbspp_cml$getGlintBrightness() != GlintSettingsHolder.DEFAULT_BRIGHTNESS)
            {
                this.bbspp_cml$glintBrightness = holder.bbspp_cml$getGlintBrightness();
            }
        }

        /* PBR 叠加：源任一值 > 0 则覆盖（与材质级 hasPbr 语义一致）。 */
        if (transform instanceof BonePbrHolder holder)
        {
            if (holder.bbspp_cml$getSmoothness() > 0F) this.bbspp_cml$pbrSmoothness = holder.bbspp_cml$getSmoothness();
            if (holder.bbspp_cml$getMetallic() > 0F)  this.bbspp_cml$pbrMetallic = holder.bbspp_cml$getMetallic();
            if (holder.bbspp_cml$getSss() > 0F)       this.bbspp_cml$pbrSss = holder.bbspp_cml$getSss();
            if (holder.bbspp_cml$getEmission() > 0F)  this.bbspp_cml$pbrEmission = holder.bbspp_cml$getEmission();
            if (holder.bbspp_cml$getRelief() > 0F)    this.bbspp_cml$pbrRelief = holder.bbspp_cml$getRelief();
        }

        /* 描边叠加：源开启则连同参数一起覆盖。 */
        if (transform instanceof BoneOutlineHolder holder && holder.bbspp_cml$getOutline())
        {
            this.bbspp_cml$outline = true;
            this.bbspp_cml$outlineColor.copy(holder.bbspp_cml$getOutlineColor());
            this.bbspp_cml$outlineWidth = holder.bbspp_cml$getOutlineWidth();
            this.bbspp_cml$outlineGlow = holder.bbspp_cml$getOutlineGlow();
            this.bbspp_cml$outlineDecalSize = holder.bbspp_cml$getOutlineDecalSize();
        }
    }

    @Unique
    private static Link bbspp_cml$closestTexture(mchorse.bbs_mod.utils.pose.Transform a, mchorse.bbs_mod.utils.pose.Transform b, float x)
    {
        mchorse.bbs_mod.utils.pose.Transform closest = x < 0.5F ? a : b;

        return closest instanceof BoneTextureHolder holder ? holder.bbspp_cml$getTexture() : null;
    }

    /** 布尔量不可插值，与纹理同样取"最近关键帧"的值。 */
    @Unique
    private static boolean bbspp_cml$closestGlint(mchorse.bbs_mod.utils.pose.Transform a, mchorse.bbs_mod.utils.pose.Transform b, float x)
    {
        mchorse.bbs_mod.utils.pose.Transform closest = x < 0.5F ? a : b;

        return closest instanceof GlintHolder holder && holder.bbspp_cml$getGlint();
    }

    /** 描边开关同样取"最近关键帧"的值。 */
    @Unique
    private static boolean bbspp_cml$closestOutline(mchorse.bbs_mod.utils.pose.Transform a, mchorse.bbs_mod.utils.pose.Transform b, float x)
    {
        mchorse.bbs_mod.utils.pose.Transform closest = x < 0.5F ? a : b;

        return closest instanceof BoneOutlineHolder holder && holder.bbspp_cml$getOutline();
    }
}
