package gbeic.bbsplusplus.clips;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 特征测试：固定 {@link ReplayTimeMath} 的现有行为。
 * 这些方法被摄像机剪辑与时间重采样共用，属于版本中立逻辑。
 */
class ReplayTimeMathTest
{
    @Test
    void positiveModuloWrapsNegativeValuesIntoPositiveRange()
    {
        assertEquals(3D, ReplayTimeMath.positiveModulo(3D, 10D), 1.0E-9D);
        assertEquals(7D, ReplayTimeMath.positiveModulo(-3D, 10D), 1.0E-9D);
        assertEquals(0D, ReplayTimeMath.positiveModulo(10D, 10D), 1.0E-9D);
        assertEquals(0D, ReplayTimeMath.positiveModulo(0D, 10D), 1.0E-9D);
    }

    @Test
    void mapSourceOffsetScalesBySpeedAndWrapsIntoDuration()
    {
        assertEquals(5D, ReplayTimeMath.mapSourceOffset(5D, 10, 1D, false, 0.5D), 1.0E-9D);
        /* speed 2 把 5 tick 映射到 10，正好回绕到 0 */
        assertEquals(0D, ReplayTimeMath.mapSourceOffset(5D, 10, 2D, false, 0.5D), 1.0E-9D);
        assertEquals(4D, ReplayTimeMath.mapSourceOffset(7D, 10, 2D, false, 0.5D), 1.0E-9D);
    }

    @Test
    void reverseMappingMirrorsOffsetAndClampsAtZero()
    {
        assertEquals(10D - 5D - 0.5D, ReplayTimeMath.mapSourceOffset(5D, 10, 1D, true, 0.5D), 1.0E-9D);
        /* 反向时偏移接近末尾会得到负数，必须被夹到 0 而不是负时间 */
        assertEquals(0D, ReplayTimeMath.mapSourceOffset(9.9D, 10, 1D, true, 0.5D), 1.0E-9D);
    }

    @Test
    void applyLoopingOnlyWrapsWhenLoopingWindowIsPositive()
    {
        assertEquals(3, ReplayTimeMath.applyLooping(13, 10));
        assertEquals(13, ReplayTimeMath.applyLooping(13, 0));
        assertEquals(-13, ReplayTimeMath.applyLooping(-13, 0));
    }
}
