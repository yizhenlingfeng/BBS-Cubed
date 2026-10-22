package gbeic.bbsplusplus.client.ui.film;

/**
 * 影片库 UI 动效使用的缓动函数。
 */
public final class UIEase
{
    private UIEase()
    {
    }

    public static float clamp01(float value)
    {
        return Math.max(0F, Math.min(1F, value));
    }

    public static float inOutCubic(float value)
    {
        value = clamp01(value);

        return value < 0.5F
            ? 4F * value * value * value
            : 1F - (float) Math.pow(-2F * value + 2F, 3F) / 2F;
    }

    public static float outCubic(float value)
    {
        value = clamp01(value);

        return 1F - (float) Math.pow(1F - value, 3F);
    }

    public static float inCubic(float value)
    {
        value = clamp01(value);

        return value * value * value;
    }
}
