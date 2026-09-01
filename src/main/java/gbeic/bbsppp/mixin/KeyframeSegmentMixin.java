package gbeic.bbsppp.mixin;

import gbeic.bbsppp.client.texture.TextureTweenManager;
import gbeic.bbsppp.keyframes.ITextureTweenKeyframe;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.resources.MultiLink;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static gbeic.bbsppp.keyframes.ITextureTweenKeyframe.TEXTURE_TWEEN_DISSIPATE;

/**
 * 纹理关键帧补间的播放时接入点。
 *
 * <p>注入目标是 {@link KeyframeSegment#createInterpolated()}。原版 Link 关键帧只会硬切或按文件名数字选择序列帧；
 * BBSPPP 在当前段属于纹理补间专属轨道时，改为根据 A/B 两张贴图生成运行时临时纹理 Link。</p>
 */
@Mixin(value = KeyframeSegment.class, remap = false)
public class KeyframeSegmentMixin
{
    private static final int BBSPPP_TEXTURE_TWEEN_DISSIPATE_REVERSE = 4;

    @Shadow
    public Keyframe<?> a;

    @Shadow
    public Keyframe<?> b;

    @Shadow
    public Keyframe<?> preA;

    @Shadow
    public float x;

    /**
     * 注入目标：关键帧段生成插值结果的入口。
     * 注入原因：这里同时能拿到当前段的前后两个关键帧和插值进度，正好适合让补间专属轨道接管 Link 插值。
     * 修改行为：仅当轨道是 BBSPPP_texture_tween、值为普通 Link 时，返回 BBSPPP 运行时生成的中间纹理 Link。
     */
    @Inject(method = "createInterpolated", at = @At("HEAD"), cancellable = true)
    private void bbsppp$createTextureTween(CallbackInfoReturnable<Object> cir)
    {
        if (!isTextureTweenTrack(this.a))
        {
            return;
        }

        if (this.a == this.b)
        {
            this.bbsppp$holdDissipatedTexture(cir);

            return;
        }

        if (!(this.a instanceof ITextureTweenKeyframe tween)
            || tween.bbsppp$getTextureTweenMode() == ITextureTweenKeyframe.TEXTURE_TWEEN_OFF)
        {
            return;
        }

        Object valueA = this.a.getValue();
        Object valueB = this.b.getValue();

        if (!(valueA instanceof Link linkA) || !(valueB instanceof Link linkB) || linkA instanceof MultiLink || linkB instanceof MultiLink)
        {
            return;
        }

        float eased = this.a.getInterpolation().interpolate(0F, 1F, this.x);
        int mode = tween.bbsppp$getTextureTweenMode() == TEXTURE_TWEEN_DISSIPATE && tween.bbsppp$isTextureTweenReverseEnabled()
            ? BBSPPP_TEXTURE_TWEEN_DISSIPATE_REVERSE
            : tween.bbsppp$getTextureTweenMode();
        Link generated = TextureTweenManager.getTween(linkA, linkB, eased, mode,
            tween.bbsppp$getTextureTweenOriginX(), tween.bbsppp$getTextureTweenOriginY(), tween.bbsppp$getTextureTweenOriginZ(),
            tween.bbsppp$isTextureTweenFlashEnabled(), tween.bbsppp$getTextureTweenFlashColor(),
            tween.bbsppp$getTextureTweenBlockSize(),
            tween.bbsppp$isTextureTweenPbrGlowEnabled(), tween.bbsppp$getTextureTweenPbrGlowStrength(),
            tween.bbsppp$getTextureTweenDissipateIntensity());

        if (generated != null)
        {
            cir.setReturnValue(generated);
        }
    }

    /**
     * 注入目标：最后一个关键帧之后的保持段。
     * 注入原因：BBS 对最后一个关键帧会返回 {@code a == b} 的保持段，原版会直接保持 B 关键帧的贴图。
     * 修改行为：如果前一段是消散模式，则保持消散完成后的透明运行时贴图，避免播放头越过 B 点后贴图反弹。
     */
    private void bbsppp$holdDissipatedTexture(CallbackInfoReturnable<Object> cir)
    {
        if (this.preA == null || this.preA == this.a || !(this.preA instanceof ITextureTweenKeyframe tween) || tween.bbsppp$getTextureTweenMode() != TEXTURE_TWEEN_DISSIPATE
            || tween.bbsppp$isTextureTweenReverseEnabled())
        {
            return;
        }

        Object valueA = this.preA.getValue();
        Object valueB = this.a.getValue();

        if (!(valueA instanceof Link linkA) || !(valueB instanceof Link linkB) || linkA instanceof MultiLink || linkB instanceof MultiLink)
        {
            return;
        }

        Link generated = TextureTweenManager.getTween(linkA, linkB, 1F, TEXTURE_TWEEN_DISSIPATE,
            tween.bbsppp$getTextureTweenOriginX(), tween.bbsppp$getTextureTweenOriginY(), tween.bbsppp$getTextureTweenOriginZ(),
            tween.bbsppp$isTextureTweenFlashEnabled(), tween.bbsppp$getTextureTweenFlashColor(),
            tween.bbsppp$getTextureTweenBlockSize(),
            tween.bbsppp$isTextureTweenPbrGlowEnabled(), tween.bbsppp$getTextureTweenPbrGlowStrength(),
            tween.bbsppp$getTextureTweenDissipateIntensity());

        if (generated != null)
        {
            cir.setReturnValue(generated);
        }
    }

    private static boolean isTextureTweenTrack(Keyframe<?> keyframe)
    {
        if (keyframe.getParent() == null)
        {
            return false;
        }

        String id = keyframe.getParent().getId();

        return "texture".equals(id) || id.endsWith("/texture");
    }
}
