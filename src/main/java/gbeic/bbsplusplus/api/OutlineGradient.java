package gbeic.bbsplusplus.api;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.List;

/**
 * A colour ramp for one outline shell (or one face of it).
 *
 * <p>{@code null} is the "solid colour" state everywhere in the outline code: a holder that owns no
 * gradient renders exactly like it did before gradients existed, and old saves (which carry no
 * gradient keys at all) stay solid. A gradient is only ever created when the user switches a row to
 * gradient mode.</p>
 *
 * <p>Stops are stored as raw ARGB ints rather than {@code Color} on purpose: a gradient is shared by
 * every bone that was painted with it, so it must not hand out mutable colour objects.</p>
 *
 * <p>{@link #axis} only drives the renderer. {@code VERTICAL} samples the layer along the model's
 * world Y (bottom to top) while {@code HORIZONTAL} samples along world X (left to right); faces use
 * their own pair of axes instead, see {@code OutlineGeometry}.</p>
 */
public final class OutlineGradient
{
    /** How the ramp is projected onto the shell. */
    public enum Axis
    {
        VERTICAL,
        HORIZONTAL;

        public Axis flipped()
        {
            return this == VERTICAL ? HORIZONTAL : VERTICAL;
        }
    }

    /** One colour stop: {@code pos} in 0..1, colours packed as ARGB like the rest of the outline. */
    public static final class Stop
    {
        public float pos;
        public int argb;

        public Stop(float pos, int argb)
        {
            this.pos = clamp01(pos);
            this.argb = argb;
        }

        public Stop copy()
        {
            return new Stop(this.pos, this.argb);
        }
    }

    private Axis axis;
    private final List<Stop> stops = new ArrayList<>();

    public OutlineGradient()
    {
        this(Axis.VERTICAL);
    }

    public OutlineGradient(Axis axis)
    {
        this.axis = axis == null ? Axis.VERTICAL : axis;
    }

    /** Builds a two-stop ramp that fades from {@code fromArgb} to {@code toArgb}. */
    public static OutlineGradient of(Axis axis, int fromArgb, int toArgb)
    {
        OutlineGradient gradient = new OutlineGradient(axis);

        gradient.stops.add(new Stop(0F, fromArgb));
        gradient.stops.add(new Stop(1F, toArgb));

        return gradient;
    }

    /**
     * Builds the ramp a row switches to when it is put into gradient mode. Both ends keep the colour
     * the row had, so flipping the switch never changes how the outline looks on its own.
     */
    public static OutlineGradient fromSolid(Axis axis, int argb)
    {
        return of(axis, argb, argb);
    }

    public Axis getAxis()
    {
        return this.axis;
    }

    public void setAxis(Axis axis)
    {
        this.axis = axis == null ? Axis.VERTICAL : axis;
    }

    public List<Stop> getStops()
    {
        return this.stops;
    }

    public int size()
    {
        return this.stops.size();
    }

    public boolean isEmpty()
    {
        return this.stops.isEmpty();
    }

    /** Keeps the list ordered so {@link #sample(float)} can walk it without checking. */
    public void sort()
    {
        this.stops.sort((a, b) -> Float.compare(a.pos, b.pos));
    }

    /**
     * Adds a stop at {@code pos}. When an existing stop already sits on that position (within a
     * pixel of travel) it is replaced instead, so dragging against an edge cannot stack duplicates.
     */
    public Stop addStop(float pos, int argb)
    {
        float p = clamp01(pos);

        for (Stop stop : this.stops)
        {
            if (Math.abs(stop.pos - p) < 1.0E-4F)
            {
                stop.argb = argb;

                return stop;
            }
        }

        Stop stop = new Stop(p, argb);

        this.stops.add(stop);
        this.sort();

        return stop;
    }

    /** Removes a stop; the last remaining one is kept so a gradient can never become empty. */
    public boolean removeStop(Stop stop)
    {
        if (this.stops.size() <= 1 || stop == null)
        {
            return false;
        }

        return this.stops.remove(stop);
    }

    /**
     * Linear ARGB sample at {@code t} (clamped to 0..1). Interpolation is per channel in straight
     * (non-premultiplied) space, matching how the particle gradient editor in BBS behaves.
     */
    public int sample(float t)
    {
        if (this.stops.isEmpty())
        {
            return 0xFFFFFFFF;
        }

        if (this.stops.size() == 1)
        {
            return this.stops.get(0).argb;
        }

        float p = clamp01(t);
        Stop first = this.stops.get(0);
        Stop last = this.stops.get(this.stops.size() - 1);

        if (p <= first.pos)
        {
            return first.argb;
        }

        if (p >= last.pos)
        {
            return last.argb;
        }

        for (int i = 0; i < this.stops.size() - 1; i++)
        {
            Stop a = this.stops.get(i);
            Stop b = this.stops.get(i + 1);

            if (p >= a.pos && p <= b.pos)
            {
                float span = b.pos - a.pos;

                if (span <= 1.0E-6F)
                {
                    return b.argb;
                }

                return lerpArgb(a.argb, b.argb, (p - a.pos) / span);
            }
        }

        return last.argb;
    }

    /** Same as {@link #sample(float)} but splits the result into float channels for the renderer. */
    public void sample(float t, float[] out)
    {
        int argb = this.sample(t);

        out[0] = ((argb >> 16) & 0xFF) / 255F;
        out[1] = ((argb >> 8) & 0xFF) / 255F;
        out[2] = (argb & 0xFF) / 255F;
        out[3] = ((argb >> 24) & 0xFF) / 255F;
    }

    public OutlineGradient copy()
    {
        OutlineGradient copy = new OutlineGradient(this.axis);

        for (Stop stop : this.stops)
        {
            copy.stops.add(stop.copy());
        }

        return copy;
    }

    /**
     * Stable identity used by cache keys and by the LabPBR batch splitter. Two gradients with the
     * same axis and stops hash the same, which is what lets identical ramps share one 64x1 texture.
     */
    public long hash()
    {
        long h = 1125899906842597L;

        h = h * 31L + this.axis.ordinal();

        for (Stop stop : this.stops)
        {
            h = h * 31L + Float.floatToIntBits(stop.pos);
            h = h * 31L + stop.argb;
        }

        return h;
    }

    /** Hash of a possibly {@code null} gradient, so callers do not need their own null branch. */
    public static long hashOf(OutlineGradient gradient)
    {
        return gradient == null ? 0L : gradient.hash();
    }

    /** Whether two gradients would render identically; {@code null} equals {@code null}. */
    public static boolean equal(OutlineGradient a, OutlineGradient b)
    {
        if (a == b)
        {
            return true;
        }

        if (a == null || b == null || a.axis != b.axis || a.stops.size() != b.stops.size())
        {
            return false;
        }

        for (int i = 0; i < a.stops.size(); i++)
        {
            Stop left = a.stops.get(i);
            Stop right = b.stops.get(i);

            if (left.argb != right.argb || Float.compare(left.pos, right.pos) != 0)
            {
                return false;
            }
        }

        return true;
    }

    /* ------------------------------------------------------------------ serialization */

    public MapType toData()
    {
        MapType data = new MapType();

        data.putByte("axis", (byte) this.axis.ordinal());

        ListType list = new ListType();

        for (Stop stop : this.stops)
        {
            MapType entry = new MapType();

            entry.putFloat("p", stop.pos);
            entry.putInt("c", stop.argb);
            list.add(entry);
        }

        data.put("stops", list);

        return data;
    }

    /**
     * Reads a ramp back. Returns {@code null} when the tag is missing or holds no usable stops, which
     * is exactly the solid-colour state.
     */
    public static OutlineGradient fromData(MapType data)
    {
        if (data == null)
        {
            return null;
        }

        OutlineGradient gradient = new OutlineGradient(readAxis(data));

        BaseType stops = data.get("stops");

        if (stops instanceof ListType list)
        {
            for (int i = 0; i < list.size(); i++)
            {
                BaseType raw = list.get(i);

                if (raw instanceof MapType entry)
                {
                    gradient.stops.add(new Stop(entry.getFloat("p"), entry.getInt("c")));
                }
            }
        }

        if (gradient.stops.isEmpty())
        {
            return null;
        }

        gradient.sort();

        return gradient;
    }

    private static Axis readAxis(MapType data)
    {
        int ordinal = data.getByte("axis", (byte) 0);

        if (ordinal < 0 || ordinal >= Axis.values().length)
        {
            return Axis.VERTICAL;
        }

        return Axis.values()[ordinal];
    }

    /* ------------------------------------------------------------------ helpers */

    private static float clamp01(float value)
    {
        if (value < 0F)
        {
            return 0F;
        }

        if (value > 1F)
        {
            return 1F;
        }

        return value;
    }

    private static int lerpArgb(int a, int b, float t)
    {
        int aa = channel(a, 24);
        int ar = channel(a, 16);
        int ag = channel(a, 8);
        int ab = channel(a, 0);
        int ba = channel(b, 24);
        int br = channel(b, 16);
        int bg = channel(b, 8);
        int bb = channel(b, 0);

        int ca = Math.round(aa + (ba - aa) * t);
        int cr = Math.round(ar + (br - ar) * t);
        int cg = Math.round(ag + (bg - ag) * t);
        int cb = Math.round(ab + (bb - ab) * t);

        return (clampChannel(ca) << 24) | (clampChannel(cr) << 16) | (clampChannel(cg) << 8) | clampChannel(cb);
    }

    private static int channel(int argb, int shift)
    {
        return (argb >> shift) & 0xFF;
    }

    private static int clampChannel(int value)
    {
        if (value < 0)
        {
            return 0;
        }

        if (value > 255)
        {
            return 255;
        }

        return value;
    }
}
