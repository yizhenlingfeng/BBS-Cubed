package gbeic.bbsplusplus.keyframes;

/**
 * 纹理关键帧补间状态接口。
 *
 * <p>原版 {@code Keyframe<Link>} 只保存贴图本身，不保存“从当前关键帧到下一个关键帧如何过渡”的额外状态。
 * BBSPPP 通过 Mixin 让关键帧实现这个接口，把补间模式、扩散起点和闪光颜色作为关键帧自己的扩展数据序列化，
 * 同时避免影响非纹理用途的 Link 关键帧。</p>
 */
public interface ITextureTweenKeyframe
{
    public static final int TEXTURE_TWEEN_OFF = 0;
    public static final int TEXTURE_TWEEN_COLOR = 1;
    public static final int TEXTURE_TWEEN_PIXEL_DISSOLVE = 2;
    public static final int TEXTURE_TWEEN_DISSIPATE = 3;
    public static final int DEFAULT_DISSIPATE_INTENSITY = 50;

    public int bbsppp$getTextureTweenMode();

    public void bbsppp$setTextureTweenMode(int mode);

    public float bbsppp$getTextureTweenOriginX();

    public float bbsppp$getTextureTweenOriginY();

    public float bbsppp$getTextureTweenOriginZ();

    public default void bbsppp$setTextureTweenOrigin(float x, float y)
    {
        this.bbsppp$setTextureTweenOrigin(x, y, 0.5F);
    }

    public void bbsppp$setTextureTweenOrigin(float x, float y, float z);

    public boolean bbsppp$isTextureTweenFlashEnabled();

    public void bbsppp$setTextureTweenFlashEnabled(boolean enabled);

    public int bbsppp$getTextureTweenFlashColor();

    public void bbsppp$setTextureTweenFlashColor(int color);

    public boolean bbsppp$isTextureTweenReverseEnabled();

    public void bbsppp$setTextureTweenReverseEnabled(boolean enabled);

    public int bbsppp$getTextureTweenBlockSize();

    public void bbsppp$setTextureTweenBlockSize(int size);

    public boolean bbsppp$isTextureTweenPbrGlowEnabled();

    public void bbsppp$setTextureTweenPbrGlowEnabled(boolean enabled);

    public int bbsppp$getTextureTweenPbrGlowStrength();

    public void bbsppp$setTextureTweenPbrGlowStrength(int strength);

    public int bbsppp$getTextureTweenDissipateIntensity();

    public void bbsppp$setTextureTweenDissipateIntensity(int intensity);
}
