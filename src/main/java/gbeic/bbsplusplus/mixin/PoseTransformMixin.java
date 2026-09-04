package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.BoneTextureHolder;
import gbeic.bbsplusplus.api.TextureGradeHolder;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
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
 */
@Mixin(value = PoseTransform.class, remap = false)
public class PoseTransformMixin implements BoneTextureHolder, TextureGradeHolder
{
    @Unique
    private Link bbspp_cml$texture;

    @Unique
    private final Color bbspp_cml$textureTint = new Color(1F, 1F, 1F, 0F);

    @Unique
    private float bbspp_cml$textureWhiten;

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

    @Inject(method = "toData", at = @At("TAIL"), remap = false)
    private void bbspp_cml$writeTexture(MapType data, CallbackInfo ci)
    {
        if (this.bbspp_cml$texture != null)
        {
            data.put("texture", LinkUtils.toData(this.bbspp_cml$texture));
        }

        if (this.bbspp_cml$textureTint.a > 0F)
        {
            data.putInt("texture_tint", this.bbspp_cml$textureTint.getARGBColor());
        }
        if (this.bbspp_cml$textureWhiten > 0F)
        {
            data.putFloat("texture_whiten", this.bbspp_cml$textureWhiten);
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

        this.bbspp_cml$textureTint.set(data.getInt("texture_tint", 0x00ffffff));
        this.bbspp_cml$textureWhiten = MathUtils.clamp(data.getFloat("texture_whiten"), 0F, 1F);
    }

    @Inject(method = "copy(Lmchorse/bbs_mod/utils/pose/Transform;)V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$copyTexture(mchorse.bbs_mod.utils.pose.Transform transform, CallbackInfo ci)
    {
        if (transform instanceof BoneTextureHolder holder)
        {
            this.bbspp_cml$texture = LinkUtils.copy(holder.bbspp_cml$getTexture());
        }

        if (transform instanceof TextureGradeHolder holder)
        {
            this.bbspp_cml$textureTint.copy(holder.bbspp_cml$getTextureTint());
            this.bbspp_cml$textureWhiten = holder.bbspp_cml$getTextureWhiten();
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

        if (cir.getReturnValueZ() && obj instanceof TextureGradeHolder holder)
        {
            if (!this.bbspp_cml$textureTint.equals(holder.bbspp_cml$getTextureTint())
                || this.bbspp_cml$textureWhiten != holder.bbspp_cml$getTextureWhiten())
            {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "identity()V", at = @At("TAIL"), remap = false)
    private void bbspp_cml$identityTexture(CallbackInfo ci)
    {
        this.bbspp_cml$texture = null;
        this.bbspp_cml$textureTint.set(Colors.WHITE);
        this.bbspp_cml$textureTint.a = 0F;
        this.bbspp_cml$textureWhiten = 0F;
    }

    @Inject(
        method = "lerp(Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/pose/Transform;Lmchorse/bbs_mod/utils/interps/IInterp;F)V",
        at = @At("TAIL"),
        remap = false
    )
    private void bbspp_cml$lerpTexture(mchorse.bbs_mod.utils.pose.Transform preA, mchorse.bbs_mod.utils.pose.Transform a, mchorse.bbs_mod.utils.pose.Transform b, mchorse.bbs_mod.utils.pose.Transform postB, mchorse.bbs_mod.utils.interps.IInterp interp, float x, CallbackInfo ci)
    {
        this.bbspp_cml$texture = LinkUtils.copy(bbspp_cml$closestTexture(a, b, x));

        if (preA instanceof TextureGradeHolder pre && a instanceof TextureGradeHolder current
            && b instanceof TextureGradeHolder next && postB instanceof TextureGradeHolder post)
        {
            Color preTint = pre.bbspp_cml$getTextureTint();
            Color currentTint = current.bbspp_cml$getTextureTint();
            Color nextTint = next.bbspp_cml$getTextureTint();
            Color postTint = post.bbspp_cml$getTextureTint();

            this.bbspp_cml$textureTint.set(
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preTint.r, currentTint.r, nextTint.r, postTint.r, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preTint.g, currentTint.g, nextTint.g, postTint.g, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preTint.b, currentTint.b, nextTint.b, postTint.b, x)), 0F, 1F),
                (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(preTint.a, currentTint.a, nextTint.a, postTint.a, x)), 0F, 1F)
            );
            this.bbspp_cml$textureWhiten = (float) MathUtils.clamp(interp.interpolate(IInterp.context.set(
                pre.bbspp_cml$getTextureWhiten(), current.bbspp_cml$getTextureWhiten(),
                next.bbspp_cml$getTextureWhiten(), post.bbspp_cml$getTextureWhiten(), x)), 0F, 1F);
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

        if (preA instanceof TextureGradeHolder pre && a instanceof TextureGradeHolder current
            && b instanceof TextureGradeHolder next && postB instanceof TextureGradeHolder post)
        {
            Color preTint = pre.bbspp_cml$getTextureTint();
            Color currentTint = current.bbspp_cml$getTextureTint();
            Color nextTint = next.bbspp_cml$getTextureTint();
            Color postTint = post.bbspp_cml$getTextureTint();

            this.bbspp_cml$textureTint.set(
                (float) MathUtils.clamp(AutoBezier.get(preTint.r, currentTint.r, nextTint.r, postTint.r, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preTint.g, currentTint.g, nextTint.g, postTint.g, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preTint.b, currentTint.b, nextTint.b, postTint.b, pt, at, bt, qt, clamped, x), 0F, 1F),
                (float) MathUtils.clamp(AutoBezier.get(preTint.a, currentTint.a, nextTint.a, postTint.a, pt, at, bt, qt, clamped, x), 0F, 1F)
            );
            this.bbspp_cml$textureWhiten = (float) MathUtils.clamp(AutoBezier.get(
                pre.bbspp_cml$getTextureWhiten(), current.bbspp_cml$getTextureWhiten(),
                next.bbspp_cml$getTextureWhiten(), post.bbspp_cml$getTextureWhiten(),
                pt, at, bt, qt, clamped, x), 0F, 1F);
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

        if (transform instanceof TextureGradeHolder holder)
        {
            Color tint = holder.bbspp_cml$getTextureTint();

            if (tint.a > 0F)
            {
                this.bbspp_cml$textureTint.copy(tint);
            }

            this.bbspp_cml$textureWhiten = MathUtils.clamp(
                this.bbspp_cml$textureWhiten + holder.bbspp_cml$getTextureWhiten(), 0F, 1F);
        }
    }

    @Unique
    private static Link bbspp_cml$closestTexture(mchorse.bbs_mod.utils.pose.Transform a, mchorse.bbs_mod.utils.pose.Transform b, float x)
    {
        mchorse.bbs_mod.utils.pose.Transform closest = x < 0.5F ? a : b;

        return closest instanceof BoneTextureHolder holder ? holder.bbspp_cml$getTexture() : null;
    }
}
