package gbeic.bbsplusplus.client.glint;

/**
 * 自建 shader 与 Iris glint 程序共用的光效相位。
 * 两个渲染后端使用相同的 phase 与 UV 平移，保证开关光影时流速一致。
 */
public final class GlintTiming
{
    /** 一个完整循环的时长。相位在 [0,1) 内循环。 */
    public static final long PERIOD_MS = 3000L;

    /** 相位 0→1 对应的 UV 平移量（两个轴相同，和 vsh 的 glint_uv 一致）。 */
    public static final float SHIFT = 4.0F;

    private GlintTiming()
    {
    }

    public static float phase(float speed)
    {
        float safeSpeed = Math.max(0F, speed);
        long time = (long) (System.currentTimeMillis() * (double) safeSpeed);

        return (time % PERIOD_MS) / (float) PERIOD_MS;
    }
}
