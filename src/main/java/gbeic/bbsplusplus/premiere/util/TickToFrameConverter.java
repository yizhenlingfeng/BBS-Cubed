package gbeic.bbsplusplus.premiere.util;

public class TickToFrameConverter
{
    private final int fps;
    private final boolean ntsc;

    public TickToFrameConverter(int fps, boolean useNtsc)
    {
        this.fps = fps;
        this.ntsc = useNtsc && isNtscRate(fps);
    }

    public int ticksToFrames(int ticks)
    {
        float seconds = ticks / 20F;
        return Math.round(seconds * this.fps);
    }

    public int secondsToFrames(float seconds)
    {
        return Math.round(seconds * this.fps);
    }

    public int getFps()
    {
        return this.fps;
    }

    public int getTimebase()
    {
        if (this.ntsc)
        {
            switch (this.fps)
            {
                case 24: return 24;
                case 30: return 30;
                case 60: return 60;
                default: return this.fps;
            }
        }
        return this.fps;
    }

    public boolean isNtsc()
    {
        return this.ntsc;
    }

    public String getNtscString()
    {
        return this.ntsc ? "TRUE" : "FALSE";
    }

    private static boolean isNtscRate(int fps)
    {
        return fps == 24 || fps == 30 || fps == 60;
    }
}
