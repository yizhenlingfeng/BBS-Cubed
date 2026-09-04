package gbeic.bbsplusplus.clips;

/** Pure time mapping used by camera clips and regression checks. */
public final class ReplayTimeMath
{
    private ReplayTimeMath()
    {}

    public static double positiveModulo(double value, double divisor)
    {
        double result = value % divisor;

        return result < 0D ? result + divisor : result;
    }

    public static double mapSourceOffset(double localTime, int sourceDuration,
        double speed, boolean reverse, double endEpsilon)
    {
        double sourceOffset = positiveModulo(localTime * speed, sourceDuration);

        return reverse
            ? Math.max(0D, sourceDuration - sourceOffset - endEpsilon)
            : sourceOffset;
    }

    /** Wraps a remapped tick back into a replay's looping window. */
    public static int applyLooping(int mappedTick, int looping)
    {
        return looping > 0 ? mappedTick % looping : mappedTick;
    }
}
