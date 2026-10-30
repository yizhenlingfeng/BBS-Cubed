package gbeic.bbsplusplus.util;

import mchorse.bbs_mod.ui.utils.Scale;

/**
 * 撤销/重做时保持时间轴的水平视口不变。
 *
 * <p>BBS 的撤销条目会把「这次编辑第一次改值那一刻」的时间轴视口（{@code x_min}/{@code x_max}）
 * 一并存下，撤销回放时各编辑器再用 {@link Scale#view(double, double)} 原样套回去，编辑之后的
 * 平移与缩放因此被丢弃。撤销只应回滚数据、不应改变当前视口：进入 {@code applyUndoData} 时用
 * {@link #capture(Scale)} 记下当前值，退出时用 {@link #restore(Scale, double[])} 恢复。</p>
 */
public final class TimelineViewportKeeper
{
    private TimelineViewportKeeper()
    {}

    /** 抓当前水平视口，返回 {@code {min, max}}；视口不可用时返回 {@code null}（调用方保持原版行为）。 */
    public static double[] capture(Scale xAxis)
    {
        if (xAxis == null || xAxis.area == null || xAxis.area.w <= 0)
        {
            return null;
        }

        double min = xAxis.getMinValue();
        double max = xAxis.getMaxValue();

        return Double.isFinite(min) && Double.isFinite(max) && max > min ? new double[] {min, max} : null;
    }

    /** 恢复 {@link #capture(Scale)} 抓到的视口；{@code range} 为 {@code null} 或视口不可用时跳过。 */
    public static void restore(Scale xAxis, double[] range)
    {
        if (range == null || xAxis == null || xAxis.area == null || xAxis.area.w <= 0)
        {
            return;
        }

        xAxis.view(range[0], range[1]);
    }
}
