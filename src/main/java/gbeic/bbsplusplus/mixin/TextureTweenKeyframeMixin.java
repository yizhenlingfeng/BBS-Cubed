package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.keyframes.ITextureTweenKeyframe;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 为 BBS 关键帧附加 BBSPPP 的纹理补间数据。
 *
 * <p>原版关键帧只保存纹理值和通用插值信息；该 Mixin 负责保存补间模式、三维扩散起点与闪光颜色，
 * 并在关键帧复制、粘贴和覆盖额外属性时同步这些段落级参数。</p>
 */
@Mixin(value = Keyframe.class, remap = false)
public class TextureTweenKeyframeMixin implements ITextureTweenKeyframe
{
    @Unique
    private static final int BBSPPP_LEGACY_TEXTURE_TWEEN_DISSIPATE_REVERSE = 4;

    @Unique
    private static final int BBSPPP_DEFAULT_TEXTURE_TWEEN_FLASH_COLOR = 0xffffffff;

    @Unique
    private int bbsppp$textureTweenMode = TEXTURE_TWEEN_OFF;

    @Unique
    private float bbsppp$textureTweenOriginX = 0.5F;

    @Unique
    private float bbsppp$textureTweenOriginY = 0.5F;

    @Unique
    private float bbsppp$textureTweenOriginZ = 0.5F;

    @Unique
    private boolean bbsppp$textureTweenFlash;

    @Unique
    private int bbsppp$textureTweenFlashColor = BBSPPP_DEFAULT_TEXTURE_TWEEN_FLASH_COLOR;

    @Unique
    private boolean bbsppp$textureTweenReverse;

    @Unique
    private int bbsppp$textureTweenBlockSize = 1;

    @Unique
    private boolean bbsppp$textureTweenPbrGlow;

    @Unique
    private int bbsppp$textureTweenPbrGlowStrength = 100;

    @Unique
    private int bbsppp$textureTweenDissipateIntensity = DEFAULT_DISSIPATE_INTENSITY;

    /**
     * 注入目标：{@link Keyframe#toData()} 返回数据后。
     * 注入原因：原版关键帧数据没有纹理补间参数。
     * 修改行为：仅对模型原生纹理轨道中启用了补间或修改过补间参数的关键帧写入扩展数据。
     */
    @Inject(method = "toData", at = @At("RETURN"), cancellable = true)
    private void bbsppp$writeTextureTween(CallbackInfoReturnable<BaseType> cir)
    {
        if (this.bbsppp$isModelTextureTrack() && this.bbsppp$hasTextureTweenData()
            && cir.getReturnValue() instanceof MapType map)
        {
            map.putInt("bbsppp_texture_tween_mode", this.bbsppp$textureTweenMode);
            map.putFloat("bbsppp_texture_tween_origin_x", this.bbsppp$textureTweenOriginX);
            map.putFloat("bbsppp_texture_tween_origin_y", this.bbsppp$textureTweenOriginY);
            map.putFloat("bbsppp_texture_tween_origin_z", this.bbsppp$textureTweenOriginZ);
            map.putBool("bbsppp_texture_tween_flash", this.bbsppp$textureTweenFlash);
            map.putInt("bbsppp_texture_tween_flash_color", this.bbsppp$textureTweenFlashColor);
            map.putBool("bbsppp_texture_tween_reverse", this.bbsppp$textureTweenReverse);
            map.putInt("bbsppp_texture_tween_block_size", this.bbsppp$textureTweenBlockSize);
            map.putBool("bbsppp_texture_tween_pbr_glow", this.bbsppp$textureTweenPbrGlow);
            map.putInt("bbsppp_texture_tween_pbr_glow_strength", this.bbsppp$textureTweenPbrGlowStrength);
            map.putInt("bbsppp_texture_tween_dissipate_intensity", this.bbsppp$textureTweenDissipateIntensity);
        }
    }

    /**
     * 注入目标：{@link Keyframe#fromData(BaseType)} 读取完成后。
     * 注入原因：需要恢复 BBSPPP 保存的纹理补间参数。
     * 修改行为：读取模式与扩散起点；没有扩展字段的原生纹理关键帧保持关闭补间。
     */
    @Inject(method = "fromData", at = @At("TAIL"))
    private void bbsppp$readTextureTween(BaseType data, CallbackInfo ci)
    {
        this.bbsppp$textureTweenMode = TEXTURE_TWEEN_OFF;
        this.bbsppp$textureTweenOriginX = 0.5F;
        this.bbsppp$textureTweenOriginY = 0.5F;
        this.bbsppp$textureTweenOriginZ = 0.5F;
        this.bbsppp$textureTweenFlash = false;
        this.bbsppp$textureTweenFlashColor = BBSPPP_DEFAULT_TEXTURE_TWEEN_FLASH_COLOR;
        this.bbsppp$textureTweenReverse = false;
        this.bbsppp$textureTweenBlockSize = 1;
        this.bbsppp$textureTweenPbrGlow = false;
        this.bbsppp$textureTweenPbrGlowStrength = 100;
        this.bbsppp$textureTweenDissipateIntensity = DEFAULT_DISSIPATE_INTENSITY;

        if (data instanceof MapType map)
        {
            if (map.has("bbsppp_texture_tween_mode"))
            {
                int mode = map.getInt("bbsppp_texture_tween_mode");

                this.bbsppp$textureTweenMode = mode == BBSPPP_LEGACY_TEXTURE_TWEEN_DISSIPATE_REVERSE ? TEXTURE_TWEEN_DISSIPATE : normalizeTextureTweenMode(mode);
                this.bbsppp$textureTweenReverse = mode == BBSPPP_LEGACY_TEXTURE_TWEEN_DISSIPATE_REVERSE;
            }

            this.bbsppp$textureTweenOriginX = clampOrigin(map.getFloat("bbsppp_texture_tween_origin_x", 0.5F));
            this.bbsppp$textureTweenOriginY = clampOrigin(map.getFloat("bbsppp_texture_tween_origin_y", 0.5F));
            this.bbsppp$textureTweenOriginZ = clampOrigin(map.getFloat("bbsppp_texture_tween_origin_z", 0.5F));
            this.bbsppp$textureTweenFlash = map.getBool("bbsppp_texture_tween_flash", false);
            this.bbsppp$textureTweenFlashColor = normalizeTextureTweenFlashColor(
                map.getInt("bbsppp_texture_tween_flash_color", BBSPPP_DEFAULT_TEXTURE_TWEEN_FLASH_COLOR)
            );
            this.bbsppp$textureTweenReverse = this.bbsppp$textureTweenReverse || map.getBool("bbsppp_texture_tween_reverse", false);
            this.bbsppp$textureTweenBlockSize = normalizeTextureTweenBlockSize(map.getInt("bbsppp_texture_tween_block_size", 1));
            this.bbsppp$textureTweenPbrGlow = map.getBool("bbsppp_texture_tween_pbr_glow", false);
            this.bbsppp$textureTweenPbrGlowStrength = normalizeTextureTweenPbrGlowStrength(
                map.getInt("bbsppp_texture_tween_pbr_glow_strength", 100)
            );
            this.bbsppp$textureTweenDissipateIntensity = normalizeTextureTweenDissipateIntensity(
                map.getInt("bbsppp_texture_tween_dissipate_intensity", DEFAULT_DISSIPATE_INTENSITY)
            );
        }
    }

    /**
     * 注入目标：{@link Keyframe#copy(Keyframe)} 复制完成后。
     * 注入原因：复制和粘贴关键帧时必须保留纹理补间参数。
     * 修改行为：从源关键帧同步模式与扩散起点。
     */
    @Inject(method = "copy", at = @At("TAIL"))
    private void bbsppp$copyTextureTween(Keyframe<?> keyframe, CallbackInfo ci)
    {
        this.bbsppp$copyTextureTweenFrom(keyframe);
    }

    /**
     * 注入目标：{@link Keyframe#copyOverExtra(Keyframe)} 复制额外属性完成后。
     * 注入原因：纹理补间参数与插值、形状一样属于关键帧段落属性。
     * 修改行为：把源关键帧的模式与扩散起点覆盖到目标关键帧。
     */
    @Inject(method = "copyOverExtra", at = @At("TAIL"))
    private void bbsppp$copyOverTextureTween(Keyframe<?> keyframe, CallbackInfo ci)
    {
        this.bbsppp$copyTextureTweenFrom(keyframe);
    }

    /**
     * 注入目标：{@link Keyframe#equals(Object)} 原版字段比较完成后。
     * 注入原因：补间参数已经挂在原生纹理关键帧上，撤销快照和脏状态判断必须感知这些扩展字段。
     * 修改行为：原版字段相同时，继续比较完整纹理补间状态。
     */
    @Inject(method = "equals", at = @At("RETURN"), cancellable = true)
    private void bbsppp$compareTextureTween(Object obj, CallbackInfoReturnable<Boolean> cir)
    {
        if (!cir.getReturnValue() || !(obj instanceof ITextureTweenKeyframe tween))
        {
            return;
        }

        cir.setReturnValue(
            this.bbsppp$textureTweenMode == tween.bbsppp$getTextureTweenMode()
                && Float.compare(this.bbsppp$textureTweenOriginX, tween.bbsppp$getTextureTweenOriginX()) == 0
                && Float.compare(this.bbsppp$textureTweenOriginY, tween.bbsppp$getTextureTweenOriginY()) == 0
                && Float.compare(this.bbsppp$textureTweenOriginZ, tween.bbsppp$getTextureTweenOriginZ()) == 0
                && this.bbsppp$textureTweenFlash == tween.bbsppp$isTextureTweenFlashEnabled()
                && this.bbsppp$textureTweenFlashColor == tween.bbsppp$getTextureTweenFlashColor()
                && this.bbsppp$textureTweenReverse == tween.bbsppp$isTextureTweenReverseEnabled()
                && this.bbsppp$textureTweenBlockSize == tween.bbsppp$getTextureTweenBlockSize()
                && this.bbsppp$textureTweenPbrGlow == tween.bbsppp$isTextureTweenPbrGlowEnabled()
                && this.bbsppp$textureTweenPbrGlowStrength == tween.bbsppp$getTextureTweenPbrGlowStrength()
                && this.bbsppp$textureTweenDissipateIntensity == tween.bbsppp$getTextureTweenDissipateIntensity()
        );
    }

    @Unique
    private void bbsppp$copyTextureTweenFrom(Keyframe<?> keyframe)
    {
        if (keyframe instanceof ITextureTweenKeyframe tween)
        {
            this.bbsppp$textureTweenMode = tween.bbsppp$getTextureTweenMode();
            this.bbsppp$setTextureTweenOrigin(tween.bbsppp$getTextureTweenOriginX(), tween.bbsppp$getTextureTweenOriginY(),
                tween.bbsppp$getTextureTweenOriginZ());
            this.bbsppp$textureTweenFlash = tween.bbsppp$isTextureTweenFlashEnabled();
            this.bbsppp$textureTweenFlashColor = tween.bbsppp$getTextureTweenFlashColor();
            this.bbsppp$textureTweenReverse = tween.bbsppp$isTextureTweenReverseEnabled();
            this.bbsppp$textureTweenBlockSize = tween.bbsppp$getTextureTweenBlockSize();
            this.bbsppp$textureTweenPbrGlow = tween.bbsppp$isTextureTweenPbrGlowEnabled();
            this.bbsppp$textureTweenPbrGlowStrength = tween.bbsppp$getTextureTweenPbrGlowStrength();
            this.bbsppp$textureTweenDissipateIntensity = tween.bbsppp$getTextureTweenDissipateIntensity();
        }
        else
        {
            this.bbsppp$textureTweenMode = TEXTURE_TWEEN_OFF;
            this.bbsppp$setTextureTweenOrigin(0.5F, 0.5F, 0.5F);
            this.bbsppp$textureTweenFlash = false;
            this.bbsppp$textureTweenFlashColor = BBSPPP_DEFAULT_TEXTURE_TWEEN_FLASH_COLOR;
            this.bbsppp$textureTweenReverse = false;
            this.bbsppp$textureTweenBlockSize = 1;
            this.bbsppp$textureTweenPbrGlow = false;
            this.bbsppp$textureTweenPbrGlowStrength = 100;
            this.bbsppp$textureTweenDissipateIntensity = DEFAULT_DISSIPATE_INTENSITY;
        }
    }

    @Unique
    private boolean bbsppp$isModelTextureTrack()
    {
        Keyframe<?> keyframe = (Keyframe<?>) (Object) this;

        if (keyframe.getParent() == null)
        {
            return false;
        }

        String id = keyframe.getParent().getId();

        return "texture".equals(id) || id.endsWith("/texture");
    }

    @Unique
    private boolean bbsppp$hasTextureTweenData()
    {
        return this.bbsppp$textureTweenMode != TEXTURE_TWEEN_OFF
            || this.bbsppp$textureTweenOriginX != 0.5F
            || this.bbsppp$textureTweenOriginY != 0.5F
            || this.bbsppp$textureTweenOriginZ != 0.5F
            || this.bbsppp$textureTweenFlash
            || this.bbsppp$textureTweenFlashColor != BBSPPP_DEFAULT_TEXTURE_TWEEN_FLASH_COLOR
            || this.bbsppp$textureTweenReverse
            || this.bbsppp$textureTweenBlockSize != 1
            || this.bbsppp$textureTweenPbrGlow
            || this.bbsppp$textureTweenPbrGlowStrength != 100
            || this.bbsppp$textureTweenDissipateIntensity != DEFAULT_DISSIPATE_INTENSITY;
    }

    @Override
    public int bbsppp$getTextureTweenMode()
    {
        return this.bbsppp$textureTweenMode;
    }

    @Override
    public void bbsppp$setTextureTweenMode(int mode)
    {
        this.bbsppp$textureTweenMode = normalizeTextureTweenMode(mode);
    }

    @Override
    public float bbsppp$getTextureTweenOriginX()
    {
        return this.bbsppp$textureTweenOriginX;
    }

    @Override
    public float bbsppp$getTextureTweenOriginY()
    {
        return this.bbsppp$textureTweenOriginY;
    }

    @Override
    public float bbsppp$getTextureTweenOriginZ()
    {
        return this.bbsppp$textureTweenOriginZ;
    }

    @Override
    public void bbsppp$setTextureTweenOrigin(float x, float y, float z)
    {
        this.bbsppp$textureTweenOriginX = clampOrigin(x);
        this.bbsppp$textureTweenOriginY = clampOrigin(y);
        this.bbsppp$textureTweenOriginZ = clampOrigin(z);
    }

    @Override
    public boolean bbsppp$isTextureTweenFlashEnabled()
    {
        return this.bbsppp$textureTweenFlash;
    }

    @Override
    public void bbsppp$setTextureTweenFlashEnabled(boolean enabled)
    {
        this.bbsppp$textureTweenFlash = enabled;
    }

    @Override
    public int bbsppp$getTextureTweenFlashColor()
    {
        return this.bbsppp$textureTweenFlashColor;
    }

    @Override
    public void bbsppp$setTextureTweenFlashColor(int color)
    {
        this.bbsppp$textureTweenFlashColor = normalizeTextureTweenFlashColor(color);
    }

    @Override
    public boolean bbsppp$isTextureTweenReverseEnabled()
    {
        return this.bbsppp$textureTweenReverse;
    }

    @Override
    public void bbsppp$setTextureTweenReverseEnabled(boolean enabled)
    {
        this.bbsppp$textureTweenReverse = enabled;
    }

    @Override
    public int bbsppp$getTextureTweenBlockSize()
    {
        return this.bbsppp$textureTweenBlockSize;
    }

    @Override
    public void bbsppp$setTextureTweenBlockSize(int size)
    {
        this.bbsppp$textureTweenBlockSize = normalizeTextureTweenBlockSize(size);
    }

    @Override
    public boolean bbsppp$isTextureTweenPbrGlowEnabled()
    {
        return this.bbsppp$textureTweenPbrGlow;
    }

    @Override
    public void bbsppp$setTextureTweenPbrGlowEnabled(boolean enabled)
    {
        this.bbsppp$textureTweenPbrGlow = enabled;
    }

    @Override
    public int bbsppp$getTextureTweenPbrGlowStrength()
    {
        return this.bbsppp$textureTweenPbrGlowStrength;
    }

    @Override
    public void bbsppp$setTextureTweenPbrGlowStrength(int strength)
    {
        this.bbsppp$textureTweenPbrGlowStrength = normalizeTextureTweenPbrGlowStrength(strength);
    }

    @Override
    public int bbsppp$getTextureTweenDissipateIntensity()
    {
        return this.bbsppp$textureTweenDissipateIntensity;
    }

    @Override
    public void bbsppp$setTextureTweenDissipateIntensity(int intensity)
    {
        this.bbsppp$textureTweenDissipateIntensity = normalizeTextureTweenDissipateIntensity(intensity);
    }

    @Unique
    private static int normalizeTextureTweenMode(int mode)
    {
        return mode >= TEXTURE_TWEEN_OFF && mode <= TEXTURE_TWEEN_DISSIPATE ? mode : TEXTURE_TWEEN_OFF;
    }

    @Unique
    private static int normalizeTextureTweenBlockSize(int size)
    {
        return switch (size)
        {
            case 3, 5, 7, 9 -> size;
            default -> 1;
        };
    }

    @Unique
    private static int normalizeTextureTweenPbrGlowStrength(int strength)
    {
        return Math.max(0, Math.min(100, strength));
    }

    @Unique
    private static int normalizeTextureTweenDissipateIntensity(int intensity)
    {
        return Math.max(0, Math.min(100, intensity));
    }

    @Unique
    private static int normalizeTextureTweenFlashColor(int color)
    {
        return color;
    }

    @Unique
    private static float clampOrigin(float value)
    {
        return Math.max(0F, Math.min(1F, value));
    }
}
