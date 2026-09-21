package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.BonePbrHolder;
import gbeic.bbsplusplus.api.BoneTextureHolder;
import gbeic.bbsplusplus.api.GlintHolder;
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
 * 给 {@link PoseTransform} 增加"骨骼纹理"链接字段（内存期）及其序列化/拷贝/相等钩子，
 * 与 BBScml 的 PoseTransform.texture 对齐。
 *
 * <p>注入点：
 * <ul>
 *   <li>{@code toData} TAIL —— 把 texture 写进 data（"texture" 键）；</li>
 *   <li>{@code fromData} TAIL —— 从 data 读取 texture（不存在则置 null）；</li>
 *   <li>{@code copy} TAIL —— 从源 transform 拷贝 texture；</li>
 *   <li>{@code equals} RETURN —— 原判定相等时再比较 texture（对齐 CML），
 *       防止"仅纹理不同"的两个 Pose 在值比较/关键帧去重场景被误判相等。</li>
 *   <li>{@code identity} TAIL —— 置空 texture（对齐 CML identity；同时防止
 *       PoseKeyframeFactory 共享插值缓存中残留上一次的纹理）；</li>
 *   <li>{@code lerp/autoLerp} TAIL —— 纹理不可插值，取"最近关键帧"的值
 *       （x&lt;0.5 取 a，否则取 b），等效 CML 运行期 getClosest 语义，使
 *       pose 关键帧轨道回放时骨骼纹理生效；</li>
 *   <li>{@code add} TAIL —— 叠加语义：源带纹理则覆盖（per-limb 骨骼通道
 *       应用链 FormProperties {@code transform.add(interpolated)} 的传播点）。</li>
 * </ul></p>
 *
 * <p>"附魔光效"（{@link GlintHolder}）与纹理走<b>完全相同的 8 处钩子</b>，唯一差别是
 * 布尔量不可插值：lerp/autoLerp 同样按"最近关键帧"取值。漏掉其中任意一处
 * （尤其是 equals 与 lerp）都会表现为"关键帧上光效闪烁或莫名丢失"。</p>
 */
@Mixin(value = PoseTransform.class, remap = false)
public class PoseTransformMixin implements BoneTextureHolder, GlintHolder, BonePbrHolder
{
    @Unique
    private Link bbspp_cml$texture;

    @Unique
    private boolean bbspp_cml$glint;

    /** 光效颜色，默认白色 = 原版观感。alpha 通道会一并乘进最终亮度。 */
    @Unique
    private final Color bbspp_cml$glintColor = new Color(1F, 1F, 1F, 1F);

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

        if (this.bbspp_cml$pbrSmoothness != 0F) data.putFloat("pbr_smoothness", this.bbspp_cml$pbrSmoothness);
        if (this.bbspp_cml$pbrMetallic != 0F)  data.putFloat("pbr_metallic", this.bbspp_cml$pbrMetallic);
        if (this.bbspp_cml$pbrSss != 0F)       data.putFloat("pbr_sss", this.bbspp_cml$pbrSss);
        if (this.bbspp_cml$pbrEmission != 0F)  data.putFloat("pbr_emission", this.bbspp_cml$pbrEmission);
        if (this.bbspp_cml$pbrRelief != 0F)    data.putFloat("pbr_relief", this.bbspp_cml$pbrRelief);
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

        this.bbspp_cml$pbrSmoothness = data.getFloat("pbr_smoothness");
        this.bbspp_cml$pbrMetallic = data.getFloat("pbr_metallic");
        this.bbspp_cml$pbrSss = data.getFloat("pbr_sss");
        this.bbspp_cml$pbrEmission = data.getFloat("pbr_emission");
        this.bbspp_cml$pbrRelief = data.getFloat("pbr_relief");
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

        if (transform instanceof BonePbrHolder holder)
        {
            this.bbspp_cml$pbrSmoothness = holder.bbspp_cml$getSmoothness();
            this.bbspp_cml$pbrMetallic = holder.bbspp_cml$getMetallic();
            this.bbspp_cml$pbrSss = holder.bbspp_cml$getSss();
            this.bbspp_cml$pbrEmission = holder.bbspp_cml$getEmission();
            this.bbspp_cml$pbrRelief = holder.bbspp_cml$getRelief();
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
    }

    @Inject(method = "identity()V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$identityTexture(CallbackInfo ci)
    {
        this.bbspp_cml$texture = null;
        this.bbspp_cml$glint = false;
        this.bbspp_cml$glintColor.set(0xFFFFFFFF);
        this.bbspp_cml$pbrSmoothness = 0F;
        this.bbspp_cml$pbrMetallic = 0F;
        this.bbspp_cml$pbrSss = 0F;
        this.bbspp_cml$pbrEmission = 0F;
        this.bbspp_cml$pbrRelief = 0F;
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

        /* PBR 叠加：源任一值 > 0 则覆盖（与材质级 hasPbr 语义一致）。 */
        if (transform instanceof BonePbrHolder holder)
        {
            if (holder.bbspp_cml$getSmoothness() > 0F) this.bbspp_cml$pbrSmoothness = holder.bbspp_cml$getSmoothness();
            if (holder.bbspp_cml$getMetallic() > 0F)  this.bbspp_cml$pbrMetallic = holder.bbspp_cml$getMetallic();
            if (holder.bbspp_cml$getSss() > 0F)       this.bbspp_cml$pbrSss = holder.bbspp_cml$getSss();
            if (holder.bbspp_cml$getEmission() > 0F)  this.bbspp_cml$pbrEmission = holder.bbspp_cml$getEmission();
            if (holder.bbspp_cml$getRelief() > 0F)    this.bbspp_cml$pbrRelief = holder.bbspp_cml$getRelief();
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
}
