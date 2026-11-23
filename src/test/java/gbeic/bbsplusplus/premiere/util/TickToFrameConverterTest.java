package gbeic.bbsplusplus.premiere.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 特征测试：固定 Premiere 导出的 tick/frame 换算与 NTSC 时基。
 */
class TickToFrameConverterTest
{
    @Test
    void ticksAreConvertedThroughTwentyTicksPerSecond()
    {
        TickToFrameConverter converter = new TickToFrameConverter(30, false);

        assertEquals(0, converter.ticksToFrames(0));
        assertEquals(30, converter.ticksToFrames(20));
        assertEquals(15, converter.ticksToFrames(10));
        assertEquals(45, converter.ticksToFrames(30));
    }

    @Test
    void secondsAreRoundedToNearestWholeFrame()
    {
        TickToFrameConverter converter = new TickToFrameConverter(24, false);

        assertEquals(24, converter.secondsToFrames(1F));
        assertEquals(12, converter.secondsToFrames(0.5F));
        /* 0.1F 的二进制近似是 0.1000000015，乘 24 得到 2.40000004，四舍五入为 2 */
        assertEquals(2, converter.secondsToFrames(0.1F));
        assertEquals(3, converter.secondsToFrames(0.125F));
    }

    @Test
    void ntscFlagOnlyAppliesToNtscRates()
    {
        assertTrue(new TickToFrameConverter(24, true).isNtsc());
        assertTrue(new TickToFrameConverter(30, true).isNtsc());
        assertTrue(new TickToFrameConverter(60, true).isNtsc());
        assertFalse(new TickToFrameConverter(25, true).isNtsc());
        assertFalse(new TickToFrameConverter(50, true).isNtsc());
    }

    @Test
    void ntscStringMirrorsFlag()
    {
        assertEquals("TRUE", new TickToFrameConverter(30, true).getNtscString());
        assertEquals("FALSE", new TickToFrameConverter(30, false).getNtscString());
        assertEquals("FALSE", new TickToFrameConverter(25, true).getNtscString());
    }

    @Test
    void timebaseFallsBackToPlainFpsWhenNtscIsOff()
    {
        assertEquals(24, new TickToFrameConverter(24, true).getTimebase());
        assertEquals(30, new TickToFrameConverter(30, true).getTimebase());
        assertEquals(60, new TickToFrameConverter(60, true).getTimebase());
        assertEquals(50, new TickToFrameConverter(50, false).getTimebase());
        assertEquals(24, new TickToFrameConverter(24, false).getTimebase());
    }
}
